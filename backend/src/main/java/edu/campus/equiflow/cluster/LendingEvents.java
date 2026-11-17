package edu.campus.equiflow.cluster;

import edu.campus.events.*;
import edu.campus.equiflow.domain.*;
import edu.campus.equiflow.repository.EquipmentRepository;
import edu.campus.equiflow.service.ActivityService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "cluster.enabled", havingValue = "true")
public class LendingEvents {
  private final EquipmentRepository equipment;
  private final EventStore events;
  private final ActivityService activity;
  private final ObjectMapper json;

  public LendingEvents(
      EquipmentRepository equipment,
      EventStore events,
      ActivityService activity,
      ObjectMapper json) {
    this.equipment = equipment;
    this.events = events;
    this.activity = activity;
    this.json = json;
  }

  @KafkaListener(topics = "equiflow.events", groupId = "equiflow-lending-v2")
  @Transactional
  public void consume(String payload) {
    var event = json.readValue(payload, DeskEvent.class);
    if (events.equipmentDeleted(event.equipmentId()) || events.seen(event.id(), "lending")) return;
    if (event.type().equals("MAINTENANCE_COMPLETED")) {
      var item = equipment.lockById(event.equipmentId()).orElse(null);
      if (item == null) return;
      if (item.getStatus() == EquipmentStatus.MAINTENANCE
          && event.correlationId().equals(item.getMaintenanceKey())) {
        item.setStatus(EquipmentStatus.READY);
        item.setMaintenanceKey(null);
        activity.record(
            null,
            "MAINTENANCE_COMPLETED",
            "EQUIPMENT",
            item.getId(),
            item.getName() + ": " + event.message());
      }
    }
    events.received(event.id(), "lending");
  }
}
