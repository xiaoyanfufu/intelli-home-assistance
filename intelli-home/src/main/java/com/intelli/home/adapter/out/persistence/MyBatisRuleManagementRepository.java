package com.intelli.home.adapter.out.persistence;

import static com.intelli.home.adapter.out.persistence.JsonRows.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.home.adapter.out.persistence.mapper.RuleManagementMapper;
import com.intelli.home.application.port.out.RuleManagementRepository;
import com.intelli.home.domain.home.ManagedRule;
import com.intelli.home.domain.rule.RuleDefinition;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisRuleManagementRepository implements RuleManagementRepository {
  private final RuleManagementMapper mapper;
  private final JsonRows json;
  private final ObjectMapper objectMapper;

  public ManagedRule find(String code) {
    var row = mapper.find(code);
    if (row == null) return null;
    try {
      Map<String, Double> params =
          objectMapper.readValue(
              text(row, "params_json"), new TypeReference<Map<String, Double>>() {});
      Object enabled = row.get("enabled");
      return new ManagedRule(
          number(row, "revision"),
          new RuleDefinition(
              code,
              enabled instanceof Boolean b ? b : ((Number) enabled).intValue() != 0,
              (int) number(row, "priority"),
              params));
    } catch (Exception e) {
      throw new IllegalStateException("Invalid stored rule", e);
    }
  }

  public boolean update(String code, long expected, ManagedRule rule) {
    var definition = rule.definition();
    return mapper.update(
            code,
            expected,
            definition.enabled(),
            definition.priority(),
            json.write(definition.params()))
        == 1;
  }

  public void recordRevision(ManagedRule rule) {
    mapper.recordRevision(
        rule.definition().code(), rule.revision(), json.write(rule), System.currentTimeMillis());
  }

  public List<ManagedRule> revisions(String code) {
    return mapper.revisions(code).stream().map(s -> json.read(s, ManagedRule.class)).toList();
  }
}
