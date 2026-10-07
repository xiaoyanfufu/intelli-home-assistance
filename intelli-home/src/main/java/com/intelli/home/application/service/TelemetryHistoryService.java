package com.intelli.home.application.service;

import com.intelli.home.application.port.out.TelemetryHistory;
import com.intelli.home.domain.home.TelemetrySample;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TelemetryHistoryService {
  private final TelemetryHistory history;
  private final DeviceCatalogService catalog;

  public record Page(List<TelemetrySample> items, String nextCursor) {}

  public record Bucket(long time, double avg, double min, double max, int count) {}

  public Page query(String key, long from, long to, String cursor, int limit) {
    catalog.require(key);
    if (from < 0 || to <= from || to - from > 31L * 86400000 || limit < 1 || limit > 500)
      throw new IllegalArgumentException("Invalid history range or limit");
    long afterTime = -1;
    String afterId = "";
    if (cursor != null && !cursor.isBlank()) {
      try {
        var parts =
            new String(
                    Base64.getUrlDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8)
                .split(":", 2);
        afterTime = Long.parseLong(parts[0]);
        afterId = parts[1];
        if (afterTime < from || afterTime >= to) throw new IllegalArgumentException();
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid cursor");
      }
    }
    var items = history.query(key, from, to, afterTime, afterId, limit + 1);
    boolean more = items.size() > limit;
    var page = more ? List.copyOf(items.subList(0, limit)) : items;
    String next = null;
    if (more) {
      var last = page.getLast();
      next =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(
                  (last.occurredAt() + ":" + last.eventId())
                      .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    return new Page(page, next);
  }

  public Object trend(
      String key, String property, long from, long to, String interval, String cursor, int limit) {
    var spec = catalog.capabilities(key).properties().get(property);
    if (spec == null || !spec.queryable())
      throw new IllegalArgumentException("Property is not queryable");
    if ("raw".equals(interval)) {
      var page = query(key, from, to, cursor, limit);
      var points =
          page.items().stream()
              .filter(s -> s.properties().containsKey(property))
              .map(
                  s ->
                      Map.of(
                          "time",
                          s.occurredAt(),
                          "eventId",
                          s.eventId(),
                          "value",
                          s.properties().get(property)))
              .toList();
      return Map.of(
          "points", points, "nextCursor", page.nextCursor() == null ? "" : page.nextCursor());
    }
    if (!"number".equals(spec.type()))
      throw new IllegalArgumentException("Only numbers can be aggregated");
    long width =
        switch (interval) {
          case "minute" -> 60000;
          case "hour" -> 3600000;
          default -> throw new IllegalArgumentException("Invalid interval");
        };
    query(key, from, to, null, 1);
    var samples = history.query(key, from, to, -1, "", 10001);
    if (samples.size() > 10000)
      throw new IllegalArgumentException("Too many samples; narrow the time range");
    var stats = new TreeMap<Long, DoubleSummaryStatistics>();
    samples.forEach(
        s -> {
          Object value = s.properties().get(property);
          if (value instanceof Number n)
            stats
                .computeIfAbsent(
                    Math.floorDiv(s.occurredAt(), width) * width,
                    k -> new DoubleSummaryStatistics())
                .accept(n.doubleValue());
        });
    if (stats.size() > 2000) throw new IllegalArgumentException("Too many buckets");
    return stats.entrySet().stream()
        .map(
            e ->
                new Bucket(
                    e.getKey(),
                    e.getValue().getAverage(),
                    e.getValue().getMin(),
                    e.getValue().getMax(),
                    (int) e.getValue().getCount()))
        .toList();
  }
}
