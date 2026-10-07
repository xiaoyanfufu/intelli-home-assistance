package com.intelli.home.application.port.out;

import com.intelli.home.domain.rule.RuleDefinition;
import java.util.Map;

public interface RuleConfigRepository {
  Map<String, RuleDefinition> load();
}
