package edu.campus.identity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.*;
import org.springframework.boot.*;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;

@Entity
@Table(name = "identity_accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 80)
  private String fullName;

  @Column(nullable = false, unique = true, length = 120)
  private String email;

  @Column(nullable = false, length = 100)
  private String passwordHash;

  @Column(nullable = false, length = 20)
  private String role;
}
