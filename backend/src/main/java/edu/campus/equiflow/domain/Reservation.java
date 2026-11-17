package edu.campus.equiflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
public class Reservation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Equipment equipment;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private AppUser requester;

  @Column(nullable = false)
  private LocalDate startDate;

  @Column(nullable = false)
  private LocalDate endDate;

  @Column(nullable = false, length = 500)
  private String purpose;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ReservationStatus status;

  @Column(nullable = false, length = 36)
  private String requestKey;

  @Column(length = 500)
  private String reviewNote;

  @ManyToOne(fetch = FetchType.LAZY)
  private AppUser reviewedBy;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  private Instant reviewedAt;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  private Instant checkedOutAt;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  private Instant returnedAt;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(nullable = false)
  private Instant createdAt;

  @Version
  @Column(nullable = false)
  private Long version;
}
