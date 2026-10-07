package com.intelli.home.application.service;

import com.intelli.home.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommandDispatchService {
  private final CommandRepository repository;
  private final CommandGateway gateway;
  private final CommandService service;

  @Scheduled(fixedDelay = 1000, initialDelay = 1000)
  public void dispatch() {
    for (var command : repository.pending()) {
      if (command.expiresAt() <= System.currentTimeMillis()) {
        service.timeout(command.id(), System.currentTimeMillis());
        continue;
      }
      try {
        gateway.publish(command);
        service.published(command.id());
      } catch (Exception e) {
        repository.failure(command.id(), e.getClass().getSimpleName());
        break;
      }
    }
    for (var command : repository.overdue(System.currentTimeMillis()))
      service.timeout(command.id(), System.currentTimeMillis());
  }
}
