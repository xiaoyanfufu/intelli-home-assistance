package com.intelli.home.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonRows {
  private final ObjectMapper json;

  public String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot serialize value", e);
    }
  }

  public <T> T read(String value, Class<T> type) {
    try {
      return value == null ? null : json.readValue(value, type);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid stored JSON", e);
    }
  }

  public Map<String, Object> map(Object value) {
    try {
      return json.readValue(value.toString(), new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Invalid stored JSON", e);
    }
  }

  public static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  public static String text(Map<String, Object> row, String key) {
    var value = row.get(key);
    return value == null ? null : value.toString();
  }
}
