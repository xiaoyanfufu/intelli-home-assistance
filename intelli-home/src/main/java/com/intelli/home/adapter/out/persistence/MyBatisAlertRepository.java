package com.intelli.home.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.home.adapter.out.messaging.AlertEventMessage;
import com.intelli.home.adapter.out.persistence.mapper.AlertMapper;
import com.intelli.home.adapter.out.persistence.mapper.OutboxMapper;
import com.intelli.home.application.port.out.AlertRepository;
import com.intelli.home.application.port.out.ChangeFeed;
import com.intelli.home.domain.alert.AlertEvent;
import com.intelli.home.domain.home.ChangeEvent;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MyBatisAlertRepository implements AlertRepository {
  private final AlertMapper alerts;
  private final OutboxMapper outbox;
  private final ObjectMapper json;
  private final ChangeFeed changes;

  @Transactional
  public void record(List<AlertEvent> events, long cooldownMillis) {
    for (var alert : events) {
      alerts.initializeCooldown(alert.getDeviceKey(), alert.getRuleCode());
      Long previous = alerts.lockCooldown(alert.getDeviceKey(), alert.getRuleCode());
      if (alerts.exists(alert.getMessageId())) continue;
      if (previous != null && previous > 0 && alert.getOccurredAt() - previous < cooldownMillis)
        continue;
      alerts.insert(alert);
      String payload;
      try {
        payload = json.writeValueAsString(AlertEventMessage.of(alert, "intelli-home"));
      } catch (Exception e) {
        throw new IllegalStateException("Cannot serialize alert", e);
      }
      outbox.insert(alert.getMessageId(), payload);
      changes.append(
          new ChangeEvent(
              0,
              "alert:" + alert.getMessageId(),
              "alert.created",
              alert.getDeviceKey(),
              System.currentTimeMillis(),
              alert.getOccurredAt(),
              Map.of("alert", alert)));
      alerts.updateCooldown(alert.getDeviceKey(), alert.getRuleCode(), alert.getOccurredAt());
    }
  }

  public List<AlertEvent> recent(int limit) {
    return alerts.findRecent(Math.max(1, Math.min(limit, 100)));
  }
}
