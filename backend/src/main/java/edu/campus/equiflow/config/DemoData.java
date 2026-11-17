package edu.campus.equiflow.config;

import edu.campus.equiflow.DeskProperties;
import edu.campus.equiflow.domain.*;
import edu.campus.equiflow.repository.*;
import edu.campus.equiflow.service.ActivityService;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DemoData implements CommandLineRunner {
  private final DeskProperties properties;
  private final UserRepository users;
  private final EquipmentRepository equipment;
  private final ReservationRepository reservations;
  private final PasswordEncoder passwords;
  private final ActivityService activity;
  private final Clock clock;

  @Override
  @Transactional
  public void run(String... args) {
    if (!properties.seedDemo() || users.count() > 0) {
      return;
    }
    AppUser admin = user("Sam Taylor", "desk@campus.test", "DeskAdmin123!", Role.ADMIN);
    AppUser alex = user("Alex Morgan", "alex@campus.test", "Student123!", Role.STUDENT);
    AppUser jordan = user("Jordan Lee", "jordan@campus.test", "Student123!", Role.STUDENT);
    List<Equipment> items =
        List.of(
            item(
                "CAM-001",
                "Canon EOS R50",
                Category.CAMERA,
                "24.2 MP mirrorless camera with an 18-45mm kit lens. Includes a battery, charger"
                    + " and carry bag.",
                "Media Lab, Level 2",
                EquipmentStatus.READY),
            item(
                "CAM-002",
                "Sony A6400",
                Category.CAMERA,
                "Compact mirrorless camera for photography and video projects. Includes a 16-50mm"
                    + " lens and spare battery.",
                "Media Lab, Level 2",
                EquipmentStatus.READY),
            item(
                "CAM-003",
                "DJI Osmo Pocket 3",
                Category.CAMERA,
                "Pocket camera with a built-in three-axis stabilizer. Supplied with a protective"
                    + " cover and USB-C cable.",
                "Media Lab, Level 2",
                EquipmentStatus.READY),
            item(
                "AUD-001",
                "Rode Wireless GO II",
                Category.AUDIO,
                "Dual-channel wireless microphone kit. Includes two transmitters, one receiver and"
                    + " three charging cables.",
                "Media Lab, Level 2",
                EquipmentStatus.READY),
            item(
                "AUD-002",
                "Zoom H5 Recorder",
                Category.AUDIO,
                "Portable audio recorder with an XY capsule, two XLR inputs and a memory card."
                    + " Check batteries before collection.",
                "Media Lab, Level 2",
                EquipmentStatus.READY),
            item(
                "AUD-003",
                "Shure SM58",
                Category.AUDIO,
                "Dynamic vocal microphone with a stand clip and XLR cable. Suitable for interviews"
                    + " and live presentations.",
                "Performance Room, Level 1",
                EquipmentStatus.READY),
            item(
                "CMP-001",
                "Lenovo ThinkPad T14",
                Category.COMPUTING,
                "14-inch project laptop with 16 GB RAM and a 512 GB SSD. Includes the power adapter"
                    + " and a protective sleeve.",
                "IT Desk, Level 1",
                EquipmentStatus.READY),
            item(
                "CMP-002",
                "Wacom Intuos Pro",
                Category.COMPUTING,
                "Medium pen tablet for illustration and design work. Includes the pen, pen stand"
                    + " and USB cable.",
                "Design Studio, Level 3",
                EquipmentStatus.READY),
            item(
                "LAB-001",
                "Arduino Starter Kit",
                Category.LAB,
                "Arduino Uno with breadboard, jumper wires and basic sensors. Return all components"
                    + " in the original organizer.",
                "Maker Space, Level 1",
                EquipmentStatus.READY),
            item(
                "LAB-002",
                "Digital Oscilloscope",
                Category.LAB,
                "Two-channel bench oscilloscope with probes. Temporarily unavailable while annual"
                    + " calibration is completed.",
                "Electronics Lab, Level 1",
                EquipmentStatus.MAINTENANCE),
            item(
                "ACC-001",
                "Manfrotto Compact Tripod",
                Category.ACCESSORIES,
                "Lightweight camera tripod with a quick-release plate. Maximum supported load is"
                    + " 1.5 kg.",
                "Media Lab, Level 2",
                EquipmentStatus.READY),
            item(
                "ACC-002",
                "Portable LED Light",
                Category.ACCESSORIES,
                "Bi-color LED panel with an adjustable stand and power supply. Includes a soft"
                    + " diffuser and carry case.",
                "Media Lab, Level 2",
                EquipmentStatus.READY));
    items.stream()
        .filter(item -> item.getStatus() == EquipmentStatus.MAINTENANCE)
        .forEach(item -> activity.maintenance(item, "Annual calibration and safety inspection."));
    LocalDate today = LocalDate.now(clock);
    reservation(
        items.get(0),
        alex,
        today,
        today.plusDays(2),
        "Filming a short interview for our media production workshop.",
        ReservationStatus.PENDING,
        admin,
        1);
    reservation(
        items.get(0),
        jordan,
        today,
        today.plusDays(1),
        "Recording a presentation for the campus entrepreneurship society.",
        ReservationStatus.PENDING,
        admin,
        2);
    Reservation approved =
        reservation(
            items.get(3),
            alex,
            today,
            today.plusDays(1),
            "Capturing clear audio for a student documentary interview.",
            ReservationStatus.APPROVED,
            admin,
            3);
    activity.notify(
        approved,
        "Reservation approved",
        "Your Rode Wireless GO II is ready for pickup at the Media Lab.",
        "request:" + approved.getId() + ":APPROVED");
    Reservation overdue =
        reservation(
            items.get(6),
            jordan,
            today.minusDays(3),
            today.minusDays(1),
            "Preparing the demonstration environment for a software engineering project.",
            ReservationStatus.CHECKED_OUT,
            admin,
            4);
    activity.notify(
        overdue,
        "Equipment return overdue",
        "Please return the Lenovo ThinkPad T14 to the IT Desk.",
        "request:" + overdue.getId() + ":OVERDUE");
    reservation(
        items.get(10),
        alex,
        today.minusDays(6),
        today.minusDays(4),
        "Photography practice for the outdoor student exhibition.",
        ReservationStatus.RETURNED,
        admin,
        5);
    reservation(
        items.get(8),
        jordan,
        today.plusDays(2),
        today.plusDays(4),
        "Building a soil moisture sensor prototype in the maker workshop.",
        ReservationStatus.PENDING,
        admin,
        6);
    activity.record(
        admin,
        "DESK_OPENED",
        "EQUIPMENT",
        items.getFirst().getId(),
        "Campus equipment catalogue and sample reservations prepared.");
  }

  private AppUser user(String name, String email, String password, Role role) {
    AppUser user = new AppUser();
    user.setFullName(name);
    user.setEmail(email);
    user.setPasswordHash(activity.clustered() ? "REMOTE_IDENTITY" : passwords.encode(password));
    user.setRole(role);
    user.setCreatedAt(clock.instant().minus(Duration.ofDays(7)));
    return users.save(user);
  }

  private Equipment item(
      String tag,
      String name,
      Category category,
      String description,
      String location,
      EquipmentStatus status) {
    Equipment item = new Equipment();
    item.setAssetTag(tag);
    item.setName(name);
    item.setCategory(category);
    item.setDescription(description);
    item.setLocation(location);
    item.setStatus(status);
    item.setCreatedAt(clock.instant().minus(Duration.ofDays(7)));
    return equipment.save(item);
  }

  private Reservation reservation(
      Equipment item,
      AppUser requester,
      LocalDate first,
      LocalDate last,
      String purpose,
      ReservationStatus status,
      AppUser admin,
      int hoursAgo) {
    Reservation r = new Reservation();
    r.setEquipment(item);
    r.setRequester(requester);
    r.setStartDate(first);
    r.setEndDate(last);
    r.setPurpose(purpose);
    r.setRequestKey(UUID.randomUUID().toString());
    r.setStatus(status);
    r.setCreatedAt(clock.instant().minus(Duration.ofHours(hoursAgo)));
    if (first.isBefore(LocalDate.now(clock))) {
      r.setCreatedAt(first.atTime(8, 0).atZone(clock.getZone()).toInstant());
    }
    if (Set.of(
            ReservationStatus.APPROVED, ReservationStatus.CHECKED_OUT, ReservationStatus.RETURNED)
        .contains(status)) {
      r.setReviewedBy(admin);
      r.setReviewedAt(r.getCreatedAt().plusSeconds(60));
      r.setReviewNote("Approved by the equipment desk.");
    }
    if (status == ReservationStatus.CHECKED_OUT || status == ReservationStatus.RETURNED) {
      r.setCheckedOutAt(first.atTime(10, 0).atZone(clock.getZone()).toInstant());
    }
    if (status == ReservationStatus.RETURNED) {
      r.setReturnedAt(last.atTime(16, 0).atZone(clock.getZone()).toInstant());
    }
    return reservations.save(r);
  }
}
