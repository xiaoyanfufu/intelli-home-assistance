package com.intelli.home.application.service;

import com.intelli.home.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HomeRetentionService {
  private final TelemetryHistory telemetry;
  private final ChangeFeed changes;

  @Value("${intelli.retention.telemetry-days:30}")
  private long telemetryDays;

  @Value("${intelli.retention.events-hours:1}")
  private long eventHours;

  @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
  public void cleanup() {
    long now = System.currentTimeMillis();
    telemetry.cleanup(now - Math.max(1, Math.min(3650, telemetryDays)) * 86400000, 1000);
    changes.cleanup(now - Math.max(1, Math.min(168, eventHours)) * 3600000, 1000);
  }
}
