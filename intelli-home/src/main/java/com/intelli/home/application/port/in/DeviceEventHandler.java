package com.intelli.home.application.port.in;

import com.intelli.home.domain.event.DeviceEvent;

/** Synchronous processing. Returns only after state and alert persistence; throws on failure. */
public interface DeviceEventHandler {
  void handle(DeviceEvent event);
}
