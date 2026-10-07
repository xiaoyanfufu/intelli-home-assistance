package com.intelli.home.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.home.adapter.out.messaging.*;
import com.intelli.home.application.port.in.*;
import com.intelli.home.application.port.out.*;
import com.intelli.home.application.service.*;
import com.intelli.home.config.RabbitConfig;
import com.intelli.home.domain.device.*;
import com.intelli.home.domain.event.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfSystemProperty(named = "home.integration", matches = "true")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "intelli.scheduling.enabled=false",
      "intelli.mqtt.client-id=intelli-home-integration",
      "intelli.agent.enabled=false"
    })
class MvpIntegrationTest {
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean DeviceEventHandler handler;
  @Autowired DeviceStateStore states;
  @Autowired DeviceRegistry registry;
  @Autowired JdbcTemplate jdbc;
  @Autowired StringRedisTemplate redis;
  @Autowired RabbitTemplate rabbit;
  @Autowired OutboxPublisher publisher;
  @Autowired OfflineDetectionService offline;
  @Autowired RuleConfigService config;
  @Autowired RecommendationUseCase recommendations;
  @Autowired com.intelli.home.domain.rule.RuleEngine ruleEngine;
  @Autowired TestRestTemplate http;
  @Autowired ObjectMapper mapper;
  final List<String> devices = new ArrayList<>();

  DeviceEvent event(String key, String id, long ts, Map<String, Object> properties) {
    return DeviceEvent.builder()
        .messageId(id)
        .deviceKey(key)
        .source(DeviceSource.MOCK)
        .location(Location.INDOOR)
        .eventType(EventType.TELEMETRY)
        .occurredAt(ts)
        .receivedAt(System.currentTimeMillis())
        .properties(properties)
        .build();
  }

  String key() {
    String key = "it-" + UUID.randomUUID();
    devices.add(key);
    return key;
  }

  @AfterEach
  void cleanup() {
    for (String key : devices) {
      var ids =
          jdbc.queryForList(
              "SELECT message_id FROM alert_event WHERE device_key=?", String.class, key);
      for (String id : ids) {
        jdbc.update("DELETE FROM notification_delivery WHERE message_id=?", id);
        jdbc.update("DELETE FROM alert_outbox WHERE message_id=?", id);
      }
      jdbc.update("DELETE FROM alert_event WHERE device_key=?", key);
      jdbc.update("DELETE FROM alert_cooldown WHERE device_key=?", key);
      jdbc.update("DELETE FROM home_device_snapshot WHERE device_key=?", key);
      jdbc.update("DELETE FROM device_telemetry WHERE device_key=?", key);
      jdbc.update("DELETE FROM home_change_event WHERE device_key=?", key);
      jdbc.update("DELETE FROM home_device WHERE device_key=?", key);
      redis.delete("home:v2:state:" + key);
      redis.opsForZSet().remove("home:v2:devices", key);
    }
  }

  @Test
  void persistenceMappingPreservesSnapshotTypesPartialUpdatesAndAlertTimestamp() {
    String key = key();
    long now = System.currentTimeMillis();
    handler.handle(
        event(
            key,
            "mapped-first",
            now,
            Map.of("temperature", 24.5, "humidity", 48, "online", true, "label", "室内")));
    handler.handle(event(key, "mapped-second", now + 1, Map.of("humidity", 50)));
    var snapshot =
        registry.snapshots().stream()
            .filter(s -> key.equals(s.deviceKey()))
            .findFirst()
            .orElseThrow();
    assertEquals("mapped-second", snapshot.eventId());
    assertEquals(Location.INDOOR, snapshot.location());
    assertEquals(now + 1, snapshot.occurredAt());
    assertEquals(24.5, ((Number) snapshot.properties().get("temperature")).doubleValue());
    assertEquals(50, ((Number) snapshot.properties().get("humidity")).intValue());
    assertEquals(true, snapshot.properties().get("online"));
    assertEquals("室内", snapshot.properties().get("label"));
    assertEquals(now, snapshot.propertyTimes().get("temperature"));
    assertEquals(now + 1, snapshot.propertyTimes().get("humidity"));

    String id = UUID.randomUUID().toString();
    var alert =
        com.intelli.home.domain.alert.AlertEvent.builder()
            .messageId(id)
            .deviceKey(key)
            .ruleCode("FIRE_RISK")
            .scene("FIRE")
            .level(com.intelli.home.domain.alert.AlertLevel.CRITICAL)
            .title("映射检查")
            .content("中文内容")
            .occurredAt(now)
            .build();
    alertRepository.record(List.of(alert), 0);
    var loaded =
        alertRepository.recent(100).stream()
            .filter(a -> id.equals(a.getMessageId()))
            .findFirst()
            .orElseThrow();
    assertEquals(alert.getLevel(), loaded.getLevel());
    assertEquals("映射检查", loaded.getTitle());
    assertEquals("中文内容", loaded.getContent());
    // MySQL may round fractional seconds; compare the mapping with an independent JDBC read.
    var storedTime =
        jdbc.queryForObject(
            "SELECT occurred_at FROM alert_event WHERE message_id=?", java.sql.Timestamp.class, id);
    assertEquals(storedTime.getTime(), loaded.getOccurredAt());
    assertTrue(Math.abs(now - loaded.getOccurredAt()) < 1000);
    assertEquals(1, alertRepository.recent(0).size());
  }

