package com.intelli.home.application.port.out;

import com.intelli.home.domain.device.DeviceSnapshot;
import com.intelli.home.domain.event.DeviceEvent;
import java.util.Map;

public interface DeviceStateStore {
  /** Reject older event timestamps; duplicate timestamps can be reprocessed for durable retry. */
  boolean update(DeviceEvent event);

  void restore(com.intelli.home.domain.device.DeviceSnapshot snapshot);

  Map<String, DeviceSnapshot> snapshots();
}
