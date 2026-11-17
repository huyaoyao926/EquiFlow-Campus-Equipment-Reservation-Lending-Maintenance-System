package edu.campus.events;

import java.time.Instant;
import java.util.List;

public record DeskEvent(
    String id,
    String type,
    String aggregateKey,
    Long userId,
    Long reservationId,
    Long equipmentId,
    String equipmentName,
    String title,
    String message,
    String correlationId,
    Instant occurredAt,
    List<Long> reservationIds) {
  public DeskEvent(
      String id,
      String type,
      String aggregateKey,
      Long userId,
      Long reservationId,
      Long equipmentId,
      String equipmentName,
      String title,
      String message,
      String correlationId,
      Instant occurredAt) {
    this(
        id,
        type,
        aggregateKey,
        userId,
        reservationId,
        equipmentId,
        equipmentName,
        title,
        message,
        correlationId,
        occurredAt,
        null);
  }
}
