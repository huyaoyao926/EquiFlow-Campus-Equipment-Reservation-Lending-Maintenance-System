package edu.campus.notification;

import edu.campus.events.*;
import jakarta.persistence.*;
import java.util.*;
import lombok.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
public class NotificationConsumer {
  private final MessageRepository messages;
  private final EventStore events;
  private final ObjectMapper json;

  NotificationConsumer(MessageRepository messages, EventStore events, ObjectMapper json) {
    this.messages = messages;
    this.events = events;
    this.json = json;
  }

  @KafkaListener(
      id = "notification-listener",
      topics = "equiflow.events",
      groupId = "equiflow-notifications-v2")
  @Transactional
  public void consume(String payload) {
    DeskEvent event = json.readValue(payload, DeskEvent.class);
    if (events.seen(event.id(), "notifications")) return;
    if (event.type().equals("EQUIPMENT_DELETED")) {
      events.markEquipmentDeleted(event.equipmentId());
      if (event.reservationIds() != null && !event.reservationIds().isEmpty()) {
        messages.deleteByReservationIds(event.reservationIds());
      }
      events.deleteEquipmentEvents(event.equipmentId());
    } else if (events.equipmentDeleted(event.equipmentId())) {
      return;
    } else if (event.type().equals("NOTIFICATION_REQUESTED") && event.userId() != null) {
      var message = new Message();
      message.setRecipientId(event.userId());
      message.setReservationId(event.reservationId());
      message.setEventId(event.id());
      message.setTitle(event.title());
      message.setMessage(event.message());
      message.setCreatedAt(event.occurredAt());
      messages.saveAndFlush(message);
    }
    events.received(event.id(), "notifications");
  }
}
