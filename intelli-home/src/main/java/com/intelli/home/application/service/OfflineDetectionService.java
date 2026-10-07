package com.intelli.home.application.service;

import com.intelli.home.application.port.in.DeviceEventHandler;
import com.intelli.home.application.port.out.DeviceRegistry;
import com.intelli.home.domain.device.DeviceSource;
import com.intelli.home.domain.event.*;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfflineDetectionService {
  private final DeviceRegistry registry;
  private final RuleConfigService config;
  private final DeviceEventHandler handler;

  @Scheduled(fixedDelay = 30000, initialDelay = 30000)
  public void scan() {
    var definition = config.snapshot().get("DEVICE_OFFLINE");
    if (!definition.enabled()) return;
    long now = System.currentTimeMillis();
    long threshold = (long) (definition.params().getOrDefault("offlineSeconds", 300.0) * 1000);
    try {
      for (var device : registry.snapshots())
        if (now - device.lastSeenAt() > threshold) {
          handler.handle(
              DeviceEvent.builder()
                  .messageId(
                      DeviceEvent.stableId(
                          "offline", device.deviceKey() + ":" + device.lastSeenAt()))
                  .deviceKey(device.deviceKey())
                  .source(DeviceSource.MOCK)
                  .location(device.location())
                  .eventType(EventType.STATUS)
                  .occurredAt(device.lastSeenAt() + threshold)
                  .receivedAt(now)
                  .properties(Map.of("online", false))
                  .build());
        }
    } catch (Exception e) {
      log.error("Offline scan failed", e);
    }
  }
}
