package edu.campus.equiflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import edu.campus.events.*;
import edu.campus.equiflow.domain.*;
import edu.campus.equiflow.repository.*;
import edu.campus.equiflow.service.*;
import edu.campus.equiflow.web.Models.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:cluster-tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "cluster.enabled=true",
      "cluster.service-key=cluster-test-service-key-1234567890",
      "cluster.publish-enabled=false",
      "spring.cloud.config.enabled=false",
      "eureka.client.enabled=false",
      "spring.kafka.listener.auto-startup=false",
      "spring.kafka.admin.auto-create=false",
      "desk.seed-demo=false",
      "management.tracing.enabled=false"
    })
class ClusterTransactionTest {
  @Autowired ReservationService service;
  @Autowired EquipmentService equipmentService;
  @Autowired EquipmentRepository equipment;
  @Autowired UserRepository users;
  @Autowired ReservationRepository reservations;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @MockitoSpyBean EventStore events;
  AppUser student, admin;
  Equipment camera;

  @BeforeEach
  void prepare() {
    reset(events);
    jdbc.update("DELETE FROM event_inbox");
    jdbc.update("DELETE FROM event_outbox");
    jdbc.update("DELETE FROM audit_events");
    reservations.deleteAll();
    equipment.deleteAll();
    users.deleteAll();
    student = newUser("Student", Role.STUDENT);
    admin = newUser("Admin", Role.ADMIN);
    camera = new Equipment();
    camera.setAssetTag("TEST-CAMERA");
    camera.setName("Test Camera");
    camera.setDescription("Complete camera kit");
    camera.setCategory(Category.CAMERA);
    camera.setLocation("Desk");
    camera.setStatus(EquipmentStatus.READY);
    camera.setCreatedAt(Instant.now());
    camera = equipment.saveAndFlush(camera);
  }

  AppUser newUser(String name, Role role) {
    var u = new AppUser();
    u.setEmail(name.toLowerCase() + "@test.test");
    u.setFullName(name);
    u.setRole(role);
    u.setPasswordHash("not-used");
    u.setCreatedAt(Instant.now());
    return users.saveAndFlush(u);
  }

  ReservationView request() {
    var today = LocalDate.now(ZoneId.of("Asia/Singapore"));
    return service.create(
        new ReservationInput(
            camera.getId(),
            today,
            today.plusDays(1),
            "Recording a campus presentation.",
            UUID.randomUUID().toString()),
        student);
  }

  @Test
  void approvalAndOutboxAreOneTransaction() {
    var r = request();
    doThrow(new IllegalStateException("Outbox storage failure")).when(events).enqueue(any());
    assertThatThrownBy(
            () ->
                service.transition(
                    r.id(), "approve", new ActionInput(r.version(), "Approved."), admin))
        .isInstanceOf(IllegalStateException.class);
    assertThat(service.detail(r.id(), admin).status()).isEqualTo(ReservationStatus.PENDING);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Long.class)).isEqualTo(1);
    assertThat(events.status().get("pending")).isEqualTo(0L);
  }

  @Test
  void damagedReturnQueuesMaintenanceAndBlocksReservations() {
    var r = request();
    r = service.transition(r.id(), "approve", new ActionInput(r.version(), "Approved."), admin);
    r = service.transition(r.id(), "checkout", new ActionInput(r.version(), "Collected."), admin);
    r =
        service.transition(
            r.id(),
            "return",
            new ActionInput(r.version(), "Camera battery compartment damaged.", true),
            admin);
    assertThat(r.status()).isEqualTo(ReservationStatus.RETURNED);
    assertThat(equipment.findById(camera.getId()).orElseThrow().getStatus())
        .isEqualTo(EquipmentStatus.MAINTENANCE);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_outbox WHERE event_type='MAINTENANCE_REQUESTED'",
                Long.class))
        .isEqualTo(1);
    assertThatThrownBy(this::request)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("not accepting");
  }

  @Test
  void equipmentDeletionAndItsEventCommitTogether() {
    var request = request();
    service.transition(
        request.id(), "approve", new ActionInput(request.version(), "Approved."), admin);
    Long equipmentId = camera.getId();
    doThrow(new IllegalStateException("Outbox storage failure")).when(events).enqueue(any());
    assertThatThrownBy(() -> equipmentService.delete(equipmentId))
        .isInstanceOf(IllegalStateException.class);
    assertThat(equipment.existsById(equipmentId)).isTrue();
    assertThat(reservations.existsById(request.id())).isTrue();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Long.class)).isEqualTo(2L);
    assertThat(events.equipmentDeleted(equipmentId)).isFalse();
    assertThat(events.status().get("pending")).isEqualTo(1L);

    reset(events);
    equipmentService.delete(equipmentId);
    assertThat(equipment.existsById(equipmentId)).isFalse();
    assertThat(reservations.existsById(request.id())).isFalse();
    assertThat(events.equipmentDeleted(equipmentId)).isTrue();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Long.class)).isZero();
    assertThat(events.pending())
        .singleElement()
        .satisfies(
            row -> {
              var event = json.readValue(row.get("payload").toString(), DeskEvent.class);
              assertThat(event.type()).isEqualTo("EQUIPMENT_DELETED");
              assertThat(event.aggregateKey()).isEqualTo("equipment:" + equipmentId);
              assertThat(event.equipmentId()).isEqualTo(equipmentId);
              assertThat(event.reservationIds()).containsExactly(request.id());
            });
  }
}
