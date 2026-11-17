package edu.campus.equiflow.web;

import edu.campus.equiflow.service.*;
import edu.campus.equiflow.web.Models.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class DeskController {
  private final ReservationService reservations;
  private final ActivityService activity;
  private final UserService users;

  @GetMapping("/api/dashboard")
  Dashboard dashboard(Authentication auth) {
    return reservations.dashboard(users.current(auth));
  }

  @GetMapping("/api/admin/activity")
  PageView<AuditView> audit(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "15") int size) {
    return activity.auditPage(page, size);
  }

  @GetMapping("/api/notifications")
  NotificationPage notifications(Authentication auth) {
    return activity.notifications(users.current(auth));
  }

  @PatchMapping("/api/notifications/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void read(@PathVariable Long id, Authentication auth) {
    activity.readNotification(id, users.current(auth));
  }
}
