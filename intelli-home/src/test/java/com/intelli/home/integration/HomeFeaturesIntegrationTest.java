package com.intelli.home.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.home.application.port.in.DeviceEventHandler;
import com.intelli.home.application.port.out.*;
import com.intelli.home.application.service.*;
import com.intelli.home.domain.device.*;
import com.intelli.home.domain.event.*;
import com.intelli.home.domain.home.*;
import com.intelli.home.domain.rule.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfSystemProperty(named = "home.integration", matches = "true")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "intelli.scheduling.enabled=false", "intelli.mqtt.client-id=intelli-home-features-it",
      "intelli.extensions.mock-controls=true", "intelli.extensions.weather-mode=mock"
    })
class HomeFeaturesIntegrationTest {
  @Autowired DeviceEventHandler handler;
  @Autowired DeviceCatalogService devices;
  @Autowired TelemetryHistoryService history;
  @Autowired PageService pages;
  @Autowired RuleManagementService rules;
  @Autowired RuleConfigService config;
  @Autowired LaundryService laundry;
  @Autowired CommandService commands;
  @Autowired CommandDispatchService dispatch;
  @Autowired CommandRepository commandRepository;
  @Autowired ChangeFeed changes;
  @Autowired RealtimeService realtime;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired StringRedisTemplate redis;
  @LocalServerPort int port;
  final List<String> keys = new ArrayList<>();
  final List<String> pageIds = new ArrayList<>();
  WeatherSnapshot oldWeather;

  @BeforeEach
  void captureWeather() {
    oldWeather = laundry.weather();
  }

  String device(String location, boolean control) {
    String key = "feature-it-" + UUID.randomUUID();
    keys.add(key);
    devices.register(
        new HomeDevice(
            key, "测试设备", control ? "mock-switch" : "environment", 1, "MOCK", location, 0));
    return key;
  }

  DeviceEvent sample(String key, String id, long time, Map<String, Object> properties) {
    return DeviceEvent.builder()
        .messageId(id)
        .deviceKey(key)
        .source(DeviceSource.MOCK)
        .location(Location.valueOf(devices.require(key).location()))
        .eventType(EventType.TELEMETRY)
        .occurredAt(time)
        .receivedAt(System.currentTimeMillis())
        .properties(properties)
        .build();
  }

  @AfterEach
  void cleanup() {
    for (String id : pageIds) {
      jdbc.update("DELETE FROM dashboard_page_revision WHERE page_id=?", id);
      jdbc.update("DELETE FROM dashboard_page WHERE id=?", id);
    }
    for (String key : keys) {
      for (String id :
          jdbc.queryForList(
              "SELECT id FROM device_command WHERE device_key=?", String.class, key)) {
        jdbc.update("DELETE FROM mock_command_execution WHERE command_id=?", id);
        jdbc.update("DELETE FROM command_outbox WHERE command_id=?", id);
      }
      jdbc.update("DELETE FROM device_command WHERE device_key=?", key);
      jdbc.update("DELETE FROM laundry_session WHERE device_key=?", key);
      for (String id :
          jdbc.queryForList(
              "SELECT message_id FROM alert_event WHERE device_key=?", String.class, key)) {
        jdbc.update("DELETE FROM notification_delivery WHERE message_id=?", id);
        jdbc.update("DELETE FROM alert_outbox WHERE message_id=?", id);
      }
      jdbc.update("DELETE FROM alert_event WHERE device_key=?", key);
      jdbc.update("DELETE FROM alert_cooldown WHERE device_key=?", key);
      jdbc.update("DELETE FROM home_change_event WHERE device_key=?", key);
      jdbc.update("DELETE FROM device_telemetry WHERE device_key=?", key);
      jdbc.update("DELETE FROM home_device_snapshot WHERE device_key=?", key);
      jdbc.update("DELETE FROM home_device WHERE device_key=?", key);
      redis.delete("home:v2:state:" + key);
      redis.opsForZSet().remove("home:v2:devices", key);
    }
    if (oldWeather == null) jdbc.update("DELETE FROM weather_snapshot");
    else laundry.saveWeather(oldWeather);
  }

