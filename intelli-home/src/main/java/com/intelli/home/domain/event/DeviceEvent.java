package com.intelli.home.domain.event;

import com.intelli.home.domain.device.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceEvent {
  private String messageId;
  private String deviceKey;
  private DeviceSource source;
  private Location location;
  private EventType eventType;
  private Map<String, Object> properties;
  private Long occurredAt;
  private Long receivedAt;

  public static String newMessageId(String deviceKey, Long occurredAt) {
    return UUID.randomUUID().toString();
  }

  public static String stableId(String namespace, String identity) {
    return UUID.nameUUIDFromBytes((namespace + ":" + identity).getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  public void validate() {
    if (messageId == null
        || messageId.isBlank()
        || messageId.length() > 64
        || deviceKey == null
        || !deviceKey.matches("[a-zA-Z0-9_-]{1,64}")
        || source == null
        || location == null
        || eventType == null
        || occurredAt == null
        || occurredAt < 0
        || receivedAt == null
        || properties == null
        || occurredAt > receivedAt + 300_000)
      throw new IllegalArgumentException("Invalid device event");
    for (var entry : properties.entrySet()) {
      if (!entry.getKey().matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")
          || entry.getValue() == null
          || !(entry.getValue() instanceof String
              || entry.getValue() instanceof Number
              || entry.getValue() instanceof Boolean)
          || String.valueOf(entry.getValue()).length() > 255)
        throw new IllegalArgumentException("Invalid device property");
      if (entry.getValue() instanceof Number number && !Double.isFinite(number.doubleValue()))
        throw new IllegalArgumentException("Non-finite device property");
    }
  }
}
