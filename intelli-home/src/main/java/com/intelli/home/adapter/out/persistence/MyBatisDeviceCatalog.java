package com.intelli.home.adapter.out.persistence;

import com.intelli.home.adapter.out.persistence.mapper.HomeDataMapper;
import com.intelli.home.application.port.out.DeviceCatalog;
import com.intelli.home.domain.home.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisDeviceCatalog implements DeviceCatalog {
  private final HomeDataMapper mapper;
  private final JsonRows json;

  public void saveModel(DeviceModel model) {
    mapper.insertModel(model.modelKey(), model.version(), json.write(model));
  }

  public DeviceModel model(String key, int version) {
    return json.read(mapper.model(key, version), DeviceModel.class);
  }

  public void register(HomeDevice device) {
    mapper.register(device);
  }

  public HomeDevice device(String key) {
    return mapper.device(key);
  }

  public List<HomeDevice> devices() {
    return mapper.devices();
  }
}
