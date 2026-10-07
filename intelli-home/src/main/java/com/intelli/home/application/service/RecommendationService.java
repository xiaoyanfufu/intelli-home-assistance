package com.intelli.home.application.service;

import com.intelli.home.application.model.*;
import com.intelli.home.application.port.in.RecommendationUseCase;
import com.intelli.home.application.port.out.*;
import com.intelli.home.domain.alert.AlertEvent;
import com.intelli.home.domain.device.*;
import com.intelli.home.domain.event.*;
import com.intelli.home.domain.rule.RuleEngine;
import com.intelli.home.domain.rule.RuleEvaluationInput;
import java.util.*;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService implements RecommendationUseCase {
  private final DeviceStateStore stateStore;
  private final AgentGateway gateway;
  private final RuleEngine rules;
  private final RuleConfigService config;
  private final Semaphore permits = new Semaphore(4);

  public RecommendationResult recommend(String deviceKey) {
    if (!permits.tryAcquire()) return fallback("建议请求繁忙，请稍后重试。", "UNKNOWN", "BUSY");
    try {
      var snapshots = stateStore.snapshots();
      long now = System.currentTimeMillis();
      if (!snapshots.containsKey(deviceKey)) throw new IllegalArgumentException("Unknown device");
      var byLocation = new HashMap<String, Map<String, Object>>();
      var safety = new ArrayList<AlertEvent>();
      var definitions = config.snapshot();
      // Stable policy: freshest receiver time per location; lexical device key breaks ties.
      snapshots.values().stream()
          .sorted(
              Comparator.comparingLong(DeviceSnapshot::lastSeenAt)
                  .thenComparing(DeviceSnapshot::deviceKey))
          .forEach(
              snapshot -> {
                var fresh = snapshot.freshProperties(now, 300000);
                if (fresh.isEmpty()) return;
                byLocation.put(snapshot.location().name(), fresh);
                var event =
                    DeviceEvent.builder()
                        .messageId(
                            DeviceEvent.stableId(
                                "recommend", snapshot.deviceKey() + ":" + snapshot.occurredAt()))
                        .deviceKey(snapshot.deviceKey())
                        .source(DeviceSource.MOCK)
                        .location(snapshot.location())
                        .eventType(EventType.TELEMETRY)
                        .occurredAt(snapshot.occurredAt())
                        .receivedAt(now)
                        .properties(fresh)
                        .build();
                safety.addAll(rules.evaluate(new RuleEvaluationInput(event, fresh, definitions)));
              });
      var selected = snapshots.get(deviceKey);
      var properties = selected.freshProperties(now, 300000);
      if (byLocation.isEmpty()) return fallback("设备数据缺失或已过期，请检查设备连接。", "UNKNOWN", "STALE_DATA");
      if (!safety.isEmpty())
        return new RecommendationResult(
            safety.getFirst().getContent(), "FIRE", Map.of("safetyAlerts", safety), false, null);
      if (properties.isEmpty()) return fallback("请求设备的数据缺失或已过期，请检查设备连接。", "UNKNOWN", "STALE_DATA");
      var request =
          new RecommendationRequest(
              1,
              UUID.randomUUID().toString(),
              deviceKey,
              selected.location().name(),
              properties,
              Map.copyOf(byLocation),
              List.copyOf(safety));
      try {
        return gateway.recommend(request);
      } catch (Exception e) {
        log.warn("Agent unavailable requestId={}", request.requestId(), e);
        return fallback("智能建议暂不可用，可查看设备状态与规则告警。", "UNKNOWN", "AGENT_UNAVAILABLE");
      }
    } finally {
      permits.release();
    }
  }

  private RecommendationResult fallback(String text, String scene, String reason) {
    return new RecommendationResult(text, scene, Map.of(), true, reason);
  }
}
