package edu.campus.events;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class InternalSecurity {
  @Bean
  SecurityFilterChain internalChain(HttpSecurity http, @Value("${cluster.service-key}") String key)
      throws Exception {
    if (key.length() < 32)
      throw new IllegalStateException("SERVICE_KEY must contain at least 32 characters.");
    http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.addFilterBefore(
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              HttpServletRequest request, HttpServletResponse response, FilterChain chain)
              throws ServletException, IOException {
            if (request.getRequestURI().equals("/actuator/health")) {
              chain.doFilter(request, response);
              return;
            }
            String supplied = request.getHeader("X-Service-Key");
            if (supplied == null
                || !MessageDigest.isEqual(
                    key.getBytes(StandardCharsets.UTF_8),
                    supplied.getBytes(StandardCharsets.UTF_8))) {
              response.setStatus(401);
              response.setContentType("application/json");
              response.getWriter().write("{\"message\":\"Service authentication required.\"}");
              return;
            }
            chain.doFilter(request, response);
          }
        },
        UsernamePasswordAuthenticationFilter.class);
    http.authorizeHttpRequests(a -> a.anyRequest().permitAll());
    return http.build();
  }
}
