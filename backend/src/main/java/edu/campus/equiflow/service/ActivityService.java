package edu.campus.equiflow.service;

import edu.campus.equiflow.domain.*;
import edu.campus.equiflow.repository.*;
import edu.campus.equiflow.web.ApiException;
import edu.campus.equiflow.web.Models.*;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivityService {
  private final AuditRepository audits;
  private final NotificationRepository notifications;
  private final Clock clock;
  private final org.springframework.beans.factory.ObjectProvider<edu.campus.events.EventStore>
      eventStores;
  private final org.springframework.beans.factory.ObjectProvider<
          edu.campus.equiflow.cluster.ClusterClient>
      clients;

  public boolean clustered() {
    return eventStores.getIfAvailable() != null;
  }

  public void maintenance(Equipment item, String reason) {
    var store = eventStores.getIfAvailable();
    if (store == null || item.getMaintenanceKey() != null) return;
    String id = java.util.UUID.randomUUID().toString();
    item.setMaintenanceKey(id);
    store.enqueue(
        new edu.campus.events.DeskEvent(
            id,
            "MAINTENANCE_REQUESTED",
            "equipment:" + item.getId(),
            null,
            null,
            item.getId(),
            item.getName(),
            "Equipment needs maintenance",
            reason,
            id,
            clock.instant()));
  }

  public void record(AppUser actor, String action, String type, Long targetId, String details) {
    AuditEvent event = new AuditEvent();
    event.setActor(actor);
    event.setAction(action);
    event.setTargetType(type);
    event.setTargetId(targetId);
    event.setDetails(details);
    event.setCreatedAt(clock.instant());
    audits.save(event);
  }

  public void notify(Reservation reservation, String title, String message, String key) {
    var store = eventStores.getIfAvailable();
    if (store != null) {
      String id =
          java.util
              .UUID
              .nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))
              .toString();
      store.enqueue(
          new edu.campus.events.DeskEvent(
              id,
              "NOTIFICATION_REQUESTED",
              "equipment:" + reservation.getEquipment().getId(),
              reservation.getRequester().getId(),
              reservation.getId(),
              reservation.getEquipment().getId(),
              reservation.getEquipment().getName(),
              title,
              message,
              key,
              clock.instant()));
      return;
    }
    if (notifications.existsByDedupeKey(key)) {
      return;
    }
    Notification note = new Notification();
    note.setRecipient(reservation.getRequester());
    note.setReservationId(reservation.getId());
    note.setTitle(title);
    note.setMessage(message);
    note.setDedupeKey(key);
    note.setCreatedAt(clock.instant());
    notifications.save(note);
  }

  @Transactional(readOnly = true)
  public PageView<AuditView> auditPage(int page, int size) {
    return PageView.from(
        audits
            .findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100)))
            .map(
                event ->
                    new AuditView(
                        event.getId(),
                        event.getActor() == null ? "System" : event.getActor().getFullName(),
                        event.getAction(),
                        event.getTargetType(),
                        event.getTargetId(),
                        event.getDetails(),
                        event.getCreatedAt())));
  }

  @Transactional(readOnly = true)
  public NotificationPage notifications(AppUser user) {
    var client = clients.getIfAvailable();
    if (client != null)
      return client.call(
          "EquiFlow-notification-service",
          "/internal/notifications/" + user.getId(),
          org.springframework.http.HttpMethod.GET,
          null,
          NotificationPage.class);
    var page =
        notifications.findByRecipientIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 30));
    return new NotificationPage(
        page.map(
                note ->
                    new NotificationView(
                        note.getId(),
                        note.getReservationId(),
                        note.getTitle(),
                        note.getMessage(),
                        note.getCreatedAt(),
                        note.getReadAt()))
            .getContent(),
        notifications.countByRecipientIdAndReadAtIsNull(user.getId()),
        page.getTotalElements());
  }

  @Transactional
  public void readNotification(Long id, AppUser user) {
    var client = clients.getIfAvailable();
    if (client != null) {
      client.call(
          "EquiFlow-notification-service",
          "/internal/notifications/" + user.getId() + "/" + id + "/read",
          org.springframework.http.HttpMethod.PATCH,
          null,
          Void.class);
      return;
    }
    Notification note =
        notifications
            .findByIdAndRecipientId(id, user.getId())
            .orElseThrow(() -> ApiException.notFound("Notification not found."));
    if (note.getReadAt() == null) {
      note.setReadAt(clock.instant());
    }
  }
}
