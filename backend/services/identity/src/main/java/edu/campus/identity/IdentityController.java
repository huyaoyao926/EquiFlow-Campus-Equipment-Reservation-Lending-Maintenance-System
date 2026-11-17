package edu.campus.identity;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.*;
import org.springframework.boot.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class IdentityController {
  private final AccountRepository accounts;

  IdentityController(AccountRepository accounts) {
    this.accounts = accounts;
  }

  public record Registration(
      @NotBlank @Size(min = 2, max = 80) String fullName,
      @NotBlank @Email @Size(max = 120) String email,
      @NotBlank
          @Size(min = 10, max = 72)
          @Pattern(regexp = "(?=.*[A-Za-z])(?=.*[0-9])[\\x20-\\x7E]+")
          String password) {}

  @GetMapping("/internal/accounts")
  Account find(@RequestParam String email) {
    return accounts
        .findByEmail(email.strip().toLowerCase(Locale.ROOT))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
  }

  @PostMapping("/internal/accounts")
  @ResponseStatus(HttpStatus.CREATED)
  Map<String, Object> register(@Valid @RequestBody Registration input) {
    String email = input.email().strip().toLowerCase(Locale.ROOT);
    if (accounts.findByEmail(email).isPresent())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "An account with this email already exists.");
    var account = new Account();
    account.setFullName(input.fullName().strip());
    account.setEmail(email);
    account.setPasswordHash(new BCryptPasswordEncoder(10).encode(input.password()));
    account.setRole("STUDENT");
    accounts.saveAndFlush(account);
    return Map.of(
        "id",
        account.getId(),
        "fullName",
        account.getFullName(),
        "email",
        account.getEmail(),
        "role",
        account.getRole());
  }
}
