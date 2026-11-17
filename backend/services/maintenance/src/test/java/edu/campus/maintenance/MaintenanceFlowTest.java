package edu.campus.maintenance;

import static org.assertj.core.api.Assertions.*;

import edu.campus.events.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:maintenance-tests;DB_CLOSE_DELAY=-1",
      "cluster.service-key=maintenance-test-service-key-123456789",
      "cluster.publish-enabled=false",
      "spring.cloud.config.enabled=false",
      "eureka.client.enabled=false",
      "spring.kafka.listener.auto-startup=false",
      "spring.kafka.admin.auto-create=false",
      "management.tracing.enabled=false"
    })
class MaintenanceFlowTest {
  @Autowired RepairService service;
  @Autowired RepairRepository jobs;
  @Autowired EventStore events;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  @Test
  void duplicateDamageEventsCreateOneRepairAndCompletionPublishesOnce() {
    String id = UUID.randomUUID().toString();
    var event =
        new DeskEvent(
            id,
            "MAINTENANCE_REQUESTED",
            "equipment:9",
            null,
            null,
            9L,
            "Camera",
            "Inspection",
            "Battery compartment damaged.",
            id,
            Instant.now());
    String payload = json.writeValueAsString(event);
    service.consume(payload);
    service.consume(payload);
    assertThat(jobs.count()).isEqualTo(1);
    var job = jobs.findAll().getFirst();
    var completed =
        service.complete(
            job.getId(), job.getVersion(), "Replaced the battery cover and passed safety checks.");
    assertThat(completed.getStatus()).isEqualTo("COMPLETED");
    assertThat(events.status().get("pending")).isEqualTo(1L);
    assertThatThrownBy(() -> service.complete(job.getId(), 0L, "Repeated repair completion."))
        .isInstanceOf(RuntimeException.class);
    assertThat(events.status().get("pending")).isEqualTo(1L);
  }

  @Test
  @Transactional
  void equipmentDeletionRemovesRepairsAndCompletionEventsAndBlocksLateRequests() {
    Long deletedEquipmentId = 51L;
    Long otherEquipmentId = 52L;
    completeRepair(openRepair(deletedEquipmentId));
    events.published(outboxEvents(deletedEquipmentId).getFirst());
    completeRepair(openRepair(deletedEquipmentId));
    openRepair(deletedEquipmentId);
    var otherJob = openRepair(otherEquipmentId);
    completeRepair(otherJob);
    String otherEvent = outboxEvents(otherEquipmentId).getFirst();
    assertThat(outboxEvents(deletedEquipmentId)).hasSize(2);
    assertThat(jobs.findAll())
        .filteredOn(job -> deletedEquipmentId.equals(job.getEquipmentId()))
        .extracting(RepairJob::getStatus)
        .containsExactlyInAnyOrder("OPEN", "COMPLETED", "COMPLETED");

    var deleted =
        new DeskEvent(
            UUID.randomUUID().toString(),
            "EQUIPMENT_DELETED",
            "equipment:" + deletedEquipmentId,
            null,
            null,
            deletedEquipmentId,
            null,
            null,
            null,
            null,
            Instant.now(),
            List.of());
    service.consume(json.writeValueAsString(deleted));
    assertThat(outboxEvents(deletedEquipmentId)).isEmpty();
    assertThat(outboxEvents(otherEquipmentId)).containsExactly(otherEvent);

    service.consume(json.writeValueAsString(repairRequest(deletedEquipmentId)));
    var lateOtherJob = openRepair(otherEquipmentId);
    assertThat(jobs.findAll())
        .filteredOn(job -> deletedEquipmentId.equals(job.getEquipmentId()))
        .isEmpty();
    assertThat(jobs.findAll())
        .filteredOn(job -> otherEquipmentId.equals(job.getEquipmentId()))
        .extracting(RepairJob::getId)
        .containsExactlyInAnyOrder(otherJob.getId(), lateOtherJob.getId());
  }

  private RepairJob openRepair(long equipmentId) {
    var request = repairRequest(equipmentId);
    service.consume(json.writeValueAsString(request));
    return jobs.findAll().stream()
        .filter(job -> request.id().equals(job.getSourceEventId()))
        .findFirst()
        .orElseThrow();
  }

  private DeskEvent repairRequest(long equipmentId) {
    String id = UUID.randomUUID().toString();
    return new DeskEvent(
        id,
        "MAINTENANCE_REQUESTED",
        "equipment:" + equipmentId,
        null,
        null,
        equipmentId,
        "Camera",
        "Inspection",
        "Battery compartment damaged.",
        id,
        Instant.now());
  }

  private void completeRepair(RepairJob job) {
    service.complete(job.getId(), job.getVersion(), "Replaced the battery cover.");
  }

  private List<String> outboxEvents(long equipmentId) {
    return jdbc.queryForList(
        "SELECT event_id FROM event_outbox WHERE aggregate_key = ?",
        String.class,
        "equipment:" + equipmentId);
  }
}
