package edu.campus.equiflow;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "desk")
public record DeskProperties(String timezone, boolean seedDemo, Booking booking) {
  public record Booking(int maxDays, int advanceDays) {}
}
