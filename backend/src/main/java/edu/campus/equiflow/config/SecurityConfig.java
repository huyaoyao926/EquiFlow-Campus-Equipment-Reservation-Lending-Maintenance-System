package edu.campus.equiflow.config;

import edu.campus.equiflow.repository.UserRepository;
import java.util.Locale;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
  }

  @Bean
  UserDetailsService userDetailsService(
      UserRepository users,
      edu.campus.equiflow.service.UserService profiles,
      org.springframework.beans.factory.ObjectProvider<edu.campus.equiflow.cluster.ClusterClient>
          clients) {
    return email -> {
      var client = clients.getIfAvailable();
      if (client != null) {
        try {
          var profile = client.credentials(email.strip().toLowerCase(Locale.ROOT));
          profiles.syncRemote(profile);
          return User.withUsername(profile.get("email").toString())
              .password(profile.get("passwordHash").toString())
              .roles(profile.get("role").toString())
              .build();
        } catch (edu.campus.equiflow.web.ApiException ex) {
          if (ex.status().value() == 404) throw new UsernameNotFoundException("Account not found");
          throw new org.springframework.security.authentication.AuthenticationServiceException(
              "Identity service unavailable", ex);
        }
      }
      return users
          .findByEmail(email.strip().toLowerCase(Locale.ROOT))
          .map(
              user ->
                  User.withUsername(user.getEmail())
                      .password(user.getPasswordHash())
                      .roles(user.getRole().name())
                      .build())
          .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    };
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
        auth ->
            auth.requestMatchers(
                    "/",
                    "/index.html",
                    "/assets/**",
                    "/favicon.svg",
                    "/actuator/health",
                    "/error",
                    "/api/config",
                    "/api/auth/csrf",
                    "/api/auth/register",
                    "/api/auth/login")
                .permitAll()
                .requestMatchers("/api/admin/**")
                .hasRole("ADMIN")
                .requestMatchers("/api/**")
                .authenticated()
                .anyRequest()
                .denyAll());
    http.csrf(Customizer.withDefaults());
    http.formLogin(
        form ->
            form.loginProcessingUrl("/api/auth/login")
                .usernameParameter("email")
                .successHandler(
                    (request, response, authentication) -> {
                      response.setContentType("application/json");
                      response.getWriter().write("{\"message\":\"Signed in\"}");
                    })
                .failureHandler(
                    (request, response, exception) -> {
                      response.setStatus(401);
                      response.setContentType("application/json");
                      response.getWriter().write("{\"message\":\"Invalid email or password.\"}");
                    }));
    http.logout(
        logout ->
            logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(
                    (request, response, authentication) -> {
                      response.setContentType("application/json");
                      response.getWriter().write("{\"message\":\"Signed out\"}");
                    }));
    http.exceptionHandling(
        errors ->
            errors
                .authenticationEntryPoint(
                    (request, response, exception) -> {
                      response.setStatus(401);
                      response.setContentType("application/json");
                      response.getWriter().write("{\"message\":\"Please sign in to continue.\"}");
                    })
                .accessDeniedHandler(
                    (request, response, exception) -> {
                      response.setStatus(403);
                      response.setContentType("application/json");
                      response
                          .getWriter()
                          .write(
                              "{\"message\":\"Access denied or session token expired. Refresh and"
                                  + " try again.\"}");
                    }));
    http.headers(
        headers ->
            headers.contentSecurityPolicy(
                policy ->
                    policy.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';"
                            + " img-src 'self' data:; object-src 'none'; base-uri 'self';"
                            + " frame-ancestors 'none'")));
    return http.build();
  }
}
