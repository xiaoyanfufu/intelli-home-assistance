package com.intelli.home.domain.device;

import java.util.Map;

public record DeviceSnapshot(
    String deviceKey,
    String eventId,
    Location location,
    long occurredAt,
    long lastSeenAt,
    Map<String, Object> properties,
    Map<String, Long> propertyTimes) {
  public DeviceSnapshot {
    properties = Map.copyOf(properties);
    propertyTimes = Map.copyOf(propertyTimes);
  }

  public Map<String, Object> freshProperties(long now, long maxAge) {
    var fresh = new java.util.HashMap<String, Object>();
    if (now - lastSeenAt > maxAge) return Map.of();
    properties.forEach(
        (key, value) -> {
          if (now - propertyTimes.getOrDefault(key, 0L) <= maxAge) fresh.put(key, value);
        });
    return Map.copyOf(fresh);
  }
}
