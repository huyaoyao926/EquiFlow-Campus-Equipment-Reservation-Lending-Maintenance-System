package edu.campus.maintenance;

import edu.campus.events.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.web.bind.annotation.*;

public interface RepairRepository extends JpaRepository<RepairJob, Long> {
  List<RepairJob> findAllByOrderByCreatedAtDesc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from RepairJob j where j.id = :id")
  Optional<RepairJob> lockById(@Param("id") Long id);

  @Modifying
  @Query("delete from RepairJob j where j.equipmentId = :equipmentId")
  void deleteByEquipmentId(@Param("equipmentId") Long equipmentId);
}
