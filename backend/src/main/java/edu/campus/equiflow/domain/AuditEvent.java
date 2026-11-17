package edu.campus.equiflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  private AppUser actor;

  @Column(nullable = false, length = 40)
  private String action;

  @Column(nullable = false, length = 30)
  private String targetType;

  @Column(nullable = false)
  private Long targetId;

  @Column(nullable = false, length = 600)
  private String details;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(nullable = false)
  private Instant createdAt;
}
