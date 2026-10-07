package com.intelli.home.application.service;

import com.intelli.home.application.port.out.*;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RealtimeService {
  private final ChangeFeed changes;
  private final DeviceRegistry registry;

  @Transactional(readOnly = true)
  public Map<String, Object> baseline() {
    long cursor = changes.latest();
    return Map.of("cursor", cursor, "devices", registry.snapshots());
  }
}
