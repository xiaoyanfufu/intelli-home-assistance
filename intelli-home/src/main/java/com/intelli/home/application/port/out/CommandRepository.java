package com.intelli.home.application.port.out;

import com.intelli.home.domain.home.*;
import java.util.List;
import java.util.Map;

public interface CommandRepository {
  void create(DeviceCommand command);

  DeviceCommand find(String id);

  DeviceCommand findRequest(String deviceKey, String key);

  DeviceCommand lock(String id);

  List<DeviceCommand> list(String deviceKey);

  List<DeviceCommand> pending();

  List<DeviceCommand> overdue(long now);

  boolean update(DeviceCommand command, long expected);

  void markPublished(String id);

  void failure(String id, String error);

  Map<String, Object> executeMockOnce(String id, String deviceKey, Map<String, Object> result);
}
