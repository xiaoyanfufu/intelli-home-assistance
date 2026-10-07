package com.intelli.home.application.service;

import com.intelli.home.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StateRecoveryService {
  private final DeviceRegistry registry;
  private final DeviceStateStore states;

  @EventListener(ApplicationReadyEvent.class)
  public void restore() {
    registry.snapshots().forEach(states::restore);
  }
}
