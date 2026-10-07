package com.intelli.home.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.home.adapter.out.persistence.mapper.RuleConfigMapper;
import com.intelli.home.application.port.out.RuleConfigRepository;
import com.intelli.home.domain.rule.RuleDefinition;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisRuleConfigRepository implements RuleConfigRepository {
  private final RuleConfigMapper rules;
  private final ObjectMapper json;

  public Map<String, RuleDefinition> load() {
    var definitions = new HashMap<String, RuleDefinition>();
    for (var row : rules.findAll()) {
      String code = row.getRuleCode();
      try {
        var params = new HashMap<String, Double>();
        if (row.getParamsJson() != null) {
          var node = json.readTree(row.getParamsJson());
          if (!node.isObject())
            throw new IllegalArgumentException("Rule parameters must be an object");
          node.fields()
              .forEachRemaining(
                  entry -> {
                    if (!entry.getValue().isNumber()
                        || !Double.isFinite(entry.getValue().asDouble())
                        || entry.getValue().asDouble() < 0)
                      throw new IllegalArgumentException(
                          "Invalid rule threshold: " + entry.getKey());
                    params.put(entry.getKey(), entry.getValue().asDouble());
                  });
        }
        definitions.put(code, new RuleDefinition(code, row.isEnabled(), row.getPriority(), params));
      } catch (Exception e) {
        throw new IllegalStateException("Invalid rule configuration: " + code, e);
      }
    }
    if (!definitions.containsKey("FIRE_RISK") || !definitions.containsKey("DEVICE_OFFLINE"))
      throw new IllegalStateException("Required safety rule configuration missing");
    return Map.copyOf(definitions);
  }
}
