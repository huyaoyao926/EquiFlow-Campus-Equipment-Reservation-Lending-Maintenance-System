package edu.campus.maintenance;

import edu.campus.events.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import lombok.*;
import org.springframework.web.bind.annotation.*;

@Entity
@Table(name = "maintenance_jobs")
@Getter
@Setter
@NoArgsConstructor
public class RepairJob {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String sourceEventId;

  @Column(nullable = false)
  private Long equipmentId;
  private String equipmentName;

  @Column(length = 1000)
  private String reason;

  private String status;

  @Column(length = 500)
  private String resolution;

  private Instant createdAt;
  private Instant completedAt;
  @Version private Long version;
}
