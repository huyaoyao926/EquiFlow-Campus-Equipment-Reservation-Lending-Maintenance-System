package edu.campus.events;

import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

public class EventStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public EventStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @PostConstruct
  void initialize() {
    jdbc.execute(
        "CREATE TABLE IF NOT EXISTS event_outbox (event_id VARCHAR(100) PRIMARY KEY, event_type"
            + " VARCHAR(60) NOT NULL, aggregate_key VARCHAR(100) NOT NULL, payload VARCHAR(8000)"
            + " NOT NULL, created_at TIMESTAMP(6) NOT NULL, published_at TIMESTAMP(6), attempts INT"
            + " NOT NULL DEFAULT 0, last_error VARCHAR(500))");
    jdbc.execute(
        "CREATE TABLE IF NOT EXISTS event_inbox (event_id VARCHAR(100) NOT NULL, handler"
            + " VARCHAR(80) NOT NULL, received_at TIMESTAMP(6) NOT NULL, PRIMARY KEY(event_id,"
            + " handler))");
  }

  public void enqueue(DeskEvent event) {
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM event_outbox WHERE event_id = ?", Long.class, event.id())
        > 0) return;
    jdbc.update(
        "INSERT INTO event_outbox(event_id,event_type,aggregate_key,payload,created_at)"
            + " VALUES(?,?,?,?,?)",
        event.id(),
        event.type(),
        event.aggregateKey(),
        json.writeValueAsString(event),
        Timestamp.from(event.occurredAt()));
  }

  public boolean seen(String id, String handler) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM event_inbox WHERE event_id=? AND handler=?",
            Long.class,
            id,
            handler)
        > 0;
  }

  public void received(String id, String handler) {
    jdbc.update(
        "INSERT INTO event_inbox(event_id,handler,received_at) VALUES(?,?,?)",
        id,
        handler,
        Timestamp.from(Instant.now()));
  }

  public boolean equipmentDeleted(Long equipmentId) {
    return equipmentId != null && seen("equipment:" + equipmentId, "equipment-deleted");
  }

  public void markEquipmentDeleted(long equipmentId) {
    if (!equipmentDeleted(equipmentId)) {
      received("equipment:" + equipmentId, "equipment-deleted");
    }
  }

  public void deleteEquipmentEvents(long equipmentId) {
    String key = "equipment:" + equipmentId;
    jdbc.update(
        "DELETE FROM event_inbox WHERE event_id IN"
            + " (SELECT event_id FROM event_outbox WHERE aggregate_key=?)",
        key);
    jdbc.update("DELETE FROM event_outbox WHERE aggregate_key=?", key);
  }

  public List<Map<String, Object>> pending() {
    return jdbc.queryForList(
        "SELECT event_id, aggregate_key, payload FROM event_outbox WHERE published_at IS NULL ORDER"
            + " BY created_at LIMIT 30");
  }

  public void published(String id) {
    jdbc.update(
        "UPDATE event_outbox SET published_at=?, attempts=attempts+1, last_error=NULL WHERE"
            + " event_id=?",
        Timestamp.from(Instant.now()),
        id);
  }

  public void failed(String id, String error) {
    jdbc.update(
        "UPDATE event_outbox SET attempts=attempts+1, last_error=? WHERE event_id=?",
        error.substring(0, Math.min(error.length(), 500)),
        id);
  }

  public Map<String, Object> status() {
    return Map.of(
        "pending",
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM event_outbox WHERE published_at IS NULL", Long.class),
        "published",
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM event_outbox WHERE published_at IS NOT NULL", Long.class),
        "received",
        jdbc.queryForObject("SELECT COUNT(*) FROM event_inbox", Long.class),
        "events",
        jdbc.queryForList(
            "SELECT event_id,event_type,created_at,published_at,attempts,last_error FROM"
                + " event_outbox ORDER BY created_at DESC LIMIT 20"));
  }

  @Transactional
  public void replay(String id) {
    if (jdbc.update("UPDATE event_outbox SET published_at=NULL WHERE event_id=?", id) == 0)
      throw new IllegalArgumentException("Event not found.");
  }
}
