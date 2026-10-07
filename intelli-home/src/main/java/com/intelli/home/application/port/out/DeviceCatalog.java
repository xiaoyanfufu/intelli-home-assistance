package com.intelli.home.application.port.out;

import com.intelli.home.domain.home.*;
import java.util.List;

public interface DeviceCatalog {
  void saveModel(DeviceModel model);

  DeviceModel model(String key, int version);

  void register(HomeDevice device);

  HomeDevice device(String key);

  List<HomeDevice> devices();
}
