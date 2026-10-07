package com.intelli.home.adapter.in.http;

import com.intelli.home.application.port.out.*;
import com.intelli.home.domain.alert.AlertEvent;
import com.intelli.home.domain.device.DeviceSnapshot;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class QueryController {
  private final AlertRepository alerts;
  private final DeviceStateStore states;

  @GetMapping("/alerts")
  public List<AlertEvent> alerts(@RequestParam(defaultValue = "20") int limit) {
    return alerts.recent(limit);
  }

  @GetMapping("/devices/states")
  public Map<String, DeviceSnapshot> states() {
    return states.snapshots();
  }
}
