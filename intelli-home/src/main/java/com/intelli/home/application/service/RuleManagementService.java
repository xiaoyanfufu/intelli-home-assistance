package com.intelli.home.application.service;

import com.intelli.home.application.port.out.RuleManagementRepository;
import com.intelli.home.domain.event.DeviceEvent;
import com.intelli.home.domain.home.ManagedRule;
import com.intelli.home.domain.rule.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RuleManagementService {
  private final RuleManagementRepository repository;
  private final RuleConfigService config;
  private final List<Rule> implementations;

  public record Parameter(String unit, double minimum, double maximum) {}

  public Map<String, Parameter> parameters(String code) {
    return switch (code) {
      case "FIRE_RISK" ->
          Map.of(
              "temperature",
              new Parameter("℃", 0, 150),
              "smoke",
              new Parameter("un-calibrated", 0, 100000));
      case "DEVICE_OFFLINE" -> Map.of("offlineSeconds", new Parameter("s", 10, 86400));
      case "COLLECT_LAUNDRY" ->
          Map.of(
              "rainProbability",
              new Parameter("ratio", 0, 1),
              "weatherMaxAgeSeconds",
              new Parameter("s", 30, 3600));
      default -> throw new IllegalArgumentException("Unsupported rule");
    };
  }

  public ManagedRule require(String code) {
    parameters(code);
    var rule = repository.find(code);
    if (rule == null) throw new IllegalArgumentException("Unknown rule");
    return rule;
  }

  public List<Object> list() {
    return List.of(describe("FIRE_RISK"), describe("DEVICE_OFFLINE"), describe("COLLECT_LAUNDRY"));
  }

  public Map<String, Object> describe(String code) {
    var saved = require(code);
    boolean active = saved.definition().equals(config.snapshot().get(code));
    return Map.of(
        "saved",
        saved,
        "parameters",
        parameters(code),
        "active",
        active,
        "activeRevision",
        active ? saved.revision() : -1L,
        "configGeneration",
        config.current().generation());
  }

  public void validate(String code, RuleDefinition definition) {
    var schema = parameters(code);
    if (definition == null
        || !code.equals(definition.code())
        || definition.priority() < 0
        || definition.priority() > 10000
        || !definition.params().keySet().equals(schema.keySet()))
      throw new IllegalArgumentException("Invalid rule configuration");
    definition
        .params()
        .forEach(
            (key, value) -> {
              var range = schema.get(key);
              if (value == null
                  || !Double.isFinite(value)
                  || value < range.minimum()
                  || value > range.maximum())
                throw new IllegalArgumentException("Invalid threshold: " + key);
            });
  }

  @Transactional
  public ManagedRule update(String code, long expected, RuleDefinition definition) {
    validate(code, definition);
    var old = require(code);
    if (old.revision() != expected) throw new VersionConflictException("Rule revision conflict");
    repository.recordRevision(old);
    var next = new ManagedRule(expected + 1, definition);
    if (!repository.update(code, expected, next))
      throw new VersionConflictException("Rule revision conflict");
    repository.recordRevision(next);
    return next;
  }

  public Object trial(
      String code,
      DeviceEvent event,
      Map<String, Object> effectiveProperties,
      RuleDefinition definition) {
    validate(code, definition);
    event.validate();
    var rule =
        implementations.stream()
            .filter(r -> r.code().equals(code))
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException("Use household context for laundry trial"));
    var context = new RuleContext(event, Map.copyOf(effectiveProperties), definition.params());
    var verdict = rule.supports(context) ? rule.evaluate(context) : RuleResult.notMatched();
    return Map.of(
        "verdict",
        verdict,
        "ruleCode",
        code,
        "parameters",
        definition.params(),
        "effectiveProperties",
        effectiveProperties);
  }

  public Object revisions(String code) {
    require(code);
    return repository.revisions(code);
  }
}
