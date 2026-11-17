package edu.campus.equiflow.web;

import edu.campus.equiflow.domain.Category;
import edu.campus.equiflow.service.*;
import edu.campus.equiflow.web.Models.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
public class EquipmentController {
  private final EquipmentService equipment;
  private final UserService users;

  @GetMapping
  PageView<EquipmentView> list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(required = false) Category category,
      @RequestParam(defaultValue = "false") boolean availableOnly,
      @RequestParam(required = false) LocalDate start,
      @RequestParam(required = false) LocalDate end,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "9") int size,
      Authentication auth) {
    return equipment.list(
        search, category, availableOnly, start, end, page, size, users.current(auth));
  }

  @GetMapping("/{id}")
  EquipmentDetail detail(@PathVariable Long id, Authentication auth) {
    return equipment.detail(id, users.current(auth));
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  EquipmentView create(@Valid @RequestBody EquipmentInput input, Authentication auth) {
    return equipment.create(input, users.current(auth));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  EquipmentView update(
      @PathVariable Long id, @Valid @RequestBody EquipmentInput input, Authentication auth) {
    return equipment.update(id, input, users.current(auth));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable Long id) {
    equipment.delete(id);
  }
}
