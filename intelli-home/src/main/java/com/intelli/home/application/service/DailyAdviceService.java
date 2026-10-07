package com.intelli.home.application.service;

import com.intelli.home.application.model.*;
import com.intelli.home.application.port.in.AlertNotificationHandler;
import com.intelli.home.application.port.out.*;
import com.intelli.home.config.IntelliProperties;
import com.intelli.home.domain.alert.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DailyAdviceService {
  private final IntelliProperties.DailyAdvice settings;
  private final IntelliProperties.Notification notification;
  private final DeviceStateStore states;
  private final DeviceCatalog catalog;
  private final AlertRepository alerts;
  private final NotificationReceiptStore receipts;
  private final AgentGateway agent;
  private final AlertNotificationHandler delivery;
  private final DailyAdviceHistorySummarizer history;
  private final Clock clock;
  private final ReentrantLock running = new ReentrantLock();

  public DailyAdviceService(
      IntelliProperties.DailyAdvice settings,
      IntelliProperties.Notification notification,
      DeviceStateStore states,
      DeviceCatalog catalog,
      AlertRepository alerts,
      NotificationReceiptStore receipts,
      AgentGateway agent,
      AlertNotificationHandler delivery,
      DailyAdviceHistorySummarizer history,
      Clock dailyAdviceClock) {
    this.settings = settings;
    this.notification = notification;
    this.states = states;
    this.catalog = catalog;
    this.alerts = alerts;
    this.receipts = receipts;
    this.agent = agent;
    this.delivery = delivery;
    this.history = history;
    this.clock = dailyAdviceClock;
    ZoneId.of(settings.getZone());
    if (settings.getHistoryHours() < 1
        || settings.getHistoryHours() > 720
        || settings.getMaxDevices() < 1
        || settings.getMaxDevices() > 100
        || settings.getMaxSamplesPerDevice() < 1
        || settings.getMaxSamplesPerDevice() > 100000)
      throw new IllegalArgumentException("Invalid daily advice aggregation limits");
  }

  public Map<String, Object> run(boolean dryRun) {
    if (!running.tryLock()) return Map.of("status", "SKIPPED_RUNNING");
    String id = "daily-advice:" + LocalDate.now(clock.withZone(ZoneId.of(settings.getZone())));
    try {
      if (!dryRun && receipts.exists(id))
        return Map.of("status", "SKIPPED_ALREADY_SENT", "requestId", id);
      // Do not consume today's receipt via the log channel or a disabled/filtered email channel.
      var email = notification.getEmail();
      if (!dryRun
          && (!"email".equals(notification.getChannel())
              || !email.isEnabled()
              || (email.getMinLevel() != AlertLevel.INFO
                  && !email.getMinLevelExemptRuleCodes().contains("DAILY_DIGEST"))))
        return Map.of("status", "DELIVERY_NOT_CONFIGURED", "requestId", id);
      long to = clock.millis(), from = to - Duration.ofHours(settings.getHistoryHours()).toMillis();
      var devices =
          catalog.devices().stream().sorted(Comparator.comparing(d -> d.deviceKey())).toList();
      var summaries = new ArrayList<Map<String, Object>>();
      devices.stream()
          .limit(settings.getMaxDevices())
          .forEach(
              device ->
                  summaries.add(
                      history.summarize(device, from, to, settings.getMaxSamplesPerDevice())));
      // Pick the freshest device per location; do not silently combine different devices'
      // properties.
      var selected = new HashMap<String, com.intelli.home.domain.device.DeviceSnapshot>();
      states.snapshots().values().stream()
          .sorted(Comparator.comparing(com.intelli.home.domain.device.DeviceSnapshot::deviceKey))
          .forEach(
              snapshot -> {
                if (snapshot.freshProperties(to, 300000).isEmpty()) return;
                selected.merge(
                    snapshot.location().name(),
                    snapshot,
                    (a, b) -> a.occurredAt() >= b.occurredAt() ? a : b);
              });
      var current = new TreeMap<String, Map<String, Object>>();
      selected.forEach(
          (location, snapshot) -> current.put(location, snapshot.freshProperties(to, 300000)));
      var recent = alerts.recent(100);
      var items =
          recent.stream().filter(a -> a.getOccurredAt() >= from && a.getOccurredAt() < to).toList();
      var levels = new TreeMap<String, Long>();
      items.forEach(a -> levels.merge(a.getLevel().name(), 1L, Long::sum));
      var alertSummary =
          Map.<String, Object>of(
              "total",
              items.size(),
              "byLevel",
              levels,
              "items",
              items,
              "truncated",
              recent.size() >= 100);
      var request =
          new DailyAdviceRequest(
              1,
              id,
              id.substring("daily-advice:".length()),
              settings.getHistoryHours(),
              current,
              summaries,
              alertSummary,
              Map.of(
                  "devicesTruncated",
                  devices.size() > settings.getMaxDevices(),
                  "totalDevices",
                  devices.size(),
                  "includedDevices",
                  summaries.size()));
      var result = agent.dailyAdvice(request);
      if (result == null
          || result.advice() == null
          || result.advice().isBlank()
          || result.advice().length() > 20000)
        throw new IllegalStateException("Invalid daily advice response");
      if (dryRun)
        return Map.of("status", "DRY_RUN", "requestId", id, "result", result, "context", request);
      String content =
          result.degraded()
              ? "降级模式：LLM 不可用，以下为基于真实数据的模板建议。\n\n" + result.advice()
              : result.advice();
      delivery.handle(
          AlertEvent.builder()
              .messageId(id)
              .deviceKey("home")
              .ruleCode("DAILY_DIGEST")
              .scene("DAILY_ADVICE")
              .level(AlertLevel.INFO)
              .title(settings.getSubjectPrefix() + " " + request.date())
              .content(content)
              .occurredAt(to)
              .build());
      return Map.of("status", "SENT", "requestId", id, "result", result);
    } catch (Exception e) {
      // Never log external response bodies, SMTP credentials or arbitrary exception messages.
      log.warn("Daily advice failed requestId={} failure={}", id, e.getClass().getSimpleName());
      return Map.of("status", "FAILED", "requestId", id, "reason", e.getClass().getSimpleName());
    } finally {
      running.unlock();
    }
  }
}
