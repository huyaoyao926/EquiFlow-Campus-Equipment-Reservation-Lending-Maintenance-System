package edu.campus.equiflow.cluster;

import edu.campus.equiflow.web.ApiException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
@ConditionalOnProperty(name = "cluster.enabled", havingValue = "true")
public class ClusterClient {
  private final RestTemplate rest;
  private final String key;
  private final CircuitBreakerRegistry breakers;

  public ClusterClient(
      @Qualifier("serviceRestTemplate") RestTemplate rest,
      @Value("${cluster.service-key}") String key,
      CircuitBreakerRegistry breakers) {
    this.rest = rest;
    this.key = key;
    this.breakers = breakers;
  }

  public <T> T call(String service, String path, HttpMethod method, Object body, Class<T> type) {
    try {
      return breakers
          .circuitBreaker(service)
          .executeSupplier(
              () -> {
                var headers = new HttpHeaders();
                headers.set("X-Service-Key", key);
                headers.setContentType(MediaType.APPLICATION_JSON);
                return rest.exchange(
                        java.net.URI.create("http://" + service + path),
                        method,
                        new HttpEntity<>(body, headers),
                        type)
                    .getBody();
              });
    } catch (HttpClientErrorException ex) {
      throw new ApiException(
          HttpStatus.valueOf(ex.getStatusCode().value()),
          ex.getStatusCode().value() == 404
              ? "Record not found."
              : ex.getStatusCode().value() == 409
                  ? "The record conflicts with an existing record. Refresh and try again."
                  : "The request was rejected by the service.");
    } catch (Exception ex) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          service + " is temporarily unavailable. Your saved records are safe. Try again shortly.");
    }
  }

  public Map credentials(String email) {
    return call(
        "EquiFlow-identity-service",
        "/internal/accounts?email="
            + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8),
        HttpMethod.GET,
        null,
        Map.class);
  }
}

@Configuration
class ClientConfiguration {
  @Bean("serviceRestTemplate")
  @LoadBalanced
  RestTemplate serviceRestTemplate(
      org.springframework.boot.restclient.RestTemplateBuilder builder) {
    var http = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofSeconds(4));
    return builder.requestFactory(() -> factory).build();
  }
}
