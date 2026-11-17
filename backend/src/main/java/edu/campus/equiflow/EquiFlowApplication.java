package edu.campus.equiflow;

import edu.campus.persistence.RequiredReference;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@org.springframework.context.annotation.Import(edu.campus.events.EventConfiguration.class)
@SpringBootApplication
@EnableScheduling
@EnableMethodSecurity
@EnableConfigurationProperties(DeskProperties.class)
public class EquiFlowApplication {
  public static void main(String[] args) {
    SpringApplication.run(EquiFlowApplication.class, args);
  }

  @Bean
  Clock campusClock(DeskProperties properties) {
    return Clock.system(ZoneId.of(properties.timezone()));
  }

  @Bean
  @DependsOn("entityManagerFactory")
  InitializingBean requireNotificationReservation(JdbcTemplate jdbc) {
    return () -> RequiredReference.enforce(jdbc, "notifications", "reservation_id");
  }
}
