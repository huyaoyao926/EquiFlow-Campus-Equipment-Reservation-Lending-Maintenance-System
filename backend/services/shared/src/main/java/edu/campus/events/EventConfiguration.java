package edu.campus.events;

import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "cluster.enabled", havingValue = "true")
public class EventConfiguration {
  @Bean
  EventStore eventStore(JdbcTemplate jdbc, ObjectMapper json) {
    return new EventStore(jdbc, json);
  }

  @Bean
  NewTopic deskEvents() {
    return TopicBuilder.name("equiflow.events").partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic deadLetterEvents() {
    return TopicBuilder.name("equiflow.events.DLT").partitions(3).replicas(1).build();
  }

  @Bean
  DefaultErrorHandler eventErrors(KafkaTemplate<String, String> kafka, ObjectMapper json) {
    var handler =
        new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(kafka), new FixedBackOff(1000, 2));
    handler.setBackOffFunction(
        (record, failure) -> {
          if (record.value() instanceof String payload) {
            try {
              if ("EQUIPMENT_DELETED".equals(json.readTree(payload).path("type").asText())) {
                return new FixedBackOff(1000, FixedBackOff.UNLIMITED_ATTEMPTS);
              }
            } catch (JacksonException ignored) {
              return null;
            }
          }
          return null;
        });
    return handler;
  }

  @Bean
  @ConditionalOnProperty(
      name = "cluster.publish-enabled",
      havingValue = "true",
      matchIfMissing = true)
  OutboxPublisher outboxPublisher(EventStore store, KafkaTemplate<String, String> kafka) {
    return new OutboxPublisher(store, kafka);
  }

  public static class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final EventStore store;
    private final KafkaTemplate<String, String> kafka;

    OutboxPublisher(EventStore store, KafkaTemplate<String, String> kafka) {
      this.store = store;
      this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${cluster.publish-delay:1000}", initialDelay = 2000)
    public void publish() {
      for (var row : store.pending()) {
        String id = row.get("event_id").toString();
        try {
          kafka
              .send(
                  "equiflow.events",
                  row.get("aggregate_key").toString(),
                  row.get("payload").toString())
              .get(8, TimeUnit.SECONDS);
          store.published(id);
        } catch (Exception ex) {
          store.failed(id, ex.toString());
          log.warn("Event {} remains in outbox: {}", id, ex.getClass().getSimpleName());
          break;
        }
      }
    }
  }
}
