package com.intelli.home.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.home.adapter.out.persistence.mapper.OutboxMapper;
import com.intelli.home.config.RabbitConfig;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {
  private final OutboxMapper outbox;
  private final RabbitTemplate rabbit;
  private final ObjectMapper mapper;

  @Scheduled(fixedDelay = 3000, initialDelay = 3000)
  public void publishPending() {
    try {
      for (var row : outbox.findPending(50)) {
        String id = row.getMessageId();
        try {
          var message = mapper.readValue(row.getPayload(), AlertEventMessage.class);
          var correlation = new CorrelationData(id);
          rabbit.convertAndSend(
              RabbitConfig.ALERT_EXCHANGE, RabbitConfig.ALERT_ROUTING_KEY, message, correlation);
          var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
          if (!confirm.isAck() || correlation.getReturned() != null)
            throw new IllegalStateException("Broker did not confirm routed message");
          outbox.markPublished(id);
        } catch (Exception e) {
          if (e instanceof InterruptedException) Thread.currentThread().interrupt();
          outbox.recordFailure(id, e.getClass().getSimpleName());
          log.warn("Outbox publish failed alertId={}", id, e);
          break; // Back off until next tick instead of hammering an unavailable broker.
        }
      }
    } catch (Exception e) {
      log.error("Outbox polling failed", e);
    }
  }
}
