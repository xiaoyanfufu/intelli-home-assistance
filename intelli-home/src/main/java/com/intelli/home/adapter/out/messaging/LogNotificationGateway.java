package com.intelli.home.adapter.out.messaging;

import com.intelli.home.application.port.out.NotificationGateway;
import com.intelli.home.domain.alert.AlertEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    prefix = "intelli.notification",
    name = "channel",
    havingValue = "log",
    matchIfMissing = true)
@Slf4j
public class LogNotificationGateway implements NotificationGateway {
  public void send(AlertEvent alert) {
    log.info(
        "NOTIFICATION alertId={} device={} rule={} title={} content={}",
        alert.getMessageId(),
        alert.getDeviceKey(),
        alert.getRuleCode(),
        alert.getTitle(),
        alert.getContent());
  }
}
