package edu.campus.equiflow.repository;

import edu.campus.equiflow.domain.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository
    extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {
  @Override
  @EntityGraph(attributePaths = {"equipment", "requester", "reviewedBy"})
  Page<Reservation> findAll(Specification<Reservation> specification, Pageable pageable);

  @Query("select r.equipment.id from Reservation r where r.id = :id")
  Optional<Long> findEquipmentId(@Param("id") Long id);

  Optional<Reservation> findByRequesterIdAndRequestKey(Long requesterId, String requestKey);

  @Query(
      "select count(r) from Reservation r where r.equipment.id = :equipmentId and r.id <>"
          + " :excludedId and r.status in :statuses and r.startDate <= :endDate and r.endDate >="
          + " :startDate")
  long countOverlaps(
      Long equipmentId,
      Long excludedId,
      Set<ReservationStatus> statuses,
      LocalDate startDate,
      LocalDate endDate);

  List<Reservation> findByEquipmentIdAndStatusInOrderByStartDateAsc(
      Long equipmentId, Set<ReservationStatus> statuses);

  @EntityGraph(attributePaths = "equipment")
  List<Reservation> findByEquipmentIdInAndStatusIn(
      Collection<Long> equipmentIds, Set<ReservationStatus> statuses);

  boolean existsByEquipmentIdAndStatus(Long equipmentId, ReservationStatus status);

  boolean existsByEquipmentIdAndStatusAndEndDateBefore(
      Long equipmentId, ReservationStatus status, LocalDate day);

  @Query("select r.id from Reservation r where r.status in :statuses and r.endDate < :day")
  List<Long> findPastIds(Set<ReservationStatus> statuses, LocalDate day);
}
