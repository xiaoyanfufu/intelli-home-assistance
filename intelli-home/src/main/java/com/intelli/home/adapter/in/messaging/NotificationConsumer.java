package com.intelli.home.adapter.in.messaging;

import com.intelli.home.adapter.out.messaging.AlertEventMessage;
import com.intelli.home.application.port.in.AlertNotificationHandler;
import com.intelli.home.config.RabbitConfig;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationConsumer {
  private final AlertNotificationHandler delivery;

  @RabbitListener(
      queues = RabbitConfig.ALERT_QUEUE,
      autoStartup = "${intelli.consumer.enabled:true}")
  public void onAlert(
      AlertEventMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag)
      throws IOException {
    if (message == null
        || message.getPayload() == null
        || message.getMessageId() == null
        || !message.getMessageId().equals(message.getPayload().getMessageId())) {
      channel.basicReject(tag, false);
      return;
    }
    // Exceptions escape to bounded container retry; success commits before manual acknowledgement.
    delivery.handle(message.getPayload());
    channel.basicAck(tag, false);
  }
}
