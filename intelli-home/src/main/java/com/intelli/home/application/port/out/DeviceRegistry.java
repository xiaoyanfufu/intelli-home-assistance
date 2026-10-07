package com.intelli.home.application.port.out;

import com.intelli.home.domain.device.DeviceSnapshot;
import com.intelli.home.domain.event.DeviceEvent;
import java.util.List;

public interface DeviceRegistry {
  void record(DeviceEvent event);

  List<DeviceSnapshot> snapshots();
}
