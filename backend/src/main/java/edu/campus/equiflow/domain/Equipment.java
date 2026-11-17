package edu.campus.equiflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "equipment")
@Getter
@Setter
@NoArgsConstructor
public class Equipment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 32)
  private String assetTag;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private Category category;

  @Column(nullable = false, length = 1000)
  private String description;

  @Column(nullable = false, length = 100)
  private String location;

  @Column(length = 100)
  private String maintenanceKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EquipmentStatus status;

  @Version
  @Column(nullable = false)
  private Long version;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(nullable = false)
  private Instant createdAt;
}
