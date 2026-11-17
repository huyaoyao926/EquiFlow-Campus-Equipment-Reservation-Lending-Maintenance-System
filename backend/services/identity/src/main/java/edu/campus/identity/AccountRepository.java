package edu.campus.identity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.*;
import org.springframework.boot.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.*;

public interface AccountRepository extends JpaRepository<Account, Long> {
  Optional<Account> findByEmail(String email);
}
