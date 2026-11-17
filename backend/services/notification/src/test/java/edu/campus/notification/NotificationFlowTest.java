package edu.campus.notification;

import static org.assertj.core.api.Assertions.*;

import edu.campus.events.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:notification-tests;DB_CLOSE_DELAY=-1",
      "cluster.service-key=notification-test-service-key-123456789",
      "cluster.publish-enabled=false",
      "spring.cloud.config.enabled=false",
      "eureka.client.enabled=false",
      "spring.kafka.listener.auto-startup=false",
      "spring.kafka.admin.auto-create=false",
      "management.tracing.enabled=false"
    })
class NotificationFlowTest {
  @Autowired NotificationConsumer consumer;
  @Autowired MessageRepository messages;
  @Autowired EventStore events;
  @Autowired ObjectMapper json;

  @Test
  void replayCreatesOnlyOneUserVisibleNotification() {
    String id = UUID.randomUUID().toString();
    var event =
        new DeskEvent(
            id,
            "NOTIFICATION_REQUESTED",
            "equipment:1",
            17L,
            23L,
            1L,
            "Camera",
            "Approved",
            "Ready for pickup.",
            id,
            Instant.now());
    String payload = json.writeValueAsString(event);
    consumer.consume(payload);
    consumer.consume(payload);
    assertThat(messages.countByRecipientId(17L)).isEqualTo(1);
    assertThat(events.seen(id, "notifications")).isTrue();
    assertThat(messages.findByIdAndRecipientId(messages.findAll().getFirst().getId(), 999L))
        .isEmpty();
  }

  @Test
  void malformedPayloadDoesNotCommitAnInboxReceipt() {
    long before = messages.count();
    assertThatThrownBy(() -> consumer.consume("not-json")).isInstanceOf(RuntimeException.class);
    assertThat(messages.count()).isEqualTo(before);
  }

  @Test
  @Transactional
  void equipmentDeletionRemovesEachBatchOfMessagesAndBlocksLateNotifications() {
    long recipientId = 18L;
    long deletedEquipmentId = 41L;
    long otherEquipmentId = 42L;
    long firstReservationId = 31L;
    long secondReservationId = 32L;
    long otherReservationId = 33L;
    String firstMessage = saveLegacyMessage(recipientId, firstReservationId);
    String secondMessage = saveLegacyMessage(recipientId, secondReservationId);
    String otherMessage = sendNotification(recipientId, otherEquipmentId, otherReservationId);
    assertThat(storedEvents(recipientId))
        .containsExactlyInAnyOrder(firstMessage, secondMessage, otherMessage);

    sendDeletionBatch(deletedEquipmentId, firstReservationId);
    assertThat(storedEvents(recipientId)).containsExactlyInAnyOrder(secondMessage, otherMessage);
    sendDeletionBatch(deletedEquipmentId, secondReservationId);
    assertThat(storedEvents(recipientId)).containsExactly(otherMessage);

    sendNotification(recipientId, deletedEquipmentId, firstReservationId);
    String newOtherMessage = sendNotification(recipientId, otherEquipmentId, otherReservationId);
    assertThat(storedEvents(recipientId)).containsExactlyInAnyOrder(otherMessage, newOtherMessage);
  }

  private String saveLegacyMessage(long recipientId, long reservationId) {
    var message = new Message();
    message.setRecipientId(recipientId);
    message.setReservationId(reservationId);
    message.setEventId(UUID.randomUUID().toString());
    message.setTitle("Approved");
    message.setMessage("Ready for pickup.");
    message.setCreatedAt(Instant.now());
    return messages.saveAndFlush(message).getEventId();
  }

  private String sendNotification(long recipientId, long equipmentId, long reservationId) {
    String id = UUID.randomUUID().toString();
    var event =
        new DeskEvent(
            id,
            "NOTIFICATION_REQUESTED",
            "equipment:" + equipmentId,
            recipientId,
            reservationId,
            equipmentId,
            "Camera",
            "Approved",
            "Ready for pickup.",
            id,
            Instant.now());
    consumer.consume(json.writeValueAsString(event));
    return id;
  }

  private void sendDeletionBatch(long equipmentId, long reservationId) {
    var event =
        new DeskEvent(
            UUID.randomUUID().toString(),
            "EQUIPMENT_DELETED",
            "equipment:" + equipmentId,
            null,
            null,
            equipmentId,
            null,
            null,
            null,
            null,
            Instant.now(),
            List.of(reservationId));
    consumer.consume(json.writeValueAsString(event));
  }

  private List<String> storedEvents(long recipientId) {
    return messages.findByRecipientIdOrderByCreatedAtDesc(recipientId, Pageable.unpaged()).stream()
        .map(Message::getEventId)
        .toList();
  }
}
