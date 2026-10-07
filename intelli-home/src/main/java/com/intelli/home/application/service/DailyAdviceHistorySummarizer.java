package com.intelli.home.application.service;

import com.intelli.home.application.port.out.TelemetryHistory;
import com.intelli.home.domain.home.HomeDevice;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DailyAdviceHistorySummarizer {
  private final TelemetryHistory history;

  public Map<String, Object> summarize(HomeDevice device, long from, long to, int maxSamples) {
    if (maxSamples < 1) throw new IllegalArgumentException("Invalid sample cap");
    var stats = new TreeMap<String, Stats>();
    long afterTime = from - 1, first = -1, last = -1;
    String afterId = "";
    int count = 0;
    boolean truncated = false;
    // Every nonempty page must advance the composite cursor; cap also bounds page count.
    while (true) {
      int limit = Math.min(500, maxSamples - count + 1);
      var page = history.query(device.deviceKey(), from, to, afterTime, afterId, limit);
      if (page.isEmpty()) break;
      for (var sample : page) {
        if (sample.occurredAt() < from
            || sample.occurredAt() >= to
            || sample.occurredAt() < afterTime
            || (sample.occurredAt() == afterTime && sample.eventId().compareTo(afterId) <= 0))
          throw new IllegalStateException("History cursor did not advance");
        if (count == maxSamples) {
          truncated = true;
          break;
        }
        if (first < 0) first = sample.occurredAt();
        last = sample.occurredAt();
        count++;
        sample
            .properties()
            .forEach(
                (key, value) -> {
                  Double number = number(value);
                  if (number != null) stats.computeIfAbsent(key, k -> new Stats()).add(number);
                });
        afterTime = sample.occurredAt();
        afterId = sample.eventId();
      }
      if (truncated || page.size() < limit) break;
    }
    var properties = new TreeMap<String, Object>();
    stats.forEach((key, value) -> properties.put(key, value.result()));
    var summary = new LinkedHashMap<String, Object>();
    summary.put("deviceKey", device.deviceKey());
    summary.put("location", device.location());
    summary.put("from", from);
    summary.put("to", to);
    summary.put("sampleCount", count);
    summary.put("truncated", truncated);
    summary.put("coveredFrom", first < 0 ? null : first);
    summary.put("coveredTo", last < 0 ? null : last);
    summary.put("properties", properties);
    return summary;
  }

  private Double number(Object value) {
    if (!(value instanceof Number) && !(value instanceof String)) return null;
    try {
      double number = Double.parseDouble(value.toString());
      return Double.isFinite(number) ? number : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static class Stats {
    double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY, mean, last;
    int count;

    void add(double value) {
      min = Math.min(min, value);
      max = Math.max(max, value);
      last = value;
      count++;
      mean = mean * ((count - 1.0) / count) + value / count;
    }

    Map<String, Object> result() {
      return Map.of("min", min, "max", max, "avg", mean, "last", last, "count", count);
    }
  }
}
