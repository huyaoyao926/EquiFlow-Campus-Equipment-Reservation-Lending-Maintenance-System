package edu.campus.identity;

import edu.campus.events.InternalSecurity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@Import(InternalSecurity.class)
public class IdentityApplication {
  public static void main(String[] args) {
    SpringApplication.run(IdentityApplication.class, args);
  }

  @Bean
  CommandLineRunner accounts(AccountRepository users) {
    return args -> {
      var encoder = new BCryptPasswordEncoder(10);
      for (var data :
          List.of(
              new String[] {"Alex Morgan", "alex@campus.test", "Student123!", "STUDENT"},
              new String[] {"Jordan Lee", "jordan@campus.test", "Student123!", "STUDENT"},
              new String[] {"Sam Taylor", "desk@campus.test", "DeskAdmin123!", "ADMIN"})) {
        if (users.findByEmail(data[1]).isEmpty()) {
          var account = new Account();
          account.setFullName(data[0]);
          account.setEmail(data[1]);
          account.setPasswordHash(encoder.encode(data[2]));
          account.setRole(data[3]);
          users.save(account);
        }
      }
    };
  }
}
