package com.intelli.home.adapter.in.http;

import com.intelli.home.application.port.out.DeviceCatalog;
import com.intelli.home.application.service.*;
import com.intelli.home.domain.home.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DeviceCatalogController {
  private final DeviceCatalog catalog;
  private final DeviceCatalogService devices;
  private final TelemetryHistoryService history;

  @GetMapping("/devices")
  public Object list() {
    return catalog.devices();
  }

  @PostMapping("/devices")
  public HomeDevice register(@RequestBody HomeDevice device) {
    devices.register(device);
    return devices.require(device.deviceKey());
  }

  @GetMapping("/devices/{key}")
  public HomeDevice device(@PathVariable String key) {
    return devices.require(key);
  }

  @GetMapping("/devices/{key}/capabilities")
  public DeviceModel capabilities(@PathVariable String key) {
    return devices.capabilities(key);
  }

  @GetMapping("/device-models/{key}/versions/{version}")
  public DeviceModel model(@PathVariable String key, @PathVariable int version) {
    return devices.requireModel(key, version);
  }

  @GetMapping("/devices/{key}/telemetry")
  public Object telemetry(
      @PathVariable String key,
      @RequestParam long from,
      @RequestParam long to,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "100") int limit) {
    return history.query(key, from, to, cursor, limit);
  }

  @GetMapping("/devices/{key}/history")
  public Object history(
      @PathVariable String key,
      @RequestParam String property,
      @RequestParam long from,
      @RequestParam long to,
      @RequestParam(defaultValue = "raw") String interval,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "100") int limit) {
    return history.trend(key, property, from, to, interval, cursor, limit);
  }
}
