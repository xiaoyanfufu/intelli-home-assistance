package com.intelli.home.adapter.in.mqtt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.home.domain.device.*;
import com.intelli.home.domain.event.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MqttDeviceEventParser {
  private final ObjectMapper mapper;

  public DeviceEvent parse(String topic, byte[] payload) {
    if (topic == null || payload == null || payload.length == 0 || payload.length > 16384)
      throw new IllegalArgumentException("Invalid MQTT payload");
    String[] parts = topic.split("/", -1);
    if (parts.length != 4 || !parts[0].equals("home") || !parts[3].equals("event"))
      throw new IllegalArgumentException("Invalid MQTT topic");
    try {
      Map<String, Object> properties =
          mapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
      Object rawTs = properties.remove("ts"),
          rawId = properties.remove("messageId"),
          sequence = properties.remove("seq");
      if (!(rawTs instanceof Number))
        throw new IllegalArgumentException("MQTT requires source timestamp ts");
      long ts = ((Number) rawTs).longValue();
      if (((Number) rawTs).doubleValue() != ts || ts < 0)
        throw new IllegalArgumentException("ts must be a non-negative integer");
      if (rawId != null
          && (!(rawId instanceof String idValue) || idValue.isBlank() || idValue.length() > 255))
        throw new IllegalArgumentException("Invalid messageId");
      if (sequence != null
          && (!(sequence instanceof Number seq)
              || seq.doubleValue() != seq.longValue()
              || seq.longValue() < 0)) throw new IllegalArgumentException("Invalid seq");
      if (rawId == null && sequence == null)
        throw new IllegalArgumentException("MQTT requires stable messageId or seq");
      String id =
          DeviceEvent.stableId(
              "mqtt", parts[2] + ":" + (rawId != null ? rawId : ts + ":" + sequence));
      var event =
          DeviceEvent.builder()
              .messageId(id)
              .deviceKey(parts[2])
              .source(DeviceSource.MQTT)
              .location(Location.valueOf(parts[1].toUpperCase(Locale.ROOT)))
              .eventType(EventType.TELEMETRY)
              .properties(properties)
              .occurredAt(ts)
              .receivedAt(System.currentTimeMillis())
              .build();
      event.validate();
      return event;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid MQTT JSON", e);
    }
  }
}
