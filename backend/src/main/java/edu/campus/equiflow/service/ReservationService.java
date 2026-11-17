package edu.campus.equiflow.service;

import edu.campus.equiflow.DeskProperties;
import edu.campus.equiflow.domain.*;
import edu.campus.equiflow.repository.*;
import edu.campus.equiflow.web.ApiException;
import edu.campus.equiflow.web.Models.*;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservationService {
  private final ReservationRepository reservations;
  private final EquipmentRepository equipment;
  private final ActivityService activity;
  private final DeskProperties properties;
  private final Clock clock;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ReservationView create(ReservationInput input, AppUser actor) {
    if (actor.getRole() != Role.STUDENT) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Only student accounts can submit requests.");
    }
    validateDates(input.startDate(), input.endDate());
    if (input.purpose().strip().length() < 10) {
      throw ApiException.invalid("Describe the purpose using at least 10 non-padding characters.");
    }
    Equipment item =
        equipment
            .lockById(input.equipmentId())
            .orElseThrow(() -> ApiException.notFound("Equipment not found."));
    var existing = reservations.findByRequesterIdAndRequestKey(actor.getId(), input.requestKey());
    if (existing.isPresent()) {
      Reservation r = existing.get();
      if (!r.getEquipment().getId().equals(input.equipmentId())
          || !r.getStartDate().equals(input.startDate())
          || !r.getEndDate().equals(input.endDate())
          || !r.getPurpose().equals(input.purpose().strip())) {
        throw ApiException.conflict(
            "This submission key has already been used for a different request.");
      }
      return view(r);
    }
    if (item.getStatus() != EquipmentStatus.READY) {
      throw ApiException.conflict("This equipment is not accepting reservations.");
    }
    assertSlotFree(item.getId(), 0L, input.startDate(), input.endDate());
    Specification<Reservation> duplicate =
        (root, query, cb) ->
            cb.and(
                cb.equal(root.get("requester").get("id"), actor.getId()),
                cb.equal(root.get("equipment").get("id"), item.getId()),
                root.get("status")
                    .in(
                        Set.of(
                            ReservationStatus.PENDING,
                            ReservationStatus.APPROVED,
                            ReservationStatus.CHECKED_OUT)),
                cb.lessThanOrEqualTo(root.get("startDate"), input.endDate()),
                cb.greaterThanOrEqualTo(root.get("endDate"), input.startDate()));
    if (reservations.count(duplicate) > 0) {
      throw ApiException.conflict(
          "You already have an active request for this equipment during these dates.");
    }
    Reservation r = new Reservation();
    r.setEquipment(item);
    r.setRequester(actor);
    r.setStartDate(input.startDate());
    r.setEndDate(input.endDate());
    r.setPurpose(input.purpose().strip());
    r.setStatus(ReservationStatus.PENDING);
    r.setRequestKey(input.requestKey());
    r.setCreatedAt(clock.instant());
    reservations.saveAndFlush(r);
    activity.record(
        actor,
        "REQUEST_SUBMITTED",
        "RESERVATION",
        r.getId(),
        reference(r)
            + ": "
            + item.getName()
            + " requested for "
            + r.getStartDate()
            + " to "
            + r.getEndDate()
            + ".");
    return view(r);
  }

  @Transactional(readOnly = true)
  public PageView<ReservationView> list(
      AppUser actor, ReservationStatus status, boolean overdue, String search, int page, int size) {
    return PageView.from(
        reservations
            .findAll(filter(actor, status, overdue, search), pageRequest(page, size))
            .map(this::view));
  }

  @Transactional(readOnly = true)
  public ReservationView detail(Long id, AppUser actor) {
    Reservation r =
        reservations.findById(id).orElseThrow(() -> ApiException.notFound("Request not found."));
    assertVisible(r, actor);
    return view(r);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ReservationView transition(Long id, String action, ActionInput input, AppUser actor) {
    Reservation r = locked(id);
    assertVisible(r, actor);
    if (!Objects.equals(input.version(), r.getVersion())) {
      throw ApiException.conflict("This request changed. Refresh before trying again.");
    }
    if (!action.equals("cancel") && actor.getRole() != Role.ADMIN) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "Only equipment desk staff can perform this action.");
    }
    String note = input.note() == null ? "" : input.note().strip();
    switch (action) {
      case "approve" -> {
        requireStatus(r, ReservationStatus.PENDING);
        if (r.getEndDate().isBefore(LocalDate.now(clock))) {
          throw ApiException.conflict("These reservation dates have passed.");
        }
        if (r.getEquipment().getStatus() != EquipmentStatus.READY) {
          throw ApiException.conflict("This equipment is not in service.");
        }
        assertSlotFree(r.getEquipment().getId(), r.getId(), r.getStartDate(), r.getEndDate());
        r.setStatus(ReservationStatus.APPROVED);
        review(r, actor, note);
      }
      case "reject" -> {
        requireStatus(r, ReservationStatus.PENDING);
        requireNote(note);
        r.setStatus(ReservationStatus.REJECTED);
        review(r, actor, note);
      }
      case "cancel" -> {
        if (!Set.of(ReservationStatus.PENDING, ReservationStatus.APPROVED)
            .contains(r.getStatus())) {
          throw ApiException.conflict("Only pending or approved requests can be cancelled.");
        }
        if (actor.getRole() == Role.ADMIN) {
          requireNote(note);
        }
        r.setStatus(ReservationStatus.CANCELLED);
        r.setReviewNote(note.isBlank() ? "Cancelled by requester." : note);
      }
      case "checkout" -> {
        requireStatus(r, ReservationStatus.APPROVED);
        LocalDate today = LocalDate.now(clock);
        if (today.isBefore(r.getStartDate()) || today.isAfter(r.getEndDate())) {
          throw ApiException.conflict(
              "Equipment can only be checked out during the approved reservation dates.");
        }
        if (r.getEquipment().getStatus() != EquipmentStatus.READY
            || reservations.existsByEquipmentIdAndStatus(
                r.getEquipment().getId(), ReservationStatus.CHECKED_OUT)) {
          throw ApiException.conflict("This equipment is not ready for pickup.");
        }
        r.setStatus(ReservationStatus.CHECKED_OUT);
        r.setCheckedOutAt(clock.instant());
      }
      case "return" -> {
        requireStatus(r, ReservationStatus.CHECKED_OUT);
        r.setStatus(ReservationStatus.RETURNED);
        r.setReturnedAt(clock.instant());
        if (Boolean.TRUE.equals(input.damaged())) {
          requireNote(note);
          r.getEquipment().setStatus(EquipmentStatus.MAINTENANCE);
          activity.maintenance(r.getEquipment(), note);
        }
      }
      default -> throw ApiException.invalid("Unknown request action.");
    }
    reservations.flush();
    activity.record(
        actor,
        "REQUEST_" + r.getStatus().name(),
        "RESERVATION",
        r.getId(),
        reference(r)
            + ": "
            + r.getEquipment().getName()
            + " -> "
            + r.getStatus()
            + (note.isBlank() ? "." : ". " + note));
    activity.notify(
        r,
        title(r.getStatus()),
        r.getEquipment().getName() + ": " + message(r.getStatus()),
        "request:" + r.getId() + ":" + r.getStatus());
    return view(r);
  }

  @Transactional(readOnly = true)
  public Dashboard dashboard(AppUser actor) {
    Specification<Reservation> scope = filter(actor, null, false, null);
    long pending = reservations.count(scope.and(hasStatus(ReservationStatus.PENDING)));
    long approved = reservations.count(scope.and(hasStatus(ReservationStatus.APPROVED)));
    long checkedOut = reservations.count(scope.and(hasStatus(ReservationStatus.CHECKED_OUT)));
    long overdue = reservations.count(filter(actor, null, true, null));
    var recent = reservations.findAll(scope, pageRequest(0, 5)).map(this::view).getContent();
    return new Dashboard(
        equipment.count(),
        equipment.countByStatus(EquipmentStatus.READY),
        pending,
        approved,
        checkedOut,
        overdue,
        reservations.count(scope),
        recent);
  }

  @Transactional(readOnly = true)
  public String exportCsv(AppUser actor, ReservationStatus status, boolean overdue, String search) {
    var records =
        reservations.findAll(
            filter(actor, status, overdue, search),
            PageRequest.of(0, 10000, Sort.by("createdAt").descending()));
    StringBuilder csv =
        new StringBuilder(
            "Reference,Equipment,Asset tag,Requester,Email,Start date,End"
                + " date,Status,Purpose,Review note\r\n");
    for (Reservation r : records) {
      csv.append(
              Arrays.asList(
                      reference(r),
                      r.getEquipment().getName(),
                      r.getEquipment().getAssetTag(),
                      r.getRequester().getFullName(),
                      r.getRequester().getEmail(),
                      r.getStartDate().toString(),
                      r.getEndDate().toString(),
                      r.getStatus().name(),
                      r.getPurpose(),
                      r.getReviewNote())
                  .stream()
                  .map(this::csvCell)
                  .collect(Collectors.joining(",")))
          .append("\r\n");
    }
    return csv.toString();
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void processPastReservation(Long id) {
    Reservation r = locked(id);
    if (!r.getEndDate().isBefore(LocalDate.now(clock))) {
      return;
    }
    if (r.getStatus() == ReservationStatus.PENDING || r.getStatus() == ReservationStatus.APPROVED) {
      r.setStatus(ReservationStatus.EXPIRED);
      activity.record(
          null,
          "REQUEST_EXPIRED",
          "RESERVATION",
          r.getId(),
          reference(r) + ": reservation dates passed without checkout.");
      activity.notify(
          r,
          "Reservation expired",
          r.getEquipment().getName() + ": the reservation period has ended.",
          "request:" + id + ":EXPIRED");
    } else if (r.getStatus() == ReservationStatus.CHECKED_OUT) {
      activity.notify(
          r,
          "Equipment return overdue",
          "Please return "
              + r.getEquipment().getName()
              + " to "
              + r.getEquipment().getLocation()
              + ".",
          "request:" + id + ":OVERDUE");
    }
  }

  public ReservationView view(Reservation r) {
    return new ReservationView(
        r.getId(),
        reference(r),
        r.getEquipment().getId(),
        r.getEquipment().getName(),
        r.getEquipment().getAssetTag(),
        r.getEquipment().getCategory(),
        r.getEquipment().getLocation(),
        r.getRequester().getId(),
        r.getRequester().getFullName(),
        r.getRequester().getEmail(),
        r.getStartDate(),
        r.getEndDate(),
        r.getPurpose(),
        r.getStatus(),
        r.getReviewNote(),
        r.getReviewedBy() == null ? null : r.getReviewedBy().getFullName(),
        r.getReviewedAt(),
        r.getCheckedOutAt(),
        r.getReturnedAt(),
        r.getCreatedAt(),
        r.getVersion(),
        r.getStatus() == ReservationStatus.CHECKED_OUT
            && r.getEndDate().isBefore(LocalDate.now(clock)));
  }

  private Reservation locked(Long id) {
    Long equipmentId =
        reservations
            .findEquipmentId(id)
            .orElseThrow(() -> ApiException.notFound("Request not found."));
    equipment
        .lockById(equipmentId)
        .orElseThrow(() -> ApiException.notFound("Equipment not found."));
    return reservations.findById(id).orElseThrow(() -> ApiException.notFound("Request not found."));
  }

  private void validateDates(LocalDate first, LocalDate last) {
    LocalDate today = LocalDate.now(clock);
    if (first.isBefore(today) || last.isBefore(first)) {
      throw ApiException.invalid(
          "Choose a start date on or after today and an end date on or after the start date.");
    }
    if (ChronoUnit.DAYS.between(first, last) + 1 > properties.booking().maxDays()) {
      throw ApiException.invalid(
          "Reservations can be at most " + properties.booking().maxDays() + " calendar days long.");
    }
    if (last.isAfter(today.plusDays(properties.booking().advanceDays()))) {
      throw ApiException.invalid(
          "Reservations must end within the next " + properties.booking().advanceDays() + " days.");
    }
  }

  private void assertSlotFree(Long equipmentId, Long excludedId, LocalDate first, LocalDate last) {
    if (reservations.countOverlaps(equipmentId, excludedId, EquipmentService.BLOCKING, first, last)
        > 0) {
      throw ApiException.conflict(
          "This equipment already has an approved reservation for these dates.");
    }
    if (reservations.existsByEquipmentIdAndStatusAndEndDateBefore(
        equipmentId, ReservationStatus.CHECKED_OUT, LocalDate.now(clock))) {
      throw ApiException.conflict(
          "This equipment has an overdue loan. It must be returned before another reservation is"
              + " approved.");
    }
  }

  private Specification<Reservation> filter(
      AppUser actor, ReservationStatus status, boolean overdue, String search) {
    return (root, query, cb) -> {
      List<Predicate> filters = new ArrayList<>();
      if (actor.getRole() != Role.ADMIN) {
        filters.add(cb.equal(root.get("requester").get("id"), actor.getId()));
      }
      if (status != null) {
        filters.add(cb.equal(root.get("status"), status));
      }
      if (overdue) {
        filters.add(cb.equal(root.get("status"), ReservationStatus.CHECKED_OUT));
        filters.add(cb.lessThan(root.get("endDate"), LocalDate.now(clock)));
      }
      if (search != null && !search.isBlank()) {
        String value = "%" + EquipmentService.escape(search.toLowerCase(Locale.ROOT).strip()) + "%";
        filters.add(
            cb.or(
                cb.like(cb.lower(root.get("equipment").get("name")), value, '\\'),
                cb.like(cb.lower(root.get("equipment").get("assetTag")), value, '\\'),
                cb.like(cb.lower(root.get("requester").get("fullName")), value, '\\')));
      }
      return cb.and(filters.toArray(Predicate[]::new));
    };
  }

  private Specification<Reservation> hasStatus(ReservationStatus status) {
    return (root, query, cb) -> cb.equal(root.get("status"), status);
  }

  private PageRequest pageRequest(int page, int size) {
    return PageRequest.of(
        Math.max(page, 0),
        Math.clamp(size, 1, 100),
        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
  }

  private void assertVisible(Reservation r, AppUser actor) {
    if (actor.getRole() != Role.ADMIN && !r.getRequester().getId().equals(actor.getId())) {
      throw ApiException.notFound("Request not found.");
    }
  }

  private void requireStatus(Reservation r, ReservationStatus expected) {
    if (r.getStatus() != expected) {
      throw ApiException.conflict(
          "This action requires a "
              + expected.name().toLowerCase(Locale.ROOT).replace('_', ' ')
              + " request.");
    }
  }

  private void requireNote(String note) {
    if (note.isBlank()) {
      throw ApiException.invalid("Add a reason so the requester understands this decision.");
    }
  }

  private void review(Reservation r, AppUser actor, String note) {
    r.setReviewedBy(actor);
    r.setReviewedAt(clock.instant());
    r.setReviewNote(note);
  }

  private String reference(Reservation r) {
    return "REQ-" + String.format(Locale.ROOT, "%05d", r.getId());
  }

  private String csvCell(String value) {
    String text = value == null ? "" : value;
    if (!text.isEmpty() && "=+-@\t\r\n".indexOf(text.charAt(0)) >= 0) {
      text = "'" + text;
    }
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }

  private String title(ReservationStatus status) {
    return switch (status) {
      case APPROVED -> "Reservation approved";
      case REJECTED -> "Request declined";
      case CANCELLED -> "Reservation cancelled";
      case CHECKED_OUT -> "Equipment checked out";
      case RETURNED -> "Return recorded";
      default -> "Request updated";
    };
  }

  private String message(ReservationStatus status) {
    return switch (status) {
      case APPROVED -> "your request is approved. Bring your student card when collecting it.";
      case REJECTED -> "your request was declined. Open the request for the desk's note.";
      case CANCELLED -> "your reservation has been cancelled.";
      case CHECKED_OUT -> "pickup is recorded. Please return it by the end of your reservation.";
      case RETURNED -> "your equipment return is complete. Thank you.";
      default -> "your request has been updated.";
    };
  }
}
