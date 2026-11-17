package edu.campus.notification;

import edu.campus.events.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class NotificationController {
  private final MessageRepository messages;
  private final EventStore events;
  private final KafkaListenerEndpointRegistry registry;

  NotificationController(
      MessageRepository messages, EventStore events, KafkaListenerEndpointRegistry registry) {
    this.messages = messages;
    this.events = events;
    this.registry = registry;
  }

  @GetMapping("/internal/notifications/{userId}")
  Map<String, Object> list(@PathVariable Long userId) {
    return Map.of(
        "items",
        messages.findByRecipientIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 30)),
        "unread",
        messages.countByRecipientIdAndReadAtIsNull(userId),
        "total",
        messages.countByRecipientId(userId));
  }

  @PatchMapping("/internal/notifications/{userId}/{id}/read")
  @Transactional
  void read(@PathVariable Long userId, @PathVariable Long id) {
    var note =
        messages
            .findByIdAndRecipientId(id, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    note.setReadAt(Instant.now());
  }

  @GetMapping("/internal/events")
  Map<String, Object> status() {
    var result = new LinkedHashMap<>(events.status());
    result.put(
        "paused", registry.getListenerContainer("notification-listener").isContainerPaused());
    result.put("messages", messages.count());
    return result;
  }

  @PostMapping("/internal/consumer/{action}")
  Map<String, Object> control(@PathVariable String action) {
    var container = registry.getListenerContainer("notification-listener");
    if (action.equals("pause")) container.pause();
    else if (action.equals("resume")) container.resume();
    else throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return status();
  }
}
