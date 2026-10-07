package com.intelli.home.domain.rule;

import com.intelli.home.domain.event.DeviceEvent;
import java.util.Map;

/** 单条规则的上下文；判断属性来自有效数据，事件用于适用范围和身份判断。 */
public record RuleContext(
    DeviceEvent sourceEvent, Map<String, Object> effectiveProperties, Map<String, Double> params) {
  public Double number(String key) {
    Object value = effectiveProperties.get(key);
    try {
      double number = Double.parseDouble(String.valueOf(value));
      return Double.isFinite(number) ? number : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public double parameter(String key, double fallback) {
    return params.getOrDefault(key, fallback);
  }
}
