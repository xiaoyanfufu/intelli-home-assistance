package com.intelli.home.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.intelli.home.application.port.in.AlertNotificationHandler;
import com.intelli.home.application.port.out.NotificationReceiptStore;
import com.intelli.home.application.service.DailyAdviceService;
import com.intelli.home.domain.alert.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfSystemProperty(named = "home.integration", matches = "true")
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "intelli.scheduling.enabled=false",
      "intelli.mqtt.enabled=false",
      "intelli.notification.channel=email",
      "intelli.notification.email.enabled=true",
      "intelli.notification.email.host=127.0.0.1",
      "intelli.notification.email.security=none",
      "intelli.notification.email.username=",
      "intelli.notification.email.password=",
      "intelli.notification.email.from=sender@example.test",
      "intelli.notification.email.to=receiver@example.test",
      "intelli.notification.api.enabled=true",
      "intelli.agent.enabled=true",
      "intelli.agent.timeout=5s"
    })
@Import(EmailDailyAdviceIntegrationTest.TestClock.class)
class EmailDailyAdviceIntegrationTest {
  static final GreenMail smtp = new GreenMail(new ServerSetup(0, "127.0.0.1", "smtp"));
  static Process python;
  static final Clock clock =
      Clock.fixed(
          LocalDate.of(2100, 1, 1)
              .plusDays(new java.security.SecureRandom().nextInt(36000))
              .atStartOfDay(ZoneOffset.UTC)
              .toInstant(),
          ZoneOffset.UTC);
  static final String dailyId =
      "daily-advice:" + LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
  static int pythonPort;

