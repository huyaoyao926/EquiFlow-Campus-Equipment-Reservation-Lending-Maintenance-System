package edu.campus.notification;

import edu.campus.events.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;
import org.springframework.web.bind.annotation.*;

@Entity
@Table(name = "notification_messages")
@Getter
@Setter
@NoArgsConstructor
public class Message {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long recipientId;

  @Column(nullable = false)
  private Long reservationId;

  @Column(nullable = false, unique = true, length = 100)
  private String eventId;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(nullable = false, length = 1000)
  private String message;

  private Instant createdAt;
  private Instant readAt;
}
