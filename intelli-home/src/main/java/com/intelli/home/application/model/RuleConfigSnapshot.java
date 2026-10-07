package com.intelli.home.application.model;

import com.intelli.home.domain.rule.RuleDefinition;
import java.util.Map;

public record RuleConfigSnapshot(
    long generation, long loadedAt, Map<String, RuleDefinition> definitions) {
  public RuleConfigSnapshot {
    definitions = Map.copyOf(definitions);
  }
}
