package com.intelli.home.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.intelli.home.application.model.*;
import com.intelli.home.application.port.in.AlertNotificationHandler;
import com.intelli.home.application.port.out.*;
import com.intelli.home.application.service.*;
import com.intelli.home.config.IntelliProperties;
import com.intelli.home.domain.alert.*;
import com.intelli.home.domain.home.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class DailyAdviceTest {
  final TelemetryHistory history = mock(TelemetryHistory.class);
  final DailyAdviceHistorySummarizer summarizer = new DailyAdviceHistorySummarizer(history);
  final HomeDevice device = new HomeDevice("device", "test", "environment", 1, "MOCK", "INDOOR", 0);
  final IntelliProperties.DailyAdvice settings = new IntelliProperties.DailyAdvice();
  final IntelliProperties.Notification notification = new IntelliProperties.Notification();
  final DeviceStateStore states = mock(DeviceStateStore.class);
  final DeviceCatalog catalog = mock(DeviceCatalog.class);
  final AlertRepository alerts = mock(AlertRepository.class);
  final NotificationReceiptStore receipts = mock(NotificationReceiptStore.class);
  final AgentGateway agent = mock(AgentGateway.class);
  final AlertNotificationHandler delivery = mock(AlertNotificationHandler.class);
  final Clock clock = Clock.fixed(Instant.parse("2026-10-08T23:30:00Z"), ZoneOffset.UTC);
  DailyAdviceService service;

  @BeforeEach
  void setup() {
    notification.setChannel("email");
    notification.getEmail().setEnabled(true);
    when(catalog.devices()).thenReturn(List.of());
    when(states.snapshots()).thenReturn(Map.of());
    when(alerts.recent(100)).thenReturn(List.of());
    when(agent.dailyAdvice(any())).thenReturn(new DailyAdviceResult("真实建议", Map.of(), false, null));
    service =
        new DailyAdviceService(
            settings,
            notification,
            states,
            catalog,
            alerts,
            receipts,
            agent,
            delivery,
            summarizer,
            clock);
  }

  TelemetrySample sample(String id, long time, Map<String, Object> values) {
    return new TelemetrySample(id, "device", "environment", 1, time, time, values);
  }

  @Test
  void numericStringsAndInvalidValuesAggregateWithCompositePaging() {
    when(history.query(eq("device"), eq(0L), eq(10L), anyLong(), anyString(), anyInt()))
        .thenReturn(
            List.of(
                sample("a", 1, Map.of("t", "26.5", "text", "bad", "flag", true)),
                sample("b", 1, Map.of("t", 30, "nan", "NaN")),
                sample("c", 2, Map.of("t", 20))));
    var result = summarizer.summarize(device, 0, 10, 3);
    assertEquals(3, result.get("sampleCount"));
    var props = (Map<?, ?>) result.get("properties");
    assertEquals(Set.of("t"), props.keySet());
    var t = (Map<?, ?>) props.get("t");
    assertEquals(20.0, t.get("min"));
    assertEquals(30.0, t.get("max"));
    assertEquals(25.5, t.get("avg"));
    assertEquals(20.0, t.get("last"));
    assertEquals(3, t.get("count"));
  }

  @Test
  void fullPageAdvancesSameTimestampCompositeCursor() {
    var page = new ArrayList<TelemetrySample>();
    for (int i = 0; i < 500; i++) page.add(sample(String.format("%04d", i), 1, Map.of("t", i)));
    when(history.query(eq("device"), eq(0L), eq(10L), eq(-1L), eq(""), eq(500))).thenReturn(page);
    when(history.query(eq("device"), eq(0L), eq(10L), eq(1L), eq("0499"), eq(2)))
        .thenReturn(List.of(sample("0500", 1, Map.of("t", 500))));
    var result = summarizer.summarize(device, 0, 10, 501);
    assertEquals(501, result.get("sampleCount"));
    assertEquals(false, result.get("truncated"));
    verify(history).query("device", 0, 10, 1, "0499", 2);
  }

  @Test
  void capAndCoverageAreExplicit() {
    when(history.query(anyString(), anyLong(), anyLong(), anyLong(), anyString(), anyInt()))
        .thenReturn(
            List.of(
                sample("a", 1, Map.of("t", 1)),
                sample("b", 2, Map.of("t", 2)),
                sample("c", 3, Map.of("t", 3))));
    var result = summarizer.summarize(device, 0, 10, 2);
    assertEquals(2, result.get("sampleCount"));
    assertEquals(true, result.get("truncated"));
    assertEquals(1L, result.get("coveredFrom"));
    assertEquals(2L, result.get("coveredTo"));
  }

  @Test
  void brokenCursorFailsInsteadOfLooping() {
    var page = new ArrayList<TelemetrySample>();
    for (int i = 0; i < 500; i++) page.add(sample("id", i, Map.of()));
    when(history.query(anyString(), anyLong(), anyLong(), anyLong(), anyString(), anyInt()))
        .thenReturn(page);
    assertThrows(IllegalStateException.class, () -> summarizer.summarize(device, 0, 1000, 20000));
  }

  @Test
  void normalDeliveryUsesConfiguredZoneAndInfoRule() {
    assertEquals("SENT", service.run(false).get("status"));
    var captor = ArgumentCaptor.forClass(AlertEvent.class);
    verify(delivery).handle(captor.capture());
    assertEquals("daily-advice:2026-10-09", captor.getValue().getMessageId());
    assertEquals("DAILY_DIGEST", captor.getValue().getRuleCode());
    assertEquals(AlertLevel.INFO, captor.getValue().getLevel());
  }

  @Test
  void committedReceiptSkipsAgentButDryRunStillGenerates() {
    when(receipts.exists(anyString())).thenReturn(true);
    assertEquals("SKIPPED_ALREADY_SENT", service.run(false).get("status"));
    verifyNoInteractions(agent, delivery);
    assertEquals("DRY_RUN", service.run(true).get("status"));
    verify(agent).dailyAdvice(any());
    verifyNoInteractions(delivery);
    verify(receipts, never()).claim(anyString());
  }

  @Test
  void unavailableAndBlankAgentDoNotDeliver() {
    when(agent.dailyAdvice(any())).thenThrow(new IllegalStateException());
    assertEquals("FAILED", service.run(false).get("status"));
    doReturn(new DailyAdviceResult(" ", Map.of(), false, null)).when(agent).dailyAdvice(any());
    assertEquals("FAILED", service.run(false).get("status"));
    verifyNoInteractions(delivery);
    verify(receipts, never()).claim(anyString());
  }

  @Test
  void degradedAdviceIsDeliveredWithNotice() {
    when(agent.dailyAdvice(any()))
        .thenReturn(new DailyAdviceResult("真实数据", Map.of(), true, "LLM_UNAVAILABLE"));
    service.run(false);
    verify(delivery)
        .handle(argThat(a -> a.getContent().contains("降级模式") && a.getContent().contains("真实数据")));
  }

  @Test
  void mailFailureLeavesNextRunAvailable() {
    doThrow(new IllegalStateException()).doNothing().when(delivery).handle(any());
    assertEquals("FAILED", service.run(false).get("status"));
    assertEquals("SENT", service.run(false).get("status"));
  }

  @Test
  void logAndFilteredEmailCannotConsumeDailyReceipt() {
    notification.setChannel("log");
    assertEquals("DELIVERY_NOT_CONFIGURED", service.run(false).get("status"));
    notification.setChannel("email");
    notification.getEmail().setMinLevelExemptRuleCodes(Set.of());
    assertEquals("DELIVERY_NOT_CONFIGURED", service.run(false).get("status"));
    verifyNoInteractions(agent, delivery);
  }

  @Test
  void alertWindowAndLimitAreReported() {
    var list = new ArrayList<AlertEvent>();
    for (int i = 0; i < 100; i++)
      list.add(
          AlertEvent.builder()
              .occurredAt(clock.millis() - (i == 0 ? 100000000L : 1))
              .level(AlertLevel.WARN)
              .title("test")
              .build());
    when(alerts.recent(100)).thenReturn(list);
    service.run(true);
    var captor = ArgumentCaptor.forClass(DailyAdviceRequest.class);
    verify(agent).dailyAdvice(captor.capture());
    assertEquals(99, captor.getValue().alertSummary().get("total"));
    assertEquals(true, captor.getValue().alertSummary().get("truncated"));
  }

  @Test
  void concurrentManualCallDoesNotDuplicateGeneration() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(agent.dailyAdvice(any()))
        .thenAnswer(
            i -> {
              entered.countDown();
              assertTrue(release.await(3, TimeUnit.SECONDS));
              return new DailyAdviceResult("test", Map.of(), false, null);
            });
    try (var executor = Executors.newSingleThreadExecutor()) {
      var first = executor.submit(() -> service.run(false));
      try {
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        assertEquals("SKIPPED_RUNNING", service.run(false).get("status"));
      } finally {
        release.countDown();
      }
      assertEquals("SENT", first.get(3, TimeUnit.SECONDS).get("status"));
    }
    verify(agent, times(1)).dailyAdvice(any());
  }
}
