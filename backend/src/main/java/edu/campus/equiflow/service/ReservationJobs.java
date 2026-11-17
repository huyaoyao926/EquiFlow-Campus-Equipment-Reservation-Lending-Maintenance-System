package edu.campus.equiflow.service;

import edu.campus.equiflow.domain.ReservationStatus;
import edu.campus.equiflow.repository.ReservationRepository;
import java.time.*;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationJobs {
  private static final Logger log = LoggerFactory.getLogger(ReservationJobs.class);
  private final ReservationRepository reservations;
  private final ReservationService service;
  private final Clock clock;

  @Scheduled(cron = "${desk.jobs.cron}", zone = "${desk.timezone}")
  public void reconcile() {
    var ids =
        reservations.findPastIds(
            Set.of(
                ReservationStatus.PENDING,
                ReservationStatus.APPROVED,
                ReservationStatus.CHECKED_OUT),
            LocalDate.now(clock));
    for (Long id : ids) {
      try {
        service.processPastReservation(id);
      } catch (RuntimeException ex) {
        log.warn("Could not reconcile reservation {}: {}", id, ex.getMessage());
      }
    }
  }
}