  @Test
  void capabilitiesNormalizeTypesAndRejectUnsupportedValues() {
    String key = device("INDOOR", false);
    assertTrue(devices.capabilities(key).actions().isEmpty());
    assertFalse(devices.capabilities(key).properties().get("smoke").calibrated());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            handler.handle(
                sample(key, "range", System.currentTimeMillis(), Map.of("humidity", 101))));
    var event = sample(key, "typed", System.currentTimeMillis(), Map.of("temperature", "25.5"));
    handler.handle(event);
    assertEquals(25.5, event.getProperties().get("temperature"));
    assertThrows(
        IllegalArgumentException.class,
        () -> commands.submit(key, "key", "setPower", Map.of("power", true)));
  }

  @Test
  void historyKeepsLateSamplesDeduplicatesAndPaginatesSameTimestamp() {
    String key = device("INDOOR", false);
    long now = System.currentTimeMillis();
    handler.handle(sample(key, "hist-z", now, Map.of("temperature", 30)));
    handler.handle(sample(key, "hist-a", now, Map.of("humidity", 40)));
    handler.handle(sample(key, "hist-a", now, Map.of("humidity", 40)));
    handler.handle(sample(key, "hist-old", now - 1000, Map.of("temperature", 20)));
    var first = history.query(key, now - 2000, now + 1, null, 1);
    var second = history.query(key, now - 2000, now + 1, first.nextCursor(), 1);
    var third = history.query(key, now - 2000, now + 1, second.nextCursor(), 1);
    assertEquals(
        List.of("hist-old", "hist-a", "hist-z"),
        List.of(
            first.items().getFirst().eventId(),
            second.items().getFirst().eventId(),
            third.items().getFirst().eventId()));
    assertNull(third.nextCursor());
    assertFalse(second.items().getFirst().properties().containsKey("temperature"));
    assertEquals("30", redis.opsForHash().get("home:v2:state:" + key, "p:temperature"));
    var buckets =
        (List<?>) history.trend(key, "temperature", now - 2000, now + 1, "hour", null, 100);
    int count = buckets.stream().mapToInt(b -> ((TelemetryHistoryService.Bucket) b).count()).sum();
    assertEquals(2, count);
  }

  PageDefinition page(String key) {
    return new PageDefinition(
        1,
        "室内环境",
        List.of(
            new PageDefinition.Widget(
                "temp",
                "device-property",
                0,
                0,
                6,
                2,
                Map.of("deviceKey", key, "property", "temperature")),
            new PageDefinition.Widget(
                "trend",
                "history-chart",
                6,
                0,
                6,
                3,
                Map.of(
                    "deviceKey",
                    key,
                    "property",
                    "temperature",
                    "interval",
                    "minute",
                    "rangeSeconds",
                    3600))));
  }

  @Test
  void pageVersionConflictsRestoreAndRejectArbitraryBindings() {
    String key = device("INDOOR", false);
    var first = pages.create(page(key));
    pageIds.add(first.id());
    var second =
        pages.replace(first.id(), 1, new PageDefinition(1, "新版", first.definition().components()));
    assertEquals(2, second.revision());
    assertThrows(VersionConflictException.class, () -> pages.replace(first.id(), 1, page(key)));
    var restored = pages.restore(first.id(), 2, 1);
    assertEquals(3, restored.revision());
    assertEquals("室内环境", restored.definition().title());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            pages.create(
                new PageDefinition(
                    1,
                    "错误",
                    List.of(
                        new PageDefinition.Widget(
                            "bad",
                            "history-chart",
                            0,
                            0,
                            6,
                            2,
                            Map.of(
                                "deviceKey",
                                key,
                                "property",
                                "temperature",
                                "sql",
                                "SELECT *"))))));
    assertEquals(List.of(3L, 2L, 1L), pages.revisions(first.id()));
  }

  @Test
  void ruleUpdatesHaveVersionConflictAndDryRunHasNoWrites() {
    var saved = rules.require("FIRE_RISK");
    try {
      var next =
          new RuleDefinition("FIRE_RISK", true, 10, Map.of("temperature", 44.0, "smoke", 300.0));
      rules.update("FIRE_RISK", saved.revision(), next);
      config.refresh();
      assertTrue((Boolean) rules.describe("FIRE_RISK").get("active"));
      assertThrows(
          VersionConflictException.class, () -> rules.update("FIRE_RISK", saved.revision(), next));
      String key = device("INDOOR", false);
      var response =
          rules.trial(
              "FIRE_RISK",
              sample(key, "dryrun", System.currentTimeMillis(), Map.of()),
              Map.of("temperature", 44),
              next);
      assertNotNull(response);
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM alert_event WHERE device_key=?", Integer.class, key));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              rules.update(
                  "FIRE_RISK",
                  saved.revision() + 1,
                  new RuleDefinition(
                      "FIRE_RISK", true, 10, Map.of("temperature", -1.0, "smoke", 300.0))));
    } finally {
      var current = rules.require("FIRE_RISK");
      rules.update("FIRE_RISK", current.revision(), saved.definition());
      config.refresh();
    }
  }

  WeatherSnapshot weather(boolean raining, long now) {
    return new WeatherSnapshot("local", "mock", now, now + 600000, true, raining, null, null);
  }

  @Test
  void laundryNeedsActiveTaskAndDeduplicatesRiskCycles() {
    String key = device("BALCONY", false);
    long now = System.currentTimeMillis();
    laundry.saveWeather(weather(true, now));
    laundry.evaluate();
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM alert_event WHERE device_key=?", Integer.class, key));
    var session = laundry.start(key);
    laundry.evaluate();
    laundry.evaluate();
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM alert_event WHERE device_key=?", Integer.class, key));
    assertThrows(VersionConflictException.class, () -> laundry.start(key));
    laundry.saveWeather(weather(false, now));
    laundry.evaluate();
    laundry.saveWeather(weather(true, now));
    laundry.evaluate();
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM alert_event WHERE device_key=?", Integer.class, key));
    laundry.complete(session.id());
    laundry.evaluate();
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM alert_event WHERE device_key=?", Integer.class, key));
    var verdict =
        laundry.trial(
            true,
            new WeatherSnapshot(
                "local", "mock", now - 700000, now - 100000, true, true, null, null),
            config.snapshot().get("COLLECT_LAUNDRY"));
    assertEquals(RuleResult.Status.INSUFFICIENT_DATA, verdict.status());
  }

  @Test
  void mqttMockCommandCompletesOnlyAfterReplyAndDoesNotExecuteTwice() throws Exception {
    String key = device("INDOOR", true);
    long now = System.currentTimeMillis();
    handler.handle(sample(key, "command-heartbeat", now, Map.of("power", false)));
    var command = commands.submit(key, "request-1", "setPower", Map.of("power", true));
    assertEquals("PENDING", command.status());
    assertEquals(
        command.id(), commands.submit(key, "request-1", "setPower", Map.of("power", true)).id());
    assertThrows(
        VersionConflictException.class,
        () -> commands.submit(key, "request-1", "setPower", Map.of("power", false)));
    dispatch.dispatch();
    await(() -> commands.require(command.id()).status().equals("SUCCEEDED"));
    assertEquals("true", redis.opsForHash().get("home:v2:state:" + key, "p:power"));
    jdbc.update("UPDATE command_outbox SET published=FALSE WHERE command_id=?", command.id());
    // Replay the actual command payload through EMQX, simulating publication before an outbox mark.
    var gateway = org.springframework.test.util.ReflectionTestUtils.getField(dispatch, "gateway");
    ((CommandGateway) gateway).publish(commands.require(command.id()));
    Thread.sleep(300);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM mock_command_execution WHERE command_id=?",
            Integer.class,
            command.id()));
    assertEquals("SUCCEEDED", commands.require(command.id()).status());
  }

  @Test
  void lateOrMismatchedRepliesCannotRewriteCommandOutcome() {
    String key = device("INDOOR", true);
    handler.handle(
        sample(key, "timeout-heartbeat", System.currentTimeMillis(), Map.of("power", false)));
    var command = commands.submit(key, "late-1", "setPower", Map.of("power", true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            commands.reply(
                "wrong",
                "INDOOR",
                new CommandReply(
                    1, command.id(), key, true, System.currentTimeMillis(), Map.of(), Map.of())));
    commands.timeout(command.id(), command.expiresAt() + 1);
    commands.reply(
        key,
        "INDOOR",
        new CommandReply(
            1,
            command.id(),
            key,
            true,
            System.currentTimeMillis(),
            Map.of("power", true),
            Map.of("power", true)));
    assertEquals("TIMED_OUT", commands.require(command.id()).status());
    assertNotNull(commands.require(command.id()).lateResult());
    assertEquals("false", redis.opsForHash().get("home:v2:state:" + key, "p:power"));
  }

  @Test
  void twoSseClientsSeeCommittedChangesAndReplayFromCursor() throws Exception {
    String key = device("INDOOR", false);
    long cursor = ((Number) realtime.baseline().get("cursor")).longValue();
    var uri =
        URI.create(
            "http://localhost:"
                + port
                + "/api/events/stream?cursor="
                + cursor
                + "&deviceKey="
                + key);
    var client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(java.time.Duration.ofSeconds(3))
            .build();
    try (var a =
            client
                .send(
                    HttpRequest.newBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream())
                .body();
        var b =
            client
                .send(
                    HttpRequest.newBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream())
                .body()) {
      handler.handle(
          sample(key, "sse-event", System.currentTimeMillis(), Map.of("temperature", 24)));
      var workers = Executors.newFixedThreadPool(2);
      try {
        var one = workers.submit(() -> readState(a, key));
        var two = workers.submit(() -> readState(b, key));
        assertTrue(one.get(5, TimeUnit.SECONDS));
        assertTrue(two.get(5, TimeUnit.SECONDS));
      } finally {
        workers.shutdownNow();
      }
    }
    var replay =
        changes.after(cursor, 100).stream().filter(e -> key.equals(e.deviceKey())).toList();
    assertEquals(1, replay.size());
    assertEquals("device.state.changed", replay.getFirst().type());
    handler.handle(sample(key, "sse-next", System.currentTimeMillis(), Map.of("humidity", 40)));
    var next =
        changes.after(replay.getFirst().id(), 100).stream()
            .filter(e -> key.equals(e.deviceKey()))
            .findFirst()
            .orElseThrow();
    assertTrue(next.resourceVersion() > replay.getFirst().resourceVersion());
  }

  boolean readState(InputStream stream, String key) throws IOException {
    var reader =
        new BufferedReader(new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
    String line;
    while ((line = reader.readLine()) != null)
      if (line.startsWith("data:") && line.contains(key) && line.contains("device.state.changed"))
        return true;
    return false;
  }

  void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
    long end = System.currentTimeMillis() + 10000;
    while (System.currentTimeMillis() < end) {
      if (condition.getAsBoolean()) return;
      Thread.sleep(100);
    }
    fail("Timed out");
  }
}
