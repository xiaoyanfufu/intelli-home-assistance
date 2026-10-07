package com.intelli.home.application.service;

import com.intelli.home.application.port.out.RuleConfigRepository;
import com.intelli.home.domain.rule.RuleDefinition;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RuleConfigService {
  private final RuleConfigRepository repository;
  private final AtomicReference<com.intelli.home.application.model.RuleConfigSnapshot> snapshot =
      new AtomicReference<>();

  public RuleConfigService(RuleConfigRepository repository) {
    this.repository = repository;
  }

  @PostConstruct
  public void initialize() {
    snapshot.set(
        new com.intelli.home.application.model.RuleConfigSnapshot(
            1, System.currentTimeMillis(), repository.load()));
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 60000)
  public void refresh() {
    try {
      var loaded = repository.load();
      snapshot.updateAndGet(
          old ->
              new com.intelli.home.application.model.RuleConfigSnapshot(
                  old.generation() + 1, System.currentTimeMillis(), loaded));
    } catch (Exception e) {
      log.warn("Rule refresh failed; preserving snapshot", e);
    }
  }

  public Map<String, RuleDefinition> snapshot() {
    return snapshot.get().definitions();
  }

  public com.intelli.home.application.model.RuleConfigSnapshot current() {
    return snapshot.get();
  }
}