  @Test
  void duplicateEventPersistsOnceAndRealRabbitConsumerAcknowledges() throws Exception {
    String key = key();
    var event =
        event(
            key,
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            Map.of("temperature", 46));
    handler.handle(event);
    handler.handle(event);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM alert_event WHERE device_key=?", Integer.class, key));
    String id =
        jdbc.queryForObject(
            "SELECT message_id FROM alert_event WHERE device_key=?", String.class, key);
    publisher.publishPending();
    await(
        () ->
            jdbc.queryForObject(
                    "SELECT COUNT(*) FROM notification_delivery WHERE message_id=?",
                    Integer.class,
                    id)
                == 1);
    assertTrue(
        jdbc.queryForObject(
            "SELECT published FROM alert_outbox WHERE message_id=?", Boolean.class, id));
    var message =
        mapper.readValue(
            jdbc.queryForObject(
                "SELECT payload FROM alert_outbox WHERE message_id=?", String.class, id),
            AlertEventMessage.class);
    rabbit.convertAndSend(RabbitConfig.ALERT_EXCHANGE, RabbitConfig.ALERT_ROUTING_KEY, message);
    Thread.sleep(300);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM notification_delivery WHERE message_id=?", Integer.class, id));
  }

  @Test
  void olderEventCannotOverwriteAndSameLocationDevicesStaySeparate() {
    String a = key(), b = key();
    long now = System.currentTimeMillis();
    handler.handle(event(a, "a-new", now, Map.of("temperature", 25, "smoke", 10)));
    handler.handle(event(a, "a-old", now - 1000, Map.of("temperature", 99)));
    handler.handle(event(b, "b-new", now, Map.of("temperature", 30, "smoke", 10)));
    assertEquals("25", states.snapshots().get(a).properties().get("temperature"));
    assertEquals("30", states.snapshots().get(b).properties().get("temperature"));
  }

  @Test
  void cacheCanRecoverFromDurableSnapshot() {
    String key = key();
    handler.handle(event(key, "restore", System.currentTimeMillis(), Map.of("temperature", 24)));
    var saved =
        registry.snapshots().stream()
            .filter(d -> d.deviceKey().equals(key))
            .findFirst()
            .orElseThrow();
    redis.delete("home:v2:state:" + key);
    states.restore(saved);
    var duplicate = event(key, "restore", saved.occurredAt(), Map.of("temperature", 24));
    states.update(duplicate);
    assertEquals(saved.lastSeenAt(), states.snapshots().get(key).lastSeenAt());
    assertEquals("24", states.snapshots().get(key).properties().get("temperature"));
  }

