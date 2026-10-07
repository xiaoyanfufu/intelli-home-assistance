package com.intelli.home.adapter.out.persistence;

import com.intelli.home.adapter.out.persistence.mapper.NotificationReceiptMapper;
import com.intelli.home.application.port.out.NotificationReceiptStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisNotificationReceiptStore implements NotificationReceiptStore {
  private final NotificationReceiptMapper receipts;

  public boolean claim(String messageId) {
    return receipts.claim(messageId) > 0;
  }

  public boolean exists(String messageId) {
    return receipts.exists(messageId);
  }
}
