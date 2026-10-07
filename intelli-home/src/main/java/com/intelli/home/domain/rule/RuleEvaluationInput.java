package com.intelli.home.domain.rule;

import com.intelli.home.domain.event.DeviceEvent;
import java.util.Map;
import java.util.Objects;

/** 一次规则评估的输入。原始事件保留身份与上报内容；有效属性可以是合并、过滤过期值后的设备快照。 definitions 是本次评估使用的同一份规则配置，不能由引擎自行刷新。 */
public record RuleEvaluationInput(
    DeviceEvent sourceEvent,
    Map<String, Object> effectiveProperties,
    Map<String, RuleDefinition> definitions) {
  public RuleEvaluationInput {
    Objects.requireNonNull(sourceEvent, "sourceEvent");
    effectiveProperties = Map.copyOf(effectiveProperties);
    definitions = Map.copyOf(definitions);
  }
}