  @Test
  void offlineScanCreatesOneAlertPerHeartbeatGap() {
    String key = key();
    handler.handle(
        event(
            key, "heartbeat", System.currentTimeMillis(), Map.of("temperature", 24, "smoke", 10)));
    jdbc.update(
        "UPDATE home_device_snapshot SET last_seen_at=? WHERE device_key=?",
        System.currentTimeMillis() - 400000,
        key);
    offline.scan();
    offline.scan();
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM alert_event WHERE device_key=? AND rule_code='DEVICE_OFFLINE'",
            Integer.class,
            key));
  }

  @Test
  void safetyRecommendationDoesNotRequireAgentAndMissingDataIsExplicit() {
    String key = key();
    handler.handle(event(key, "safety", System.currentTimeMillis(), Map.of("temperature", 46)));
    assertEquals("FIRE", recommendations.recommend(key).scenario());
    String empty = key();
    handler.handle(event(empty, "missing", System.currentTimeMillis(), Map.of()));
    // A safety condition anywhere in the home takes priority over another node's missing data.
    assertEquals("FIRE", recommendations.recommend(empty).scenario());
  }

  @Test
  void invalidConfigurationDoesNotReplaceLastValidSnapshot() {
    var old = config.snapshot();
    String params =
        jdbc.queryForObject(
            "SELECT params_json FROM rule_config WHERE rule_code='FIRE_RISK'", String.class);
    try {
      jdbc.update(
          "UPDATE rule_config SET params_json=? WHERE rule_code='FIRE_RISK'",
          "{\"temperature\":-1}");
      config.refresh();
      assertSame(old, config.snapshot());
    } finally {
      jdbc.update("UPDATE rule_config SET params_json=? WHERE rule_code='FIRE_RISK'", params);
      config.refresh();
    }
  }

  @Test
  void httpInvalidSceneReturnsBadRequest() {
    assertEquals(
        400,
        http.postForEntity("/api/mock/scenes/INVALID", null, Map.class).getStatusCode().value());
  }

  @Test
  void mqttBrokerFeedsSameApplicationPipeline() throws Exception {
    String key = key();
    try (var client = new MqttClient("tcp://localhost:1883", "it-pub-" + UUID.randomUUID())) {
      client.connect();
      byte[] body =
          mapper.writeValueAsBytes(
              Map.of("ts", System.currentTimeMillis(), "seq", 1, "temperature", 46));
      client.publish("home/indoor/" + key + "/event", body, 1, false);
      await(
          () ->
              jdbc.queryForObject(
                      "SELECT COUNT(*) FROM alert_event WHERE device_key=?", Integer.class, key)
                  == 1);
      client.disconnect();
    }
  }

  @Test
  void databaseTransactionRollsBackAlertAndOutboxTogether() {
    String key = key();
    var valid =
        com.intelli.home.domain.alert.AlertEvent.builder()
            .messageId(UUID.randomUUID().toString())
            .deviceKey(key)
            .ruleCode("FIRE_RISK")
            .scene("FIRE")
            .level(com.intelli.home.domain.alert.AlertLevel.CRITICAL)
            .title("test")
            .content("test")
            .occurredAt(System.currentTimeMillis())
            .build();
    var invalid =
        com.intelli.home.domain.alert.AlertEvent.builder()
            .messageId(UUID.randomUUID().toString())
            .deviceKey(key)
            .ruleCode("INVALID")
            .scene("FIRE")
            .level(com.intelli.home.domain.alert.AlertLevel.CRITICAL)
            .title("test")
            .content("x".repeat(600))
            .occurredAt(System.currentTimeMillis())
            .build();
    assertThrows(
        org.springframework.dao.DataAccessException.class,
        () -> alertRepository.record(List.of(valid, invalid), 0));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM alert_event WHERE device_key=?", Integer.class, key));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM alert_outbox WHERE message_id=?",
            Integer.class,
            valid.getMessageId()));
  }

  @Autowired AlertRepository alertRepository;

  @Test
  @EnabledIfSystemProperty(named = "home.brokerFaults", matches = "true")
  void pendingOutboxSurvivesBrokerPauseAndDuplicateRepublish() throws Exception {
    // Only operate on the dedicated disposable test broker, never the normal middleware container.
    assertEquals(5673, rabbit.getConnectionFactory().getPort());
    // A previous offline scan may enqueue alerts for other persisted devices.
    // Drain them before pausing, since the publisher stops at the first failed message.
    publisher.publishPending();
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM alert_outbox WHERE published=FALSE", Integer.class));
    String key = key();
    handler.handle(
        event(
            key,
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            Map.of("temperature", 46)));
    String id =
        jdbc.queryForObject(
            "SELECT message_id FROM alert_event WHERE device_key=?", String.class, key);
    docker("pause");
    try {
      publisher.publishPending();
      assertFalse(
          jdbc.queryForObject(
              "SELECT published FROM alert_outbox WHERE message_id=?", Boolean.class, id));
      assertTrue(
          jdbc.queryForObject(
                  "SELECT attempts FROM alert_outbox WHERE message_id=?", Integer.class, id)
              > 0);
    } finally {
      docker("unpause");
    }
    publisher.publishPending();
    await(
        () ->
            jdbc.queryForObject(
                    "SELECT COUNT(*) FROM notification_delivery WHERE message_id=?",
                    Integer.class,
                    id)
                == 1);
    assertTrue(
        jdbc.queryForObject(
            "SELECT published FROM alert_outbox WHERE message_id=?", Boolean.class, id));
    // Simulate confirmed publication followed by a crash before the outbox update.
    jdbc.update("UPDATE alert_outbox SET published=FALSE WHERE message_id=?", id);
    publisher.publishPending();
    Thread.sleep(300);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM notification_delivery WHERE message_id=?", Integer.class, id));
  }

  void docker(String operation) throws Exception {
    var process =
        new ProcessBuilder("docker", operation, "intelli-home-it-rabbit")
            .redirectErrorStream(true)
            .start();
    assertTrue(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS));
    assertEquals(
        0,
        process.exitValue(),
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
  }

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  NotificationGateway notification;

  @Autowired com.intelli.home.adapter.in.mqtt.MqttDeviceAccessAdapter mqtt;

  @Test
  void mqttProcessingFailureCanBeRedeliveredAfterReconnect() throws Exception {
    String key = key();
    org.mockito.Mockito.doThrow(new IllegalStateException("temporary processing failure"))
        .doCallRealMethod()
        .when(handler)
        .handle(org.mockito.Mockito.argThat(e -> key.equals(e.getDeviceKey())));
    try (var client = new MqttClient("tcp://localhost:1883", "it-retry-" + UUID.randomUUID())) {
      client.connect();
      client.publish(
          "home/indoor/" + key + "/event",
          mapper.writeValueAsBytes(
              Map.of("ts", System.currentTimeMillis(), "seq", 1, "temperature", 46)),
          1,
          false);
      await(
          () -> {
            mqtt.reconnect();
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM alert_event WHERE device_key=?", Integer.class, key)
                == 1;
          });
      org.mockito.Mockito.verify(handler, org.mockito.Mockito.atLeast(2))
          .handle(org.mockito.Mockito.argThat(e -> key.equals(e.getDeviceKey())));
      client.disconnect();
    }
  }

  @Test
  void delayedSampleIsNotFreshJustBecauseItArrivedNow() {
    String key = key();
    handler.handle(
        event(key, "delayed", System.currentTimeMillis() - 3600000, Map.of("temperature", 46)));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM alert_event WHERE device_key=?", Integer.class, key));
    // Scope this freshness assertion to its own real Redis snapshot; another household's
    // fresh fire condition legitimately takes safety priority in the shared development database.
    var selectedStates = org.mockito.Mockito.mock(DeviceStateStore.class);
    org.mockito.Mockito.when(selectedStates.snapshots())
        .thenReturn(Map.of(key, states.snapshots().get(key)));
    var selectedRecommendations =
        new RecommendationService(
            selectedStates, org.mockito.Mockito.mock(AgentGateway.class), ruleEngine, config);
    assertEquals("STALE_DATA", selectedRecommendations.recommend(key).degradationReason());
  }

  @Test
  @EnabledIfSystemProperty(named = "home.brokerFaults", matches = "true")
  void failedNotificationRollsBackReceiptAndEntersDeadLetterAfterThreeAttempts() throws Exception {
    assertEquals(5673, rabbit.getConnectionFactory().getPort());
    String key = key();
    org.mockito.Mockito.doThrow(new IllegalStateException("test notification failure"))
        .when(notification)
        .send(org.mockito.Mockito.argThat(alert -> key.equals(alert.getDeviceKey())));
    handler.handle(
        event(
            key,
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            Map.of("temperature", 46)));
    String id =
        jdbc.queryForObject(
            "SELECT message_id FROM alert_event WHERE device_key=?", String.class, key);
    publisher.publishPending();
    var dead = rabbit.receive(RabbitConfig.ALERT_DLQ, 10000);
    assertNotNull(dead, "Failed notification must be rejected even with manual ACK");
    var message = mapper.readValue(dead.getBody(), AlertEventMessage.class);
    assertEquals(id, message.getMessageId());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM notification_delivery WHERE message_id=?", Integer.class, id));
    org.mockito.Mockito.verify(notification, org.mockito.Mockito.times(3))
        .send(org.mockito.Mockito.argThat(alert -> key.equals(alert.getDeviceKey())));
    org.mockito.Mockito.reset(notification);
    rabbit.convertAndSend(RabbitConfig.ALERT_EXCHANGE, RabbitConfig.ALERT_ROUTING_KEY, message);
    await(
        () ->
            jdbc.queryForObject(
                    "SELECT COUNT(*) FROM notification_delivery WHERE message_id=?",
                    Integer.class,
                    id)
                == 1);
  }

  void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 10000;
    while (System.currentTimeMillis() < deadline) {
      if (condition.getAsBoolean()) return;
      Thread.sleep(100);
    }
    fail("Timed out waiting for integration result");
  }
}
