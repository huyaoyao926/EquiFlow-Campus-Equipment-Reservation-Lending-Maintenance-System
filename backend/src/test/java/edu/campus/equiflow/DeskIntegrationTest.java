package edu.campus.equiflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.campus.equiflow.domain.*;
import edu.campus.equiflow.repository.*;
import edu.campus.equiflow.service.*;
import edu.campus.equiflow.web.ApiException;
import edu.campus.equiflow.web.Models.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "cluster.enabled=false",
      "spring.cloud.config.enabled=false",
      "eureka.client.enabled=false",
      "desk.seed-demo=false",
      "desk.jobs.cron=0 0 0 1 1 *"
    })
@AutoConfigureMockMvc
@Import(DeskIntegrationTest.ClockConfiguration.class)
class DeskIntegrationTest {
  @Autowired ReservationService service;
  @Autowired EquipmentService equipmentService;
  @Autowired UserService userService;
  @Autowired UserRepository users;
  @Autowired EquipmentRepository equipment;
  @Autowired ReservationRepository reservations;
  @Autowired AuditRepository audits;
  @Autowired NotificationRepository notifications;
  @Autowired PasswordEncoder passwords;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired AdjustableClock clock;
  @MockitoSpyBean ActivityService activity;
  AppUser student;
  AppUser otherStudent;
  AppUser admin;
  Equipment camera;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    String url =
        System.getProperty(
            "test.database.url",
            System.getenv()
                .getOrDefault(
                    "TEST_DATABASE_URL",
                    "jdbc:h2:mem:equiflow-tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"));
    registry.add("spring.datasource.url", () -> url);
    registry.add(
        "spring.jpa.hibernate.ddl-auto",
        () -> url.startsWith("jdbc:mysql") ? "update" : "validate");
    registry.add(
        "spring.datasource.username",
        () ->
            System.getProperty(
                "test.database.user", System.getenv().getOrDefault("TEST_DATABASE_USER", "sa")));
    registry.add(
        "spring.datasource.password",
        () ->
            System.getProperty(
                "test.database.password",
                System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "")));
    registry.add(
        "spring.sql.init.schema-locations",
        () ->
            url.startsWith("jdbc:mysql")
                ? "classpath:db/schema-mysql.sql"
                : "classpath:db/schema.sql");
  }

  @BeforeEach
  void prepare() {
    reset(activity);
    clock.set(Instant.parse("2026-11-02T02:00:00Z"));
    notifications.deleteAll();
    audits.deleteAll();
    reservations.deleteAll();
    equipment.deleteAll();
    users.deleteAll();
    student = newUser("Alex Morgan", "alex@campus.test", Role.STUDENT);
    otherStudent = newUser("Jordan Lee", "jordan@campus.test", Role.STUDENT);
    admin = newUser("Sam Taylor", "desk@campus.test", Role.ADMIN);
    camera = new Equipment();
    camera.setAssetTag("CAM-001");
    camera.setName("Canon EOS R50");
    camera.setCategory(Category.CAMERA);
    camera.setDescription("Camera with lens, battery and carry bag.");
    camera.setLocation("Media Lab");
    camera.setStatus(EquipmentStatus.READY);
    camera.setCreatedAt(clock.instant());
    camera = equipment.saveAndFlush(camera);
  }

  @Test
  void actionPayloadWithoutDamageFlagRemainsCompatible() throws Exception {
    var request = create(student, today(), today());
    mvc.perform(
            post("/api/reservations/" + request.id() + "/approve")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "version",
                            request.version(),
                            "note",
                            "Approved for the campus project."))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  @Test
  void completeLoanWorkflowRecordsAuditAndNotifications() {
    ReservationView request = create(student, today(), today().plusDays(1));
    assertThat(request.status()).isEqualTo(ReservationStatus.PENDING);
    request = act(request, "approve", admin);
    assertThat(request.status()).isEqualTo(ReservationStatus.APPROVED);
    request = act(request, "checkout", admin);
    assertThat(request.checkedOutAt()).isEqualTo(clock.instant());
    request = act(request, "return", admin);
    assertThat(request.status()).isEqualTo(ReservationStatus.RETURNED);
    assertThat(request.returnedAt()).isNotNull();
    assertThat(audits.count()).isEqualTo(4);
    assertThat(notifications.count()).isEqualTo(3);
    assertThat(equipmentService.detail(camera.getId(), student).equipment().availability())
        .isEqualTo("AVAILABLE");
  }

  @Test
  void administratorDeletionRemovesOnlyTheSelectedEquipmentAndItsRecords() throws Exception {
    var request = create(student, today(), today().plusDays(1));
    act(act(act(request, "approve", admin), "checkout", admin), "return", admin);
    var other =
        equipmentService.create(
            new EquipmentInput(
                "CAM-002",
                "Second Camera",
                Category.CAMERA,
                "Spare camera kit.",
                "Media Lab",
                EquipmentStatus.READY,
                null),
            admin);
    var otherRequest =
        service.create(
            new ReservationInput(
                other.id(), today(), today(), "Record a second project.", "other-camera-request"),
            otherStudent);
    act(otherRequest, "approve", admin);
    String url = "/api/equipment/" + camera.getId();
    mvc.perform(delete(url).with(user(student.getEmail()).roles("STUDENT")).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(delete(url).with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isForbidden());
    assertThat(equipment.existsById(camera.getId())).isTrue();
    mvc.perform(delete(url).with(user(admin.getEmail()).roles("ADMIN")).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get(url).with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isNotFound());
    assertThat(equipment.findAll()).extracting(Equipment::getId).containsExactly(other.id());
    assertThat(reservations.findAll())
        .extracting(Reservation::getId)
        .containsExactly(otherRequest.id());
    assertThat(notifications.findAll())
        .extracting(Notification::getReservationId)
        .containsExactly(otherRequest.id());
    assertThat(audits.findAll())
        .allMatch(
            audit ->
                audit
                    .getTargetId()
                    .equals(
                        audit.getTargetType().equals("EQUIPMENT") ? other.id() : otherRequest.id()))
        .hasSize(3);
    assertThat(users.count()).isEqualTo(3);
  }

  @Test
  void competingRequestsCanBePendingButOnlyOneCanBeApproved() {
    var first = create(student, today(), today().plusDays(2));
    var second = create(otherStudent, today(), today().plusDays(1));
    act(first, "approve", admin);
    assertThatThrownBy(() -> act(second, "approve", admin))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("approved reservation");
    assertThat(service.detail(second.id(), admin).status()).isEqualTo(ReservationStatus.PENDING);
    assertThat(audits.count()).isEqualTo(3);
  }

  @Test
  void simultaneousApprovalsNeverAllocateOneAssetTwice() throws Exception {
    var first = create(student, today(), today().plusDays(1));
    var second = create(otherStudent, today(), today().plusDays(1));
    CyclicBarrier barrier = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var futures =
          List.of(first, second).stream()
              .map(
                  request ->
                      pool.submit(
                          () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            try {
                              act(request, "approve", admin);
                              return "APPROVED";
                            } catch (ApiException failure) {
                              return failure.status().name();
                            }
                          }))
              .toList();
      List<String> outcomes = new ArrayList<>();
      for (var future : futures) outcomes.add(future.get(15, TimeUnit.SECONDS));
      assertThat(outcomes).containsExactlyInAnyOrder("APPROVED", "CONFLICT");
    }
    assertThat(service.list(admin, ReservationStatus.APPROVED, false, "", 0, 10).total())
        .isEqualTo(1);
  }

  @Test
  void approvalAndAuditRollbackIfNotificationPersistenceFails() {
    var request = create(student, today(), today());
    long auditCount = audits.count();
    doThrow(new IllegalStateException("Simulated notification failure"))
        .when(activity)
        .notify(any(Reservation.class), anyString(), anyString(), anyString());
    assertThatThrownBy(() -> act(request, "approve", admin))
        .isInstanceOf(IllegalStateException.class);
    assertThat(service.detail(request.id(), admin).status()).isEqualTo(ReservationStatus.PENDING);
    assertThat(audits.count()).isEqualTo(auditCount);
    assertThat(notifications.count()).isZero();
    assertThat(equipmentService.detail(camera.getId(), student).reservedDates()).isEmpty();
  }

  @Test
  void retryingTheSameSubmissionReturnsTheOriginalRequest() {
    var input = input(today(), today().plusDays(1));
    var first = service.create(input, student);
    var second = service.create(input, student);
    assertThat(second.id()).isEqualTo(first.id());
    assertThat(reservations.count()).isEqualTo(1);
    assertThat(audits.count()).isEqualTo(1);
  }

  @Test
  void simultaneousSubmissionRetriesRemainIdempotent() throws Exception {
    var input = input(today(), today().plusDays(1));
    CyclicBarrier barrier = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Long> task =
          () -> {
            barrier.await(5, TimeUnit.SECONDS);
            return service.create(input, student).id();
          };
      var first = pool.submit(task);
      var second = pool.submit(task);
      assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
    }
    assertThat(reservations.count()).isEqualTo(1);
  }

  @Test
  void aSubmissionKeyCannotBeReusedForDifferentContent() {
    var original = input(today(), today());
    service.create(original, student);
    var changed =
        new ReservationInput(
            camera.getId(),
            today(),
            today(),
            "A different project purpose.",
            original.requestKey());
    assertThatThrownBy(() -> service.create(changed, student))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("different request");
  }

  @Test
  void duplicateActiveRequestsAreRejectedEvenWithNewKeys() {
    create(student, today(), today().plusDays(1));
    assertThatThrownBy(() -> create(student, today(), today()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("already have an active request");
  }

  @Test
  void reservationBoundariesAreInclusiveAndAdjacentDaysAreAllowed() {
    act(create(student, today(), today().plusDays(1)), "approve", admin);
    assertThatThrownBy(() -> create(otherStudent, today().plusDays(1), today().plusDays(2)))
        .isInstanceOf(ApiException.class);
    var next = create(otherStudent, today().plusDays(2), today().plusDays(3));
    assertThat(act(next, "approve", admin).status()).isEqualTo(ReservationStatus.APPROVED);
  }

  @Test
  void datesMustRespectPastDurationAndAdvanceLimits() {
    assertThatThrownBy(() -> create(student, today().minusDays(1), today()))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> create(student, today(), today().plusDays(7)))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> create(student, today().plusDays(60), today().plusDays(61)))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> create(student, today().plusDays(2), today()))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void cancellationReleasesTheApprovedDates() {
    var approved = act(create(student, today(), today()), "approve", admin);
    assertThat(act(approved, "cancel", student).status()).isEqualTo(ReservationStatus.CANCELLED);
    assertThat(act(create(otherStudent, today(), today()), "approve", admin).status())
        .isEqualTo(ReservationStatus.APPROVED);
  }

  @Test
  void studentsCannotReadOrCancelOtherStudentsRequests() {
    var request = create(student, today(), today());
    assertThatThrownBy(() -> service.detail(request.id(), otherStudent))
        .isInstanceOf(ApiException.class)
        .hasMessage("Request not found.");
    assertThatThrownBy(() -> act(request, "cancel", otherStudent)).isInstanceOf(ApiException.class);
    assertThat(service.list(otherStudent, null, false, "", 0, 10).total()).isZero();
    assertThat(service.exportCsv(otherStudent, null, false, "")).doesNotContain("Canon EOS R50");
  }

  @Test
  void studentApprovalIsDeniedAndFutureCheckoutIsRejected() {
    var request = create(student, today().plusDays(1), today().plusDays(2));
    assertThatThrownBy(() -> act(request, "approve", student)).isInstanceOf(ApiException.class);
    var approved = act(request, "approve", admin);
    assertThatThrownBy(() -> act(approved, "checkout", admin))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("during the approved");
  }

  @Test
  void staleActionsAndInvalidTransitionsDoNotChangeTheRequest() {
    var pending = create(student, today(), today());
    var approved = act(pending, "approve", admin);
    assertThatThrownBy(() -> act(pending, "cancel", student))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("changed");
    assertThatThrownBy(() -> act(approved, "return", admin)).isInstanceOf(ApiException.class);
    assertThat(service.detail(pending.id(), admin).status()).isEqualTo(ReservationStatus.APPROVED);
  }

  @Test
  void decisionsRequiringAnExplanationRejectBlankNotes() {
    var pending = create(student, today(), today());
    assertThatThrownBy(
            () ->
                service.transition(
                    pending.id(), "reject", new ActionInput(pending.version(), " "), admin))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                service.transition(
                    pending.id(), "cancel", new ActionInput(pending.version(), ""), admin))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void overdueLoansBlockNewReservationsUntilReturned() {
    var loan = act(act(create(student, today(), today()), "approve", admin), "checkout", admin);
    clock.set(clock.instant().plus(Duration.ofDays(2)));
    assertThat(service.detail(loan.id(), student).overdue()).isTrue();
    assertThatThrownBy(() -> create(otherStudent, today(), today()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("overdue loan");
    long count = notifications.count();
    service.processPastReservation(loan.id());
    service.processPastReservation(loan.id());
    assertThat(notifications.count()).isEqualTo(count + 1);
    act(service.detail(loan.id(), admin), "return", admin);
    assertThat(create(otherStudent, today(), today()).status())
        .isEqualTo(ReservationStatus.PENDING);
  }

  @Test
  void pastUncollectedReservationsExpireOnce() {
    var approved = act(create(student, today(), today()), "approve", admin);
    clock.set(clock.instant().plus(Duration.ofDays(2)));
    service.processPastReservation(approved.id());
    service.processPastReservation(approved.id());
    assertThat(service.detail(approved.id(), student).status())
        .isEqualTo(ReservationStatus.EXPIRED);
    assertThat(notifications.count()).isEqualTo(2);
  }

  @Test
  void equipmentWithCommittedDatesCannotBeTakenOutOfService() {
    act(create(student, today(), today()), "approve", admin);
    assertThatThrownBy(
            () ->
                equipmentService.update(
                    camera.getId(),
                    equipmentInput(EquipmentStatus.MAINTENANCE, camera.getVersion()),
                    admin))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Resolve active loans");
  }

  @Test
  void equipmentUpdatesUseVersionsAndMaintenanceBlocksReservations() {
    var updated =
        equipmentService.update(
            camera.getId(),
            equipmentInput(EquipmentStatus.MAINTENANCE, camera.getVersion()),
            admin);
    assertThat(updated.version()).isGreaterThan(camera.getVersion());
    assertThatThrownBy(
            () ->
                equipmentService.update(
                    camera.getId(),
                    equipmentInput(EquipmentStatus.READY, camera.getVersion()),
                    admin))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> create(student, today(), today()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("not accepting");
  }

  @Test
  void availableFilterExcludesConflictingDates() {
    act(create(student, today(), today()), "approve", admin);
    assertThat(equipmentService.list("", null, true, today(), today(), 0, 9, student).total())
        .isZero();
    assertThat(
            equipmentService
                .list("", null, true, today().plusDays(1), today().plusDays(1), 0, 9, student)
                .total())
        .isEqualTo(1);
  }

  @Test
  void exportedCsvNeutralizesFormulaCells() {
    service.create(
        new ReservationInput(
            camera.getId(),
            today(),
            today(),
            "=HYPERLINK(\"unsafe\")",
            UUID.randomUUID().toString()),
        student);
    assertThat(service.exportCsv(student, null, false, "")).contains("\"'=HYPERLINK");
  }

  @Test
  void registrationHashesPasswordsAndAlwaysCreatesAStudent() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"fullName\":\"Casey"
                        + " Smith\",\"email\":\"CASEY@campus.test\",\"password\":\"Student123!\",\"role\":\"ADMIN\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("STUDENT"));
    var saved = users.findByEmail("casey@campus.test").orElseThrow();
    assertThat(saved.getPasswordHash()).isNotEqualTo("Student123!");
    assertThat(passwords.matches("Student123!", saved.getPasswordHash())).isTrue();
  }

  @Test
  void apiEnforcesAuthenticationCsrfAndAdministratorPermissions() throws Exception {
    mvc.perform(get("/api/equipment")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/activity").with(user(student.getEmail()).roles("STUDENT")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/equipment")
                .with(user(student.getEmail()).roles("STUDENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(equipmentInput(EquipmentStatus.READY, null))))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/reservations")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(input(today(), today()))))
        .andExpect(status().isForbidden());
  }

  @Test
  void signInPersistsTheSessionAndSignOutRevokesIt() throws Exception {
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .param("email", student.getEmail())
                    .param("password", "Student123!"))
            .andExpect(status().isOk())
            .andReturn();
    var session = (MockHttpSession) result.getRequest().getSession(false);
    assertThat(session).isNotNull();
    mvc.perform(get("/api/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(student.getEmail()));
    mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isOk());
    assertThat(session.isInvalid()).isTrue();
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .param("email", student.getEmail())
                .param("password", "wrong-password"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void invalidInputsReturnFieldErrorsAndPrivateRecordsStayPrivate() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"A\",\"email\":\"bad\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.password").exists());
    var request = create(student, today(), today());
    mvc.perform(
            get("/api/reservations/" + request.id())
                .with(user(otherStudent.getEmail()).roles("STUDENT")))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/equipment?category=UNKNOWN").with(user(student.getEmail()).roles("STUDENT")))
        .andExpect(status().isBadRequest());
  }

  private AppUser newUser(String name, String email, Role role) {
    AppUser user = new AppUser();
    user.setFullName(name);
    user.setEmail(email);
    user.setRole(role);
    user.setPasswordHash(passwords.encode("Student123!"));
    user.setCreatedAt(clock.instant());
    return users.saveAndFlush(user);
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  private ReservationInput input(LocalDate first, LocalDate last) {
    return new ReservationInput(
        camera.getId(),
        first,
        last,
        "Recording an interview for a student project.",
        UUID.randomUUID().toString());
  }

  private ReservationView create(AppUser actor, LocalDate first, LocalDate last) {
    return service.create(input(first, last), actor);
  }

  private ReservationView act(ReservationView view, String action, AppUser actor) {
    return service.transition(
        view.id(),
        action,
        new ActionInput(view.version(), "Recorded at the equipment desk."),
        actor);
  }

  private EquipmentInput equipmentInput(EquipmentStatus status, Long version) {
    return new EquipmentInput(
        camera.getAssetTag(),
        camera.getName(),
        camera.getCategory(),
        camera.getDescription(),
        camera.getLocation(),
        status,
        version);
  }

  @TestConfiguration
  static class ClockConfiguration {
    @Bean
    @Primary
    AdjustableClock testClock() {
      return new AdjustableClock();
    }
  }

  static class AdjustableClock extends Clock {
    private final AtomicReference<Instant> current =
        new AtomicReference<>(Instant.parse("2026-11-02T02:00:00Z"));

    void set(Instant instant) {
      current.set(instant);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("Asia/Singapore");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(current.get(), zone);
    }

    @Override
    public Instant instant() {
      return current.get();
    }
  }
}
