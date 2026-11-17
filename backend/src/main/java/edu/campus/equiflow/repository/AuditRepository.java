package edu.campus.equiflow.repository;

import edu.campus.equiflow.domain.AuditEvent;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface AuditRepository extends JpaRepository<AuditEvent, Long> {
  @EntityGraph(attributePaths = "actor")
  Page<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
