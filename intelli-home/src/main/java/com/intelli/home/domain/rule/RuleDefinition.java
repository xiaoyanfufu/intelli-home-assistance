package com.intelli.home.domain.rule;

import java.util.Map;

public record RuleDefinition(
    String code, boolean enabled, int priority, Map<String, Double> params) {
  public RuleDefinition {
    params = Map.copyOf(params);
  }
}
