package com.intelli.home.application.port.out;

import com.intelli.home.domain.alert.AlertEvent;

public interface NotificationGateway {
  void send(AlertEvent alert);
}
