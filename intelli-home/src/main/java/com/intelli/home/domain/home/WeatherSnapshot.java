package com.intelli.home.domain.home;

public record WeatherSnapshot(
    String location,
    String source,
    long observedAt,
    long validUntil,
    boolean available,
    Boolean raining,
    Double rainProbability,
    String reason) {
  public boolean fresh(long now, long maxAge) {
    return available
        && observedAt <= now + 300000
        && observedAt >= now - maxAge
        && validUntil > now;
  }
}
