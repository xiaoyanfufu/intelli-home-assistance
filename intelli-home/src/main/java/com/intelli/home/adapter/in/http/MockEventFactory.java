package com.intelli.home.adapter.in.http;

import com.intelli.home.domain.device.*;
import com.intelli.home.domain.event.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MockEventFactory {
  public DeviceEvent telemetry(String key, Location location, Map<String, Object> properties) {
    long now = System.currentTimeMillis();
    return DeviceEvent.builder()
        .messageId(DeviceEvent.newMessageId(key, now))
        .deviceKey(key)
        .source(DeviceSource.MOCK)
        .location(location)
        .eventType(EventType.TELEMETRY)
        .properties(Map.copyOf(properties))
        .occurredAt(now)
        .receivedAt(now)
        .build();
  }

  public List<DeviceEvent> scene(String scene) {
    if (!Set.of("FIRE", "NORMAL", "DRYING").contains(scene))
      throw new IllegalArgumentException("Unknown scene");
    return List.of(
        telemetry(
            "indoor-node-01",
            Location.INDOOR,
            Map.of(
                "temperature",
                scene.equals("FIRE") ? 46.2 : 24.0,
                "humidity",
                55,
                "smoke",
                scene.equals("FIRE") ? 320 : 80)),
        telemetry(
            "balcony-node-01",
            Location.BALCONY,
            Map.of("temperature", 25.5, "humidity", scene.equals("DRYING") ? 42 : 60)));
  }
}
