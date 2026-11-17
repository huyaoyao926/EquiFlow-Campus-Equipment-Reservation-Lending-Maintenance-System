package edu.campus.maintenance;

import edu.campus.events.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import lombok.*;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class RepairService {
  private final RepairRepository jobs;
  private final EventStore events;
  private final ObjectMapper json;

  RepairService(RepairRepository jobs, EventStore events, ObjectMapper json) {
    this.jobs = jobs;
    this.events = events;
    this.json = json;
  }

  @KafkaListener(topics = "equiflow.events", groupId = "equiflow-maintenance-v2")
  @Transactional
  public void consume(String payload) {
    var event = json.readValue(payload, DeskEvent.class);
    if (events.seen(event.id(), "maintenance")) return;
    if (event.type().equals("EQUIPMENT_DELETED")) {
      events.markEquipmentDeleted(event.equipmentId());
      jobs.deleteByEquipmentId(event.equipmentId());
      events.deleteEquipmentEvents(event.equipmentId());
    } else if (events.equipmentDeleted(event.equipmentId())) {
      return;
    } else if (event.type().equals("MAINTENANCE_REQUESTED")) {
      var job = new RepairJob();
      job.setSourceEventId(event.id());
      job.setEquipmentId(event.equipmentId());
      job.setEquipmentName(event.equipmentName());
      job.setReason(event.message());
      job.setStatus("OPEN");
      job.setCreatedAt(event.occurredAt());
      jobs.saveAndFlush(job);
    }
    events.received(event.id(), "maintenance");
  }

  @Transactional
  public RepairJob complete(Long id, Long version, String resolution) {
    var job =
        jobs.lockById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!Objects.equals(job.getVersion(), version) || !job.getStatus().equals("OPEN"))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Repair changed. Refresh before retrying.");
    job.setStatus("COMPLETED");
    job.setResolution(resolution.strip());
    job.setCompletedAt(Instant.now());
    jobs.saveAndFlush(job);
    events.enqueue(
        new DeskEvent(
            UUID.randomUUID().toString(),
            "MAINTENANCE_COMPLETED",
            "equipment:" + job.getEquipmentId(),
            null,
            null,
            job.getEquipmentId(),
            job.getEquipmentName(),
            "Maintenance completed",
            job.getResolution(),
            job.getSourceEventId(),
            Instant.now()));
    return job;
  }
}
