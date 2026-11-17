package edu.campus.maintenance;

import edu.campus.events.EventConfiguration;
import edu.campus.events.InternalSecurity;
import edu.campus.persistence.RequiredReference;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
@Import({EventConfiguration.class, InternalSecurity.class})
public class MaintenanceApplication {
  public static void main(String[] args) {
    SpringApplication.run(MaintenanceApplication.class, args);
  }

  @Bean
  @DependsOn("entityManagerFactory")
  InitializingBean requireRepairEquipment(JdbcTemplate jdbc) {
    return () -> RequiredReference.enforce(jdbc, "maintenance_jobs", "equipment_id");
  }
}
