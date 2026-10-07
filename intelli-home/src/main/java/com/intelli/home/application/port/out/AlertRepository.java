package com.intelli.home.application.port.out;

import com.intelli.home.domain.alert.AlertEvent;
import java.util.List;

public interface AlertRepository {
  void record(List<AlertEvent> alerts, long cooldownMillis);

  List<AlertEvent> recent(int limit);
}
