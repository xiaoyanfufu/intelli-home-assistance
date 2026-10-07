package com.intelli.home.application.service;

import com.intelli.home.application.port.out.*;
import com.intelli.home.domain.event.DeviceEvent;
import com.intelli.home.domain.home.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceCatalogService {
  private final DeviceCatalog catalog;
  private final DeviceRegistry registry;

  @EventListener(ApplicationReadyEvent.class)
  public void initialize() {
    var attrs = new LinkedHashMap<String, DeviceModel.Property>();
    attrs.put(
        "temperature",
        new DeviceModel.Property("温度", "number", "℃", -80.0, 150.0, Map.of(), true, true));
    attrs.put(
        "humidity",
        new DeviceModel.Property("相对湿度", "number", "%", 0.0, 100.0, Map.of(), true, true));
    attrs.put(
        "smoke",
        new DeviceModel.Property("模拟烟雾读数", "number", null, 0.0, null, Map.of(), true, false));
    var sensor = new DeviceModel("environment", 1, "环境传感器", Map.copyOf(attrs), Map.of());
    catalog.saveModel(sensor);
    var controlAttrs = new LinkedHashMap<>(attrs);
    controlAttrs.put(
        "power",
        new DeviceModel.Property("电源", "boolean", null, null, null, Map.of(), false, true));
    var power = new DeviceModel.Property("电源", "boolean", null, null, null, Map.of(), false, true);
    catalog.saveModel(
        new DeviceModel(
            "mock-switch",
            1,
            "模拟开关",
            Map.copyOf(controlAttrs),
            Map.of("setPower", new DeviceModel.Action("设置电源", Map.of("power", power), 30, true))));
    registry
        .snapshots()
        .forEach(
            s ->
                catalog.register(
                    new HomeDevice(
                        s.deviceKey(),
                        s.deviceKey(),
                        "environment",
                        1,
                        "LEGACY",
                        s.location().name(),
                        System.currentTimeMillis())));
  }

  public HomeDevice require(String key) {
    var device = catalog.device(key);
    if (device == null) throw new IllegalArgumentException("Unknown device: " + key);
    return device;
  }

  public DeviceModel capabilities(String key) {
    var device = require(key);
    return requireModel(device.modelKey(), device.modelVersion());
  }

  public DeviceModel requireModel(String key, int version) {
    var model = catalog.model(key, version);
    if (model == null) throw new IllegalArgumentException("Unknown model version");
    return model;
  }

  public void register(HomeDevice device) {
    if (device == null
        || device.deviceKey() == null
        || !device.deviceKey().matches("[a-zA-Z0-9_-]{1,64}")
        || device.displayName() == null
        || device.displayName().isBlank()
        || device.displayName().length() > 128)
      throw new IllegalArgumentException("Invalid device");
    requireModel(device.modelKey(), device.modelVersion());
    if (!Set.of("MOCK", "MQTT").contains(device.source())
        || !Set.of("INDOOR", "BALCONY").contains(device.location()))
      throw new IllegalArgumentException("Invalid device source/location");
    if (catalog.device(device.deviceKey()) != null)
      throw new IllegalArgumentException("Device already registered");
    catalog.register(
        new HomeDevice(
            device.deviceKey(),
            device.displayName(),
            device.modelKey(),
            device.modelVersion(),
            device.source(),
            device.location(),
            System.currentTimeMillis()));
  }

  public HomeDevice normalize(DeviceEvent event) {
    var device = catalog.device(event.getDeviceKey());
    if (device == null)
      device =
          new HomeDevice(
              event.getDeviceKey(),
              event.getDeviceKey(),
              "environment",
              1,
              event.getSource().name(),
              event.getLocation().name(),
              System.currentTimeMillis());
    if (!device.location().equals(event.getLocation().name()))
      throw new IllegalArgumentException("Device location mismatch");
    var model = requireModel(device.modelKey(), device.modelVersion());
    var normalized = new HashMap<String, Object>(event.getProperties());
    model
        .properties()
        .forEach(
            (key, property) -> {
              if (normalized.containsKey(key))
                normalized.put(key, validateValue(property, normalized.get(key)));
            });
    event.setProperties(normalized);
    catalog.register(device);
    return require(event.getDeviceKey());
  }

  public Object validateValue(DeviceModel.Property spec, Object value) {
    if ("number".equals(spec.type())) {
      double n;
      try {
        n = Double.parseDouble(String.valueOf(value));
      } catch (Exception e) {
        throw new IllegalArgumentException("Expected number");
      }
      if (!Double.isFinite(n)
          || (spec.minimum() != null && n < spec.minimum())
          || (spec.maximum() != null && n > spec.maximum()))
        throw new IllegalArgumentException("Property out of range");
      return value instanceof Number ? value : n;
    }
    if ("boolean".equals(spec.type()) && value instanceof Boolean) return value;
    if ("string".equals(spec.type()) && value instanceof String s && s.length() <= 255)
      return value;
    throw new IllegalArgumentException("Invalid property type: " + spec.type());
  }
}
