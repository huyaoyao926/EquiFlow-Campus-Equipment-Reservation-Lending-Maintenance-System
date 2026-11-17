package edu.campus.equiflow.repository;

import edu.campus.equiflow.domain.Equipment;
import edu.campus.equiflow.domain.EquipmentStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EquipmentRepository
    extends JpaRepository<Equipment, Long>, JpaSpecificationExecutor<Equipment> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Equipment e where e.id = :id")
  Optional<Equipment> lockById(@Param("id") Long id);

  boolean existsByAssetTag(String tag);

  long countByStatus(EquipmentStatus status);
}