  @TestConfiguration(proxyBeanMethods = false)
  static class TestClock {
    @Bean
    @Primary
    Clock testDailyClock() {
      return clock;
    }
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) throws Exception {
    smtp.start();
    smtp.setUser("receiver@example.test", "receiver", "password");
    try {
      try (var socket = new java.net.ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
        pythonPort = socket.getLocalPort();
      }
      Path agent =
          Path.of(System.getProperty("user.dir")).resolve("../intelli-home-agent").normalize();
      var process =
          new ProcessBuilder(
              agent.resolve(".venv/Scripts/python.exe").toString(),
              "-m",
              "uvicorn",
              "intelli_agent.main:app",
              "--host",
              "127.0.0.1",
              "--port",
              Integer.toString(pythonPort));
      process.directory(agent.toFile());
      process.environment().put("LLM_API_KEY", "");
      process.environment().put("WEATHER_API_KEY", "");
      process.environment().put("PYTHONPATH", agent.resolve("src").toString());
      process
          .redirectErrorStream(true)
          .redirectOutput(Path.of("target/python-daily-it.log").toFile());
      python = process.start();
      var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
      boolean ready = false;
      for (int i = 0; i < 60; i++) {
        if (!python.isAlive())
          throw new IllegalStateException(
              "Test Python process exited; inspect target/python-daily-it.log");
        try {
          var response =
              client.send(
                  HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + pythonPort + "/health"))
                      .timeout(Duration.ofSeconds(1))
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.discarding());
          if (response.statusCode() == 200) {
            ready = true;
            break;
          }
        } catch (java.io.IOException ignored) {
        }
        Thread.sleep(100);
      }
      if (!ready) throw new IllegalStateException("Test Python process did not become ready");
      registry.add("intelli.notification.email.port", () -> smtp.getSmtp().getPort());
      registry.add("intelli.agent.base-url", () -> "http://127.0.0.1:" + pythonPort);
    } catch (Exception e) {
      stopInfrastructure();
      throw e;
    }
  }

  static void stopInfrastructure() {
    if (python != null) {
      python.destroy();
      try {
        if (!python.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) python.destroyForcibly();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        python.destroyForcibly();
      }
    }
    smtp.stop();
  }

  @AfterAll
  static void stop() {
    stopInfrastructure();
  }

  @Autowired DailyAdviceService daily;
  @Autowired NotificationReceiptStore receipts;
  @Autowired AlertNotificationHandler delivery;
  @Autowired JdbcTemplate jdbc;
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean JavaMailSenderImpl sender;
  @Autowired com.intelli.home.adapter.out.messaging.OutboxPublisher publisher;
  @Autowired org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;
  @Autowired com.intelli.home.application.port.out.AlertRepository alertRepository;
  @Autowired com.intelli.home.config.IntelliProperties.DailyAdvice dailySettings;
  @Autowired com.intelli.home.config.IntelliProperties.Notification notificationSettings;
  @Autowired com.intelli.home.application.port.out.DeviceStateStore states;
  @Autowired com.intelli.home.application.port.out.DeviceCatalog catalog;
  @Autowired com.intelli.home.application.service.DailyAdviceHistorySummarizer summarizer;
  @Autowired ObjectMapper json;
  @LocalServerPort int port;
  final List<String> ownIds = new ArrayList<>();

  @BeforeEach
  void reset() throws Exception {
    smtp.purgeEmailFromAllMailboxes();
    jdbc.update("DELETE FROM notification_delivery WHERE message_id=?", dailyId);
    ownIds.add(dailyId);
  }

  @AfterEach
  void cleanup() {
    sender.setPort(smtp.getSmtp().getPort());
    ownIds.forEach(id -> jdbc.update("DELETE FROM notification_delivery WHERE message_id=?", id));
  }

  long count(String id) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM notification_delivery WHERE message_id=?", Long.class, id);
  }

  @Test
  void realPythonPreviewThenDeliveryAndPersistentGuard() {
    var preview = daily.run(true);
    assertEquals("DRY_RUN", preview.get("status"));
    assertEquals(0, count(dailyId));
    assertEquals(0, smtp.getReceivedMessages().length);
    assertEquals("SENT", daily.run(false).get("status"));
    assertTrue(smtp.waitForIncomingEmail(2000, 1));
    assertEquals(1, count(dailyId));
    assertEquals("SKIPPED_ALREADY_SENT", daily.run(false).get("status"));
    assertEquals(1, smtp.getReceivedMessages().length);
    try {
      var message = smtp.getReceivedMessages()[0];
      assertTrue(message.getSubject().contains(dailyId));
      assertTrue(message.getContent().toString().contains("历史回顾"));
      assertTrue(message.getContent().toString().contains("降级模式"));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM alert_event WHERE message_id=?", Long.class, dailyId));
  }

  @Test
  void smtpFailureRollsBackReceiptAndNextRunRetries() throws Exception {
    try (var unused = new java.net.ServerSocket(0)) {
      sender.setPort(unused.getLocalPort());
    }
    assertEquals("FAILED", daily.run(false).get("status"));
    assertEquals(0, count(dailyId));
    sender.setPort(smtp.getSmtp().getPort());
    assertEquals("SENT", daily.run(false).get("status"));
    assertTrue(smtp.waitForIncomingEmail(2000, 1));
    assertEquals(1, count(dailyId));
  }

  @Test
  void databaseGuardSurvivesNewServiceObject() {
    // Simulate a prior process's committed receipt without relying on this object's memory.
    jdbc.update("INSERT INTO notification_delivery(message_id) VALUES (?)", dailyId);
    var agent = org.mockito.Mockito.mock(com.intelli.home.application.port.out.AgentGateway.class);
    var recreated =
        new DailyAdviceService(
            dailySettings,
            notificationSettings,
            states,
            catalog,
            alertRepository,
            receipts,
            agent,
            delivery,
            summarizer,
            clock);
    assertEquals("SKIPPED_ALREADY_SENT", recreated.run(false).get("status"));
    org.mockito.Mockito.verifyNoInteractions(agent);
    assertEquals(0, smtp.getReceivedMessages().length);
  }

  @Test
  @EnabledIfSystemProperty(named = "home.brokerFaults", matches = "true")
  void smtpFailureRetriesThreeTimesThenDeadLetterCanBeReplayed() throws Exception {
    assertEquals(5673, rabbit.getConnectionFactory().getPort());
    String id = "mail-it-" + UUID.randomUUID();
    ownIds.add(id);
    var alert =
        AlertEvent.builder()
            .messageId(id)
            .deviceKey(id)
            .ruleCode("FIRE_RISK")
            .scene("FIRE")
            .level(AlertLevel.CRITICAL)
            .title("SMTP 故障测试")
            .content("仅测试")
            .occurredAt(System.currentTimeMillis())
            .build();
    try (var unused = new java.net.ServerSocket(0)) {
      sender.setPort(unused.getLocalPort());
    }
    org.mockito.Mockito.clearInvocations(sender);
    var message = com.intelli.home.adapter.out.messaging.AlertEventMessage.of(alert, "email-it");
    rabbit.convertAndSend(
        com.intelli.home.config.RabbitConfig.ALERT_EXCHANGE,
        com.intelli.home.config.RabbitConfig.ALERT_ROUTING_KEY,
        message);
    var dead = rabbit.receive(com.intelli.home.config.RabbitConfig.ALERT_DLQ, 10000);
    assertNotNull(dead);
    assertEquals(id, json.readTree(dead.getBody()).get("messageId").asText());
    assertEquals(0, count(id));
    org.mockito.Mockito.verify(sender, org.mockito.Mockito.times(3))
        .send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
    sender.setPort(smtp.getSmtp().getPort());
    rabbit.convertAndSend(
        com.intelli.home.config.RabbitConfig.ALERT_EXCHANGE,
        com.intelli.home.config.RabbitConfig.ALERT_ROUTING_KEY,
        message);
    assertTrue(smtp.waitForIncomingEmail(3000, 1));
    awaitReceipt(id);
    assertEquals(1, count(id));
  }

  @Test
  @EnabledIfSystemProperty(named = "home.brokerFaults", matches = "true")
  void blockedDailyAgentDoesNotDelayRealOutboxPublication() throws Exception {
    assertEquals(5673, rabbit.getConnectionFactory().getPort());
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var agent = org.mockito.Mockito.mock(com.intelli.home.application.port.out.AgentGateway.class);
    org.mockito.Mockito.when(agent.dailyAdvice(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            i -> {
              entered.countDown();
              assertTrue(release.await(8, java.util.concurrent.TimeUnit.SECONDS));
              return new com.intelli.home.application.model.DailyAdviceResult(
                  "慢调用测试", Map.of(), true, "TEST");
            });
    var slow =
        new DailyAdviceService(
            dailySettings,
            notificationSettings,
            states,
            catalog,
            alertRepository,
            receipts,
            agent,
            delivery,
            summarizer,
            clock);
    var dailyScheduler =
        new com.intelli.home.config.DailyAdviceSchedulingConfig().dailyAdviceScheduler();
    var coreScheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
    coreScheduler.setPoolSize(1);
    coreScheduler.setThreadNamePrefix("home-scheduled-it-");
    dailyScheduler.initialize();
    coreScheduler.initialize();
    String id = "mail-it-" + UUID.randomUUID();
    ownIds.add(id);
    var future = dailyScheduler.schedule(() -> slow.run(false), Instant.now());
    try {
      assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
      alertRepository.record(
          List.of(
              AlertEvent.builder()
                  .messageId(id)
                  .deviceKey(id)
                  .ruleCode("FIRE_RISK")
                  .scene("FIRE")
                  .level(AlertLevel.CRITICAL)
                  .title("隔离调度测试")
                  .content("仅测试")
                  .occurredAt(System.currentTimeMillis())
                  .build()),
          0);
      coreScheduler.schedule(publisher::publishPending, Instant.now());
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
      boolean published = false;
      while (System.nanoTime() < deadline) {
        published =
            jdbc.queryForObject(
                "SELECT published FROM alert_outbox WHERE message_id=?", Boolean.class, id);
        if (published) break;
        Thread.sleep(50);
      }
      assertTrue(published, "Outbox must publish while daily Agent remains blocked");
      assertEquals(1, release.getCount());
      awaitReceipt(id);
    } finally {
      release.countDown();
      try {
        future.get(3, java.util.concurrent.TimeUnit.SECONDS);
      } finally {
        dailyScheduler.shutdown();
        coreScheduler.shutdown();
      }
      jdbc.update("DELETE FROM alert_outbox WHERE message_id=?", id);
      jdbc.update("DELETE FROM home_change_event WHERE event_key=?", "alert:" + id);
      jdbc.update("DELETE FROM alert_event WHERE message_id=?", id);
      jdbc.update("DELETE FROM alert_cooldown WHERE device_key=?", id);
    }
  }

  void awaitReceipt(String id) throws Exception {
    for (int i = 0; i < 60; i++) {
      if (count(id) == 1) return;
      Thread.sleep(50);
    }
    fail("Receipt did not commit");
  }

  @Test
  void alertDeliveryIsTransactionalAndFilteredReceiptCommits() throws Exception {
    String id = "mail-it-" + UUID.randomUUID();
    ownIds.add(id);
    var alert =
        AlertEvent.builder()
            .messageId(id)
            .deviceKey("mail-it")
            .ruleCode("FIRE_RISK")
            .scene("FIRE")
            .level(AlertLevel.CRITICAL)
            .title("测试火灾隐患")
            .content("仅测试")
            .occurredAt(clock.millis())
            .build();
    try (var unused = new java.net.ServerSocket(0)) {
      sender.setPort(unused.getLocalPort());
    }
    assertThrows(MailSendException.class, () -> delivery.handle(alert));
    assertEquals(0, count(id));
    sender.setPort(smtp.getSmtp().getPort());
    delivery.handle(alert);
    delivery.handle(alert);
    assertTrue(smtp.waitForIncomingEmail(2000, 1));
    assertEquals(1, count(id));
    String filtered = "mail-it-" + UUID.randomUUID();
    ownIds.add(filtered);
    alert.setMessageId(filtered);
    alert.setRuleCode("FILTERED");
    alert.setLevel(AlertLevel.INFO);
    delivery.handle(alert);
    assertEquals(1, count(filtered));
    assertEquals(1, smtp.getReceivedMessages().length);
  }

  @Test
  void manualEmailUsesFixedRecipientAndRejectsOversizedBody() throws Exception {
    String key = UUID.randomUUID().toString();
    String id = "manual-email:" + key;
    ownIds.add(id);
    var client = HttpClient.newHttpClient();
    var body =
        Map.of(
            "subject",
            "手动测试",
            "body",
            "真实投递",
            "idempotencyKey",
            key,
            "to",
            "attacker@example.test");
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/notifications/email"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build();
    assertEquals(200, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    assertTrue(smtp.waitForIncomingEmail(2000, 1));
    assertEquals(
        "receiver@example.test", smtp.getReceivedMessages()[0].getAllRecipients()[0].toString());
    assertEquals(200, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    assertEquals(1, smtp.getReceivedMessages().length);
    var oversized =
        HttpRequest.newBuilder(request.uri())
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    json.writeValueAsString(Map.of("subject", "test", "body", "x".repeat(4001)))))
            .build();
    assertEquals(400, client.send(oversized, HttpResponse.BodyHandlers.discarding()).statusCode());
  }
}
