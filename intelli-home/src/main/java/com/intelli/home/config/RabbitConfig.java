package com.intelli.home.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.*;
import org.springframework.context.annotation.*;

@Configuration
public class RabbitConfig {
  @Bean
  org.springframework.amqp.rabbit.retry.MessageRecoverer exhaustedMessageRecoverer() {
    return (message, cause) -> {
      // Explicit rejection is required even when the listener uses manual acknowledgement.
      throw new org.springframework.amqp.AmqpRejectAndDontRequeueException(
          "Notification retries exhausted", true, cause);
    };
  }

  @Bean
  org.springframework.boot.autoconfigure.amqp.ConnectionFactoryCustomizer rabbitTimeouts() {
    return factory -> {
      factory.setChannelRpcTimeout(3000);
      factory.setHandshakeTimeout(3000);
      factory.setConnectionTimeout(3000);
    };
  }

  public static final String ALERT_EXCHANGE = "intelli.alert.exchange",
      ALERT_QUEUE = "intelli.alert.queue",
      ALERT_ROUTING_KEY = "alert.notify";
  public static final String ALERT_DLQ_EXCHANGE = "intelli.alert.dlx",
      ALERT_DLQ = "intelli.alert.dlq",
      ALERT_DLQ_ROUTING_KEY = "alert.notify.dead";

  @Bean
  DirectExchange alertExchange() {
    return new DirectExchange(ALERT_EXCHANGE, true, false);
  }

  @Bean
  Queue alertQueue() {
    return QueueBuilder.durable(ALERT_QUEUE)
        .deadLetterExchange(ALERT_DLQ_EXCHANGE)
        .deadLetterRoutingKey(ALERT_DLQ_ROUTING_KEY)
        .build();
  }

  @Bean
  Binding alertBinding() {
    return BindingBuilder.bind(alertQueue()).to(alertExchange()).with(ALERT_ROUTING_KEY);
  }

  @Bean
  DirectExchange alertDlqExchange() {
    return new DirectExchange(ALERT_DLQ_EXCHANGE, true, false);
  }

  @Bean
  Queue alertDlq() {
    return QueueBuilder.durable(ALERT_DLQ).build();
  }

  @Bean
  Binding alertDlqBinding() {
    return BindingBuilder.bind(alertDlq()).to(alertDlqExchange()).with(ALERT_DLQ_ROUTING_KEY);
  }

  @Bean
  MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
    var template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(converter);
    template.setMandatory(true);
    return template;
  }
}
