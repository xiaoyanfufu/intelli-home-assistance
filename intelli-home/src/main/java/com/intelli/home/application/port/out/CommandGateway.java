package com.intelli.home.application.port.out;

import com.intelli.home.domain.home.DeviceCommand;

public interface CommandGateway {
  void publish(DeviceCommand command);
}
