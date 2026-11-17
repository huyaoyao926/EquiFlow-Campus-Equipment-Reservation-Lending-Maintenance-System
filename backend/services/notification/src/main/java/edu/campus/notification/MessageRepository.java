package edu.campus.notification;

import edu.campus.events.*;
import jakarta.persistence.*;
import java.util.*;
import lombok.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.web.bind.annotation.*;

public interface MessageRepository extends JpaRepository<Message, Long> {
  List<Message> findByRecipientIdOrderByCreatedAtDesc(
      Long id, org.springframework.data.domain.Pageable page);

  long countByRecipientIdAndReadAtIsNull(Long id);

  long countByRecipientId(Long id);

  Optional<Message> findByIdAndRecipientId(Long id, Long recipientId);

  @Modifying
  @Query("delete from Message m where m.reservationId in :reservationIds")
  void deleteByReservationIds(@Param("reservationIds") Collection<Long> reservationIds);
}
