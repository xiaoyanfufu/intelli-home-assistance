package com.intelli.home.application.service;

import com.intelli.home.application.port.in.AlertNotificationHandler;
import com.intelli.home.application.port.out.NotificationGateway;
import com.intelli.home.application.port.out.NotificationReceiptStore;
import com.intelli.home.domain.alert.AlertEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService implements AlertNotificationHandler {
  private final NotificationReceiptStore receipts;
  private final NotificationGateway notifications;

  @Transactional
  public void handle(AlertEvent alert) {
    if (receipts.claim(alert.getMessageId())) notifications.send(alert);
  }
}
