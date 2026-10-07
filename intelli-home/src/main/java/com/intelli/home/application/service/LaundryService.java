package com.intelli.home.application.service;

import com.intelli.home.application.port.out.*;
import com.intelli.home.domain.alert.AlertEvent;
import com.intelli.home.domain.event.DeviceEvent;
import com.intelli.home.domain.home.*;
import com.intelli.home.domain.rule.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LaundryService {
  private final LaundryRepository repository;
  private final DeviceCatalogService devices;
  private final RuleConfigService config;
  private final AlertRepository alerts;
  private final ChangeFeed changes;
  private final LaundryRule rule = new LaundryRule();

  @Transactional
  public LaundrySession start(String deviceKey) {
    var device = devices.require(deviceKey);
    if (!"BALCONY".equals(device.location()))
      throw new IllegalArgumentException("Laundry requires a balcony device");
    var session =
        new LaundrySession(
            UUID.randomUUID().toString(),
            deviceKey,
            "ACTIVE",
            System.currentTimeMillis(),
            null,
            false,
            0,
            1);
    try {
      repository.create(session);
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw new VersionConflictException("Device already has an active laundry session");
    }
    return session;
  }

  public List<LaundrySession> list() {
    return repository.list();
  }

  @Transactional
  public LaundrySession complete(String id) {
    var old = repository.lock(id);
    if (old == null) throw new IllegalArgumentException("Unknown laundry session");
    if (old.status().equals("COMPLETED")) return old;
    var next =
        new LaundrySession(
            id,
            old.deviceKey(),
            "COMPLETED",
            old.startedAt(),
            System.currentTimeMillis(),
            old.riskActive(),
            old.riskCycle(),
            old.version() + 1);
    if (!repository.update(next, old.version()))
      throw new VersionConflictException("Laundry changed");
    return next;
  }

  public WeatherSnapshot weather() {
    return repository.weather();
  }

  @Transactional
  public void saveWeather(WeatherSnapshot weather) {
    long now = System.currentTimeMillis();
    if (weather == null
        || weather.location() == null
        || weather.source() == null
        || weather.observedAt() < 0
        || weather.observedAt() > now + 300000
        || weather.validUntil() < weather.observedAt()
        || weather.validUntil() - weather.observedAt() > 3600000
        || (weather.rainProbability() != null
            && (!Double.isFinite(weather.rainProbability())
                || weather.rainProbability() < 0
                || weather.rainProbability() > 1)))
      throw new IllegalArgumentException("Invalid weather");
    repository.saveWeather(weather);
  }

  @Transactional
  public void evaluate() {
    var definition = config.snapshot().get("COLLECT_LAUNDRY");
    if (definition == null || !definition.enabled()) return;
    var weather = repository.weather();
    long now = System.currentTimeMillis();
    for (var old : repository.lockActive()) {
      var verdict = rule.evaluate(true, weather, now, definition);
      if (verdict.status() == RuleResult.Status.INSUFFICIENT_DATA) continue;
      boolean risk = verdict.status() == RuleResult.Status.MATCHED;
      if (risk == old.riskActive()) continue;
      int cycle = old.riskCycle() + (risk ? 1 : 0);
      var next =
          new LaundrySession(
              old.id(),
              old.deviceKey(),
              old.status(),
              old.startedAt(),
              old.completedAt(),
              risk,
              cycle,
              old.version() + 1);
      if (!repository.update(next, old.version()))
        throw new VersionConflictException("Laundry changed");
      if (risk) {
        var alert =
            AlertEvent.builder()
                .messageId(DeviceEvent.stableId("laundry", old.id() + ":" + cycle))
                .deviceKey(old.deviceKey())
                .ruleCode("COLLECT_LAUNDRY")
                .scene("LAUNDRY")
                .level(verdict.level())
                .title(verdict.title())
                .content(verdict.content())
                .occurredAt(now)
                .build();
        alerts.record(List.of(alert), 0);
      }
      changes.append(
          new ChangeEvent(
              0,
              "laundry:" + old.id() + ":" + next.version(),
              "laundry.changed",
              old.deviceKey(),
              now,
              next.version(),
              Map.of("session", next)));
    }
  }

  public RuleResult trial(boolean active, WeatherSnapshot weather, RuleDefinition definition) {
    return rule.evaluate(active, weather, System.currentTimeMillis(), definition);
  }
}
