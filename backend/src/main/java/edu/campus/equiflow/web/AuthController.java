package edu.campus.equiflow.web;

import edu.campus.equiflow.DeskProperties;
import edu.campus.equiflow.service.UserService;
import edu.campus.equiflow.web.Models.*;
import jakarta.validation.Valid;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AuthController {
  private final UserService users;
  private final DeskProperties properties;
  private final Clock clock;

  public record CsrfView(String headerName, String token) {}

  @GetMapping("/api/auth/csrf")
  CsrfView csrf(CsrfToken token) {
    return new CsrfView(token.getHeaderName(), token.getToken());
  }

  @GetMapping("/api/auth/me")
  UserView me(Authentication authentication) {
    return users.view(users.current(authentication));
  }

  @PostMapping("/api/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  UserView register(@Valid @RequestBody Registration input) {
    return users.register(input);
  }

  @GetMapping("/api/config")
  CampusConfig config() {
    return new CampusConfig(
        LocalDate.now(clock),
        properties.timezone(),
        properties.booking().maxDays(),
        properties.booking().advanceDays(),
        properties.seedDemo());
  }
}
