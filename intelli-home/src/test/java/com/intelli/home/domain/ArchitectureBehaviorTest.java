package com.intelli.home.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.home.adapter.in.mqtt.MqttDeviceEventParser;
import com.intelli.home.domain.device.*;
import com.intelli.home.domain.event.*;
import com.intelli.home.domain.rule.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ArchitectureBehaviorTest {
  DeviceEvent event(Map<String, Object> properties) {
    return DeviceEvent.builder()
        .messageId("event-1")
        .deviceKey("test-indoor")
        .source(DeviceSource.MOCK)
        .location(Location.INDOOR)
        .eventType(EventType.TELEMETRY)
        .occurredAt(1000L)
        .receivedAt(1000L)
        .properties(properties)
        .build();
  }

  @Test
  void exactThresholdMatchesEvenWithOneMissingSensor() {
    var result =
        new FireRiskRule()
            .evaluate(new RuleContext(event(Map.of()), Map.of("temperature", 45), Map.of()));
    assertEquals(RuleResult.Status.MATCHED, result.status());
  }

  @Test
  void missingSensorIsNotNormal() {
    var result =
        new FireRiskRule()
            .evaluate(new RuleContext(event(Map.of()), Map.of("temperature", 20), Map.of()));
    assertEquals(RuleResult.Status.INSUFFICIENT_DATA, result.status());
  }

  @Test
  void malformedAndNonFiniteValuesAreMissing() {
    var context =
        new RuleContext(event(Map.of()), Map.of("smoke", "NaN", "temperature", "bad"), Map.of());
    assertNull(context.number("smoke"));
    assertNull(context.number("temperature"));
  }

  @Test
  void alertsHaveStablePerRuleIdsAndDisabledRulesDoNotExecute() {
    var engine = new RuleEngine(List.of(new FireRiskRule()));
    var config = Map.of("FIRE_RISK", new RuleDefinition("FIRE_RISK", true, 10, Map.of()));
    var first =
        engine.evaluate(
            new RuleEvaluationInput(event(Map.of()), Map.of("temperature", 46), config));
    var retry =
        engine.evaluate(
            new RuleEvaluationInput(event(Map.of()), Map.of("temperature", 46), config));
    assertEquals(first.getFirst().getMessageId(), retry.getFirst().getMessageId());
    assertTrue(
        engine
            .evaluate(
                new RuleEvaluationInput(
                    event(Map.of()),
                    Map.of("temperature", 46),
                    Map.of("FIRE_RISK", new RuleDefinition("FIRE_RISK", false, 10, Map.of()))))
            .isEmpty());
  }

  @Test
  void configurationPriorityDeterminesExecutionOrder() {
    class Always implements Rule {
      final String code;

      Always(String code) {
        this.code = code;
      }

      public String code() {
        return code;
      }

      public RuleScene scene() {
        return RuleScene.FIRE;
      }

      public boolean supports(RuleContext c) {
        return true;
      }

      public RuleResult evaluate(RuleContext c) {
        return RuleResult.matched(com.intelli.home.domain.alert.AlertLevel.WARN, code, code);
      }
    }
    var engine = new RuleEngine(List.of(new Always("A"), new Always("B")));
    var config =
        Map.of(
            "A",
            new RuleDefinition("A", true, 20, Map.of()),
            "B",
            new RuleDefinition("B", true, 10, Map.of()));
    assertEquals(
        List.of("B", "A"),
        engine.evaluate(new RuleEvaluationInput(event(Map.of()), Map.of(), config)).stream()
            .map(a -> a.getRuleCode())
            .toList());
  }

  @Test
  void staleAttributesAreExcludedIndividually() {
    var snapshot =
        new DeviceSnapshot(
            "test",
            "test-event",
            Location.INDOOR,
            100,
            1000,
            Map.of("temperature", 46, "smoke", 400),
            Map.of("temperature", 1000L, "smoke", 1L));
    assertEquals(Map.of("temperature", 46), snapshot.freshProperties(1100, 500));
    assertTrue(snapshot.freshProperties(2000, 500).isEmpty());
  }

  @Test
  void mqttParserRequiresStableIdentityAndExactTopic() {
    var parser = new MqttDeviceEventParser(new ObjectMapper());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            parser.parse("home/indoor/node/event", "{\"ts\":1000,\"temperature\":20}".getBytes()));
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("wrong/indoor/node/event", "{}".getBytes()));
    byte[] payload = "{\"ts\":1000,\"seq\":1,\"temperature\":20}".getBytes();
    var one = parser.parse("home/indoor/node/event", payload);
    var two = parser.parse("home/indoor/node/event", payload);
    assertEquals(one.getMessageId(), two.getMessageId());
    assertFalse(one.getProperties().containsKey("seq"));
  }

  @Test
  void offlineRuleOnlyConsumesStatusEvents() {
    var rule = new OfflineRule();
    var e = event(Map.of("online", false));
    assertFalse(rule.supports(new RuleContext(e, e.getProperties(), Map.of())));
    e.setEventType(EventType.STATUS);
    assertTrue(rule.supports(new RuleContext(e, e.getProperties(), Map.of())));
    assertEquals(
        RuleResult.Status.MATCHED,
        rule.evaluate(new RuleContext(e, e.getProperties(), Map.of())).status());
  }

  @Test
  void evaluationUsesEffectiveDataWithoutReplacingSourceEventProperties() {
    var engine = new RuleEngine(List.of(new FireRiskRule()));
    var definitions = Map.of("FIRE_RISK", new RuleDefinition("FIRE_RISK", true, 10, Map.of()));
    var sourceEvent = event(Map.of("temperature", 46));
    var input =
        new RuleEvaluationInput(sourceEvent, Map.of("temperature", 20, "smoke", 10), definitions);
    assertTrue(engine.evaluate(input).isEmpty());
    assertEquals(Map.of("temperature", 46), sourceEvent.getProperties());

    var mergedInput =
        new RuleEvaluationInput(sourceEvent, Map.of("temperature", 20, "smoke", 400), definitions);
    var alert = engine.evaluate(mergedInput).getFirst();
    assertEquals(sourceEvent.getDeviceKey(), alert.getDeviceKey());
    assertEquals(sourceEvent.getOccurredAt(), alert.getOccurredAt());
    assertEquals(
        DeviceEvent.stableId("alert", sourceEvent.getMessageId() + ":FIRE_RISK"),
        alert.getMessageId());
    assertEquals(Map.of("temperature", 46), sourceEvent.getProperties());
  }

  @Test
  void configurationParametersCannotBeMutated() {
    var source = new HashMap<String, Double>();
    source.put("temperature", 45.0);
    var definition = new RuleDefinition("FIRE_RISK", true, 10, source);
    source.put("temperature", 99.0);
    assertEquals(45.0, definition.params().get("temperature"));
    assertThrows(
        UnsupportedOperationException.class, () -> definition.params().put("temperature", 30.0));
  }
}
