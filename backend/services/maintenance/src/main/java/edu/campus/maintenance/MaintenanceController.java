package edu.campus.maintenance;

import edu.campus.events.*;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class MaintenanceController {
  private final RepairRepository jobs;
  private final RepairService repairs;
  private final EventStore events;

  MaintenanceController(RepairRepository jobs, RepairService repairs, EventStore events) {
    this.jobs = jobs;
    this.repairs = repairs;
    this.events = events;
  }

  public record Completion(
      @NotNull Long version, @NotBlank @Size(min = 10, max = 500) String resolution) {}

  @GetMapping("/internal/maintenance")
  List<RepairJob> list() {
    return jobs.findAllByOrderByCreatedAtDesc();
  }

  @PostMapping("/internal/maintenance/{id}/complete")
  RepairJob complete(@PathVariable Long id, @Valid @RequestBody Completion input) {
    return repairs.complete(id, input.version(), input.resolution());
  }

  @GetMapping("/internal/events")
  Map<String, Object> status() {
    return events.status();
  }
}
