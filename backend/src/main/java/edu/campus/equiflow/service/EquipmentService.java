package edu.campus.equiflow.service;

import edu.campus.events.DeskEvent;
import edu.campus.events.EventStore;
import edu.campus.equiflow.domain.*;
import edu.campus.equiflow.repository.*;
import edu.campus.equiflow.web.ApiException;
import edu.campus.equiflow.web.Models.*;
import jakarta.persistence.criteria.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EquipmentService {
  public static final Set<ReservationStatus> BLOCKING =
      Set.of(ReservationStatus.APPROVED, ReservationStatus.CHECKED_OUT);
  private static final int DELETION_BATCH_SIZE = 250;
  private final EquipmentRepository equipment;
  private final ReservationRepository reservations;
  private final ActivityService activity;
  private final Clock clock;
  private final JdbcTemplate jdbc;
  private final ObjectProvider<EventStore> eventStores;

  @Transactional(readOnly = true)
  public PageView<EquipmentView> list(
      String search,
      Category category,
      boolean availableOnly,
      LocalDate start,
      LocalDate end,
      int page,
      int size,
      AppUser user) {
    LocalDate first = start == null ? LocalDate.now(clock) : start;
    LocalDate last = end == null ? first : end;
    if (last.isBefore(first)) {
      throw ApiException.invalid("The end date must be on or after the start date.");
    }
    Specification<Equipment> spec =
        (root, query, cb) -> {
          List<Predicate> filters = new ArrayList<>();
          if (user.getRole() != Role.ADMIN) {
            filters.add(cb.notEqual(root.get("status"), EquipmentStatus.RETIRED));
          }
          if (search != null && !search.isBlank()) {
            String value = "%" + escape(search.toLowerCase(Locale.ROOT).strip()) + "%";
            filters.add(
                cb.or(
                    cb.like(cb.lower(root.get("name")), value, '\\'),
                    cb.like(cb.lower(root.get("assetTag")), value, '\\')));
          }
          if (category != null) {
            filters.add(cb.equal(root.get("category"), category));
          }
          if (availableOnly) {
            filters.add(cb.equal(root.get("status"), EquipmentStatus.READY));
            Subquery<Long> blocked = query.subquery(Long.class);
            Root<Reservation> r = blocked.from(Reservation.class);
            Predicate overlap =
                cb.and(
                    cb.lessThanOrEqualTo(r.get("startDate"), last),
                    cb.greaterThanOrEqualTo(r.get("endDate"), first));
            Predicate overdue =
                cb.and(
                    cb.equal(r.get("status"), ReservationStatus.CHECKED_OUT),
                    cb.lessThan(r.get("endDate"), LocalDate.now(clock)));
            blocked
                .select(r.get("id"))
                .where(
                    cb.equal(r.get("equipment"), root),
                    r.get("status").in(BLOCKING),
                    cb.or(overlap, overdue));
            filters.add(cb.not(cb.exists(blocked)));
          }
          return cb.and(filters.toArray(Predicate[]::new));
        };
    var result =
        equipment.findAll(
            spec, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("name")));
    var ids = result.getContent().stream().map(Equipment::getId).toList();
    Map<Long, List<Reservation>> committed =
        ids.isEmpty()
            ? Map.of()
            : reservations.findByEquipmentIdInAndStatusIn(ids, BLOCKING).stream()
                .collect(Collectors.groupingBy(r -> r.getEquipment().getId()));
    return PageView.from(
        result.map(
            item -> view(item, first, last, committed.getOrDefault(item.getId(), List.of()))));
  }

  @Transactional(readOnly = true)
  public EquipmentDetail detail(Long id, AppUser user) {
    Equipment item =
        equipment.findById(id).orElseThrow(() -> ApiException.notFound("Equipment not found."));
    if (item.getStatus() == EquipmentStatus.RETIRED && user.getRole() != Role.ADMIN) {
      throw ApiException.notFound("Equipment not found.");
    }
    LocalDate today = LocalDate.now(clock);
    var slots =
        reservations.findByEquipmentIdAndStatusInOrderByStartDateAsc(id, BLOCKING).stream()
            .filter(
                r ->
                    !r.getEndDate().isBefore(today)
                        || r.getStatus() == ReservationStatus.CHECKED_OUT)
            .map(r -> new SlotView(r.getStartDate(), r.getEndDate(), r.getStatus()))
            .toList();
    return new EquipmentDetail(view(item, today, today), slots);
  }

  @Transactional
  public EquipmentView create(EquipmentInput input, AppUser actor) {
    if (equipment.existsByAssetTag(input.assetTag())) {
      throw ApiException.conflict("This asset tag is already in use.");
    }
    Equipment item = new Equipment();
    item.setCreatedAt(clock.instant());
    apply(item, input);
    equipment.saveAndFlush(item);
    if (item.getStatus() == EquipmentStatus.MAINTENANCE)
      activity.maintenance(item, "Equipment added in maintenance status.");
    activity.record(
        actor,
        "EQUIPMENT_ADDED",
        "EQUIPMENT",
        item.getId(),
        item.getName() + " (" + item.getAssetTag() + ") added to the desk.");
    return view(item, LocalDate.now(clock), LocalDate.now(clock));
  }

  @Transactional
  public EquipmentView update(Long id, EquipmentInput input, AppUser actor) {
    Equipment item =
        equipment.lockById(id).orElseThrow(() -> ApiException.notFound("Equipment not found."));
    if (!Objects.equals(input.version(), item.getVersion())) {
      throw ApiException.conflict("This equipment record changed. Refresh before saving.");
    }
    if (!item.getAssetTag().equals(input.assetTag())
        && equipment.existsByAssetTag(input.assetTag())) {
      throw ApiException.conflict("This asset tag is already in use.");
    }
    if (input.status() != EquipmentStatus.READY) {
      boolean committed =
          reservations.findByEquipmentIdAndStatusInOrderByStartDateAsc(id, BLOCKING).stream()
              .anyMatch(
                  r ->
                      r.getStatus() == ReservationStatus.CHECKED_OUT
                          || !r.getEndDate().isBefore(LocalDate.now(clock)));
      if (committed) {
        throw ApiException.conflict(
            "Resolve active loans and approved reservations before changing availability.");
      }
    }
    if (item.getMaintenanceKey() != null && input.status() != EquipmentStatus.MAINTENANCE) {
      throw ApiException.conflict(
          "Complete the open repair in Maintenance before returning this asset to service.");
    }
    apply(item, input);
    if (item.getStatus() == EquipmentStatus.MAINTENANCE)
      activity.maintenance(item, "Maintenance requested by the equipment desk.");
    equipment.flush();
    activity.record(
        actor,
        "EQUIPMENT_UPDATED",
        "EQUIPMENT",
        id,
        item.getName() + " updated. Status: " + item.getStatus() + ".");
    return view(item, LocalDate.now(clock), LocalDate.now(clock));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void delete(Long id) {
    Equipment item =
        equipment.lockById(id).orElseThrow(() -> ApiException.notFound("Equipment not found."));
    var store = eventStores.getIfAvailable();
    var reservationIds =
        store == null
            ? List.<Long>of()
            : jdbc.queryForList("SELECT id FROM reservations WHERE equipment_id=?", Long.class, id);
    jdbc.update(
        "DELETE FROM notifications WHERE reservation_id IN"
            + " (SELECT id FROM reservations WHERE equipment_id=?)",
        id);
    jdbc.update(
        "DELETE FROM audit_events WHERE (target_type='EQUIPMENT' AND target_id=?)"
            + " OR (target_type='RESERVATION' AND target_id IN"
            + " (SELECT id FROM reservations WHERE equipment_id=?))",
        id,
        id);
    jdbc.update("DELETE FROM reservations WHERE equipment_id=?", id);
    equipment.delete(item);
    equipment.flush();
    if (store != null) {
      store.deleteEquipmentEvents(id);
      store.markEquipmentDeleted(id);
      enqueueDeletion(store, id, reservationIds);
    }
  }

  private void enqueueDeletion(EventStore store, Long id, List<Long> reservationIds) {
    int start = 0;
    do {
      int end = Math.min(start + DELETION_BATCH_SIZE, reservationIds.size());
      store.enqueue(
          new DeskEvent(
              UUID.randomUUID().toString(),
              "EQUIPMENT_DELETED",
              "equipment:" + id,
              null,
              null,
              id,
              null,
              null,
              null,
              null,
              clock.instant(),
              reservationIds.subList(start, end)));
      start = end;
    } while (start < reservationIds.size());
  }

  public EquipmentView view(Equipment item, LocalDate first, LocalDate last) {
    return view(
        item,
        first,
        last,
        reservations.findByEquipmentIdAndStatusInOrderByStartDateAsc(item.getId(), BLOCKING));
  }

  private EquipmentView view(
      Equipment item, LocalDate first, LocalDate last, List<Reservation> committed) {
    String availability = item.getStatus().name();
    if (item.getStatus() == EquipmentStatus.READY) {
      availability = "AVAILABLE";
      for (Reservation r : committed) {
        boolean overlaps = !r.getStartDate().isAfter(last) && !r.getEndDate().isBefore(first);
        boolean overdue =
            r.getStatus() == ReservationStatus.CHECKED_OUT
                && r.getEndDate().isBefore(LocalDate.now(clock));
        if (overlaps || overdue) {
          availability = r.getStatus() == ReservationStatus.CHECKED_OUT ? "ON_LOAN" : "RESERVED";
          if (availability.equals("ON_LOAN")) {
            break;
          }
        }
      }
    }
    return new EquipmentView(
        item.getId(),
        item.getAssetTag(),
        item.getName(),
        item.getCategory(),
        item.getDescription(),
        item.getLocation(),
        item.getStatus(),
        availability,
        item.getVersion());
  }

  private void apply(Equipment item, EquipmentInput input) {
    item.setAssetTag(input.assetTag());
    item.setName(input.name().strip());
    item.setCategory(input.category());
    item.setDescription(input.description().strip());
    item.setLocation(input.location().strip());
    item.setStatus(input.status());
  }

  public static String escape(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
