package edu.campus.equiflow.service;

import edu.campus.equiflow.domain.*;
import edu.campus.equiflow.repository.UserRepository;
import edu.campus.equiflow.web.ApiException;
import edu.campus.equiflow.web.Models.*;
import java.time.Clock;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final Clock clock;
  private final org.springframework.beans.factory.ObjectProvider<
          edu.campus.equiflow.cluster.ClusterClient>
      clients;

  @Transactional
  public AppUser syncRemote(java.util.Map profile) {
    String email = profile.get("email").toString();
    AppUser user = users.findByEmail(email).orElseGet(AppUser::new);
    user.setEmail(email);
    user.setFullName(profile.get("fullName").toString());
    user.setRole(Role.valueOf(profile.get("role").toString()));
    user.setPasswordHash("REMOTE_IDENTITY");
    if (user.getCreatedAt() == null) user.setCreatedAt(clock.instant());
    return users.saveAndFlush(user);
  }

  @Transactional
  public UserView register(Registration input) {
    var client = clients.getIfAvailable();
    if (client != null) {
      var profile =
          client.call(
              "EquiFlow-identity-service",
              "/internal/accounts",
              org.springframework.http.HttpMethod.POST,
              input,
              java.util.Map.class);
      return view(syncRemote(profile));
    }
    String email = input.email().strip().toLowerCase(Locale.ROOT);
    if (users.existsByEmail(email)) {
      throw ApiException.conflict("An account with this email already exists.");
    }
    AppUser user = new AppUser();
    user.setFullName(input.fullName().strip());
    user.setEmail(email);
    user.setPasswordHash(passwords.encode(input.password()));
    user.setRole(Role.STUDENT);
    user.setCreatedAt(clock.instant());
    return view(users.saveAndFlush(user));
  }

  @Transactional(readOnly = true)
  public AppUser current(Authentication authentication) {
    return users
        .findByEmail(authentication.getName())
        .orElseThrow(() -> ApiException.notFound("Account not found."));
  }

  public UserView view(AppUser user) {
    return new UserView(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
  }
}
