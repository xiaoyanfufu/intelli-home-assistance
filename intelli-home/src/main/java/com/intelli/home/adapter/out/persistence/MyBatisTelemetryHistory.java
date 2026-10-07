package com.intelli.home.adapter.out.persistence;

import static com.intelli.home.adapter.out.persistence.JsonRows.*;

import com.intelli.home.adapter.out.persistence.mapper.HomeDataMapper;
import com.intelli.home.application.port.out.TelemetryHistory;
import com.intelli.home.domain.home.TelemetrySample;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisTelemetryHistory implements TelemetryHistory {
  private final HomeDataMapper mapper;
  private final JsonRows json;

  public void append(TelemetrySample sample) {
    mapper.insertTelemetry(sample, json.write(sample.properties()));
  }

  public List<TelemetrySample> query(
      String key, long from, long to, long afterTime, String afterId, int limit) {
    return mapper.telemetry(key, from, to, afterTime, afterId, limit).stream()
        .map(
            r ->
                new TelemetrySample(
                    text(r, "event_id"),
                    text(r, "device_key"),
                    text(r, "model_key"),
                    (int) number(r, "model_version"),
                    number(r, "occurred_at"),
                    number(r, "received_at"),
                    json.map(r.get("properties_json"))))
        .toList();
  }

  public int cleanup(long before, int limit) {
    return mapper.cleanupTelemetry(before, limit);
  }
}
