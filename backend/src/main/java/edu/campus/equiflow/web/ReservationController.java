package edu.campus.equiflow.web;

import edu.campus.equiflow.domain.ReservationStatus;
import edu.campus.equiflow.service.*;
import edu.campus.equiflow.web.Models.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {
  private final ReservationService reservations;
  private final UserService users;

  @GetMapping
  PageView<ReservationView> list(
      @RequestParam(required = false) ReservationStatus status,
      @RequestParam(defaultValue = "false") boolean overdue,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      Authentication auth) {
    return reservations.list(users.current(auth), status, overdue, search, page, size);
  }

  @GetMapping("/export")
  ResponseEntity<String> export(
      @RequestParam(required = false) ReservationStatus status,
      @RequestParam(defaultValue = "false") boolean overdue,
      @RequestParam(defaultValue = "") String search,
      Authentication auth) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=EquiFlow-requests.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(reservations.exportCsv(users.current(auth), status, overdue, search));
  }

  @GetMapping("/{id}")
  ReservationView detail(@PathVariable Long id, Authentication auth) {
    return reservations.detail(id, users.current(auth));
  }

  @PostMapping
  @PreAuthorize("hasRole('STUDENT')")
  @ResponseStatus(HttpStatus.CREATED)
  ReservationView create(@Valid @RequestBody ReservationInput input, Authentication auth) {
    return reservations.create(input, users.current(auth));
  }

  @PostMapping("/{id}/{action:approve|reject|cancel|checkout|return}")
  ReservationView transition(
      @PathVariable Long id,
      @PathVariable String action,
      @Valid @RequestBody ActionInput input,
      Authentication auth) {
    return reservations.transition(id, action, input, users.current(auth));
  }
}
