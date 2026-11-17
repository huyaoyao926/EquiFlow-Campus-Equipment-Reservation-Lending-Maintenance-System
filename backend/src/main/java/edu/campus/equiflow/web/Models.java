package edu.campus.equiflow.web;

import edu.campus.equiflow.domain.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;
import org.springframework.data.domain.Page;

public final class Models {
  private Models() {}

  public record Registration(
      @NotBlank @Size(min = 2, max = 80) String fullName,
      @NotBlank @Email @Size(max = 120) String email,
      @NotBlank
          @Size(min = 10, max = 72)
          @Pattern(
              regexp = "(?=.*[A-Za-z])(?=.*[0-9])[\\x20-\\x7E]+",
              message = "Use at least one letter and one number, with ASCII characters only")
          String password) {}

  public record UserView(Long id, String fullName, String email, Role role) {}

  public record EquipmentInput(
      @NotBlank
          @Pattern(
              regexp = "[A-Z0-9][A-Z0-9-]{2,31}",
              message = "Use 3 to 32 uppercase letters, numbers or hyphens")
          String assetTag,
      @NotBlank @Size(max = 100) String name,
      @NotNull Category category,
      @NotBlank @Size(max = 1000) String description,
      @NotBlank @Size(max = 100) String location,
      @NotNull EquipmentStatus status,
      Long version) {}

  public record EquipmentView(
      Long id,
      String assetTag,
      String name,
      Category category,
      String description,
      String location,
      EquipmentStatus status,
      String availability,
      Long version) {}

  public record SlotView(LocalDate startDate, LocalDate endDate, ReservationStatus status) {}

  public record EquipmentDetail(EquipmentView equipment, List<SlotView> reservedDates) {}

  public record ReservationInput(
      @NotNull @Positive Long equipmentId,
      @NotNull LocalDate startDate,
      @NotNull LocalDate endDate,
      @NotBlank @Size(min = 10, max = 500) String purpose,
      @NotBlank
          @Pattern(
              regexp =
                  "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
          String requestKey) {}

  public record ActionInput(
      @NotNull @PositiveOrZero Long version, @Size(max = 500) String note, Boolean damaged) {
    public ActionInput(Long version, String note) {
      this(version, note, false);
    }
  }

  public record ReservationView(
      Long id,
      String reference,
      Long equipmentId,
      String equipmentName,
      String assetTag,
      Category category,
      String location,
      Long requesterId,
      String requesterName,
      String requesterEmail,
      LocalDate startDate,
      LocalDate endDate,
      String purpose,
      ReservationStatus status,
      String reviewNote,
      String reviewedBy,
      Instant reviewedAt,
      Instant checkedOutAt,
      Instant returnedAt,
      Instant createdAt,
      Long version,
      boolean overdue) {}

  public record AuditView(
      Long id,
      String actor,
      String action,
      String targetType,
      Long targetId,
      String details,
      Instant createdAt) {}

  public record NotificationView(
      Long id,
      Long reservationId,
      String title,
      String message,
      Instant createdAt,
      Instant readAt) {}

  public record NotificationPage(List<NotificationView> items, long unread, long total) {}

  public record Dashboard(
      long equipmentCount,
      long readyCount,
      long pending,
      long approved,
      long checkedOut,
      long overdue,
      long totalRequests,
      List<ReservationView> recentRequests) {}

  public record PageView<T>(List<T> items, long total, int page, int size, int pages) {
    public static <T> PageView<T> from(Page<T> page) {
      return new PageView<>(
          page.getContent(),
          page.getTotalElements(),
          page.getNumber(),
          page.getSize(),
          page.getTotalPages());
    }
  }

  public record CampusConfig(
      LocalDate today, String timezone, int maxDays, int advanceDays, boolean demoEnabled) {}
}
