package com.intelli.home.adapter.in.http;

import com.intelli.home.application.port.in.DeviceEventHandler;
import com.intelli.home.application.port.out.DeviceStateStore;
import com.intelli.home.domain.device.*;
import com.intelli.home.domain.event.DeviceEvent;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mock")
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "intelli.mock",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MockDeviceController {
  private final MockEventFactory factory;
  private final DeviceEventHandler handler;
  private final DeviceStateStore states;

  @PostMapping("/devices/{deviceKey}/events")
  public DeviceEvent push(
      @PathVariable String deviceKey,
      @RequestParam(defaultValue = "INDOOR") Location location,
      @RequestBody Map<String, Object> properties) {
    var event = factory.telemetry(deviceKey, location, properties);
    handler.handle(event);
    return event;
  }

  @PostMapping("/scenes/{scene}")
  public Map<String, Object> scene(@PathVariable String scene) {
    var events = factory.scene(scene.toUpperCase(Locale.ROOT));
    events.forEach(handler::handle);
    return Map.of("scene", scene, "processed", true, "events", events.size());
  }

  @GetMapping("/states")
  public Map<String, DeviceSnapshot> all() {
    return states.snapshots();
  }

  @GetMapping("/devices/{deviceKey}/state")
  public Map<String, Object> one(@PathVariable String deviceKey) {
    var snapshot = states.snapshots().get(deviceKey);
    return snapshot == null ? Map.of() : snapshot.properties();
  }
}
