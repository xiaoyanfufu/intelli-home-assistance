package com.intelli.home.application.port.out;

public interface NotificationReceiptStore {
  /** Must be called within the notification transaction; returns false for a committed receipt. */
  boolean claim(String messageId);

  boolean exists(String messageId);
}
