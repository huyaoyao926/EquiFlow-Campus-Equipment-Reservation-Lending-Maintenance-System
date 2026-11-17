package edu.campus.equiflow.cluster;

import edu.campus.events.EventStore;
import edu.campus.equiflow.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "cluster.enabled", havingValue = "true")
public class ClusterController {
  private final ClusterClient client;
  private final EventStore events;
  private final JdbcTemplate jdbc;
  private final String applicationName;

  public ClusterController(
      ClusterClient client,
      EventStore events,
      JdbcTemplate jdbc,
      @Value("${spring.application.name}") String applicationName) {
    this.client = client;
    this.events = events;
    this.jdbc = jdbc;
    this.applicationName = applicationName;
  }

  public record Completion(
      @NotNull Long version, @NotBlank @Size(min = 10, max = 500) String resolution) {}

  @GetMapping("/api/admin/maintenance")
  Object maintenance() {
    return client.call(
        "EquiFlow-maintenance-service", "/internal/maintenance", HttpMethod.GET, null, List.class);
  }

  @PostMapping("/api/admin/maintenance/{id}/complete")
  Object complete(@PathVariable Long id, @Valid @RequestBody Completion body) {
    return client.call(
        "EquiFlow-maintenance-service",
        "/internal/maintenance/" + id + "/complete",
        HttpMethod.POST,
        body,
        Map.class);
  }

  @GetMapping("/api/admin/services")
  Object services() {
    var services = new ArrayList<Map<String, Object>>();
    for (String name :
        List.of(
            "EquiFlow-identity-service",
            "EquiFlow-maintenance-service",
            "EquiFlow-notification-service")) {
      try {
        client.call(name, "/actuator/health", HttpMethod.GET, null, Map.class);
        services.add(Map.of("name", name, "status", "UP"));
      } catch (ApiException ex) {
        services.add(Map.of("name", name, "status", "UNAVAILABLE"));
      }
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("services", services);
    result.put("lendingServiceName", applicationName);
    result.put("lending", events.status());
    for (String name : List.of("maintenance", "notification")) {
      try {
        result.put(
            name,
            client.call(
                "EquiFlow-" + name + "-service",
                "/internal/events",
                HttpMethod.GET,
                null,
                Map.class));
      } catch (ApiException ex) {
        result.put(name, Map.of("unavailable", true));
      }
    }
    result.put(
        "statistics",
        jdbc.queryForList("SELECT status,COUNT(*) AS total FROM reservations GROUP BY status"));
    return result;
  }

  @PostMapping("/api/admin/services/notifications/{action}")
  Object consumer(@PathVariable String action) {
    return client.call(
        "EquiFlow-notification-service",
        "/internal/consumer/" + action,
        HttpMethod.POST,
        null,
        Map.class);
  }

  @PostMapping("/api/admin/events/{id}/replay")
  Object replay(@PathVariable String id) {
    events.replay(id);
    return Map.of(
        "message",
        "Event queued for replay. Consumer deduplication keeps existing results unchanged.");
  }
}
