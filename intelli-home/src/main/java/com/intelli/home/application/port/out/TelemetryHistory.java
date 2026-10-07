package com.intelli.home.application.port.out;

import com.intelli.home.domain.home.TelemetrySample;
import java.util.List;

public interface TelemetryHistory {
  void append(TelemetrySample sample);

  List<TelemetrySample> query(
      String deviceKey, long from, long to, long afterTime, String afterId, int limit);

  int cleanup(long before, int limit);
}
