package com.intelli.home.application.port.in;

import com.intelli.home.domain.alert.AlertEvent;

public interface AlertNotificationHandler {
  void handle(AlertEvent alert);
}
