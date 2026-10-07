package com.intelli.home.application.service;

import com.intelli.home.application.port.in.DeviceEventHandler;
import com.intelli.home.application.port.out.*;
import com.intelli.home.domain.event.*;
import com.intelli.home.domain.home.TelemetrySample;
import com.intelli.home.domain.rule.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceEventDispatcher implements DeviceEventHandler {
  private static final long PROPERTY_MAX_AGE_MILLIS = 300_000;
  private static final long ALERT_COOLDOWN_MILLIS = 60_000;
  private final DeviceStateStore states;
  private final DeviceRegistry registry;
  private final RuleEngine rules;
  private final RuleConfigService config;
  private final AlertRepository alerts;
  private final DeviceCatalogService catalog;
  private final TelemetryHistory history;
  private final LaundryService laundry;

  public void handle(DeviceEvent event) {
    event.validate();
    var definitions = config.snapshot();
    switch (event.getEventType()) {
      case TELEMETRY -> handleTelemetry(event, definitions);
      case STATUS -> handleEventProperties(event, definitions);
      case COMMAND_REPLY -> throw new IllegalArgumentException("Use the command reply contract");
    }
  }

  private void handleTelemetry(DeviceEvent event, Map<String, RuleDefinition> definitions) {
    var device = catalog.normalize(event);
    history.append(
        new TelemetrySample(
            event.getMessageId(),
            event.getDeviceKey(),
            device.modelKey(),
            device.modelVersion(),
            event.getOccurredAt(),
            event.getReceivedAt(),
            event.getProperties()));
    if (!states.update(event)) return;
    registry.record(event);
    evaluateAndRecord(event, freshProperties(event.getDeviceKey()), definitions);
    laundry.evaluate();
  }

  private Map<String, Object> freshProperties(String deviceKey) {
    // Rules use only this device's fresh snapshot, including previously reported attributes.
    var snapshot = states.snapshots().get(deviceKey);
    return snapshot == null
        ? Map.of()
        : snapshot.freshProperties(System.currentTimeMillis(), PROPERTY_MAX_AGE_MILLIS);
  }

  private void handleEventProperties(DeviceEvent event, Map<String, RuleDefinition> definitions) {
    // Status events use their own properties; command replies have a separate handler.
    evaluateAndRecord(event, new HashMap<>(event.getProperties()), definitions);
  }

  private void evaluateAndRecord(
      DeviceEvent sourceEvent,
      Map<String, Object> effectiveProperties,
      Map<String, RuleDefinition> definitions) {
    var input = new RuleEvaluationInput(sourceEvent, effectiveProperties, definitions);
    var matchedAlerts = rules.evaluate(input);
    if (!matchedAlerts.isEmpty()) {
      alerts.record(matchedAlerts, ALERT_COOLDOWN_MILLIS);
    }
  }
}
