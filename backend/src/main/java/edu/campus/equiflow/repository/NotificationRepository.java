package edu.campus.equiflow.repository;

import edu.campus.equiflow.domain.Notification;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long id, Pageable pageable);

  Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

  boolean existsByDedupeKey(String key);

  long countByRecipientIdAndReadAtIsNull(Long id);
}
