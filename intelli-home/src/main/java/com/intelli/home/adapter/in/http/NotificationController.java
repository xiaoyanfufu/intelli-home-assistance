package com.intelli.home.adapter.in.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.intelli.home.application.port.in.AlertNotificationHandler;
import com.intelli.home.application.port.out.NotificationReceiptStore;
import com.intelli.home.config.IntelliProperties;
import com.intelli.home.domain.alert.*;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "intelli.notification.api", name = "enabled", havingValue = "true")
public class NotificationController {
  private final AlertNotificationHandler delivery;
  private final NotificationReceiptStore receipts;
  private final IntelliProperties.Notification settings;

  @JsonIgnoreProperties(ignoreUnknown = false)
  public record EmailRequest(String subject, String body, String idempotencyKey) {}

  @PostMapping("/api/notifications/email")
  public Map<String, Object> email(@RequestBody EmailRequest request) {
    if (request.subject() == null
        || request.subject().isBlank()
        || request.subject().length() > 160
        || request.subject().contains("\r")
        || request.subject().contains("\n")
        || request.body() == null
        || request.body().isBlank()
        || request.body().length() > settings.getApi().getMaxBodyLength())
      throw new IllegalArgumentException("Invalid email subject or body");
    String key =
        request.idempotencyKey() == null ? UUID.randomUUID().toString() : request.idempotencyKey();
    if (!key.matches("[A-Za-z0-9_-]{1,51}"))
      throw new IllegalArgumentException("Invalid idempotency key");
    String id = "manual-email:" + key;
    if (receipts.exists(id)) return Map.of("status", "SKIPPED_ALREADY_SENT", "messageId", id);
    if (!"email".equals(settings.getChannel())
        || !settings.getEmail().isEnabled()
        || (settings.getEmail().getMinLevel() != AlertLevel.INFO
            && !settings.getEmail().getMinLevelExemptRuleCodes().contains("MANUAL_EMAIL")))
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Email delivery not configured");
    delivery.handle(
        AlertEvent.builder()
            .messageId(id)
            .deviceKey("home")
            .ruleCode("MANUAL_EMAIL")
            .scene("MANUAL")
            .level(AlertLevel.INFO)
            .title(request.subject())
            .content(request.body())
            .occurredAt(System.currentTimeMillis())
            .build());
    return Map.of("status", "SENT", "messageId", id);
  }
}
