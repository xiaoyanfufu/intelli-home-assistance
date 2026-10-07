package com.intelli.home.domain.rule;

import com.intelli.home.domain.alert.AlertEvent;
import com.intelli.home.domain.event.DeviceEvent;
import java.util.*;

public class RuleEngine {
  private final List<Rule> rules; // 规则对象集(执行器,而非具体规则)

  public RuleEngine(List<Rule> rules) {
    this.rules = List.copyOf(rules);
  }

  public List<AlertEvent> evaluate(RuleEvaluationInput input) {
    var sourceEvent = input.sourceEvent();
    var definitions = input.definitions();
    var result = new ArrayList<AlertEvent>();
    rules.stream()
        .filter(
            r -> definitions.containsKey(r.code()) && definitions.get(r.code()).enabled()) // 筛选启用规则
        .sorted( // 规则优先级排序
            Comparator.comparingInt((Rule r) -> definitions.get(r.code()).priority())
                .thenComparing(Rule::code))
        .forEach( // 规则匹配评估
            rule -> {
              var c =
                  new RuleContext(
                      sourceEvent,
                      input.effectiveProperties(),
                      definitions.get(rule.code()).params());
              if (!rule.supports(c)) return; // 规则适配性过滤
              var verdict = rule.evaluate(c);
              if (verdict.status() == RuleResult.Status.MATCHED)
                result.add(
                    AlertEvent.builder()
                        .messageId(
                            DeviceEvent.stableId(
                                "alert", sourceEvent.getMessageId() + ":" + rule.code()))
                        .deviceKey(sourceEvent.getDeviceKey())
                        .ruleCode(rule.code())
                        .scene(rule.scene().name())
                        .level(verdict.level())
                        .title(verdict.title())
                        .content(verdict.content())
                        .occurredAt(sourceEvent.getOccurredAt())
                        .build());
            });
    return List.copyOf(result);
  }
}
