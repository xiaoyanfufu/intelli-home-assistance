package com.intelli.home.domain.rule;

import com.intelli.home.domain.alert.AlertLevel;
import com.intelli.home.domain.home.WeatherSnapshot;

public class LaundryRule {
  public RuleResult evaluate(
      boolean active, WeatherSnapshot weather, long now, RuleDefinition definition) {
    if (!active || !definition.enabled()) return RuleResult.notMatched();
    if (weather == null
        || !weather.fresh(
            now, (long) (definition.params().getOrDefault("weatherMaxAgeSeconds", 600.0) * 1000))
        || (weather.raining() == null && weather.rainProbability() == null))
      return RuleResult.insufficient();
    boolean risk =
        Boolean.TRUE.equals(weather.raining())
            || (weather.rainProbability() != null
                && weather.rainProbability()
                    >= definition.params().getOrDefault("rainProbability", 0.6));
    return risk
        ? RuleResult.matched(AlertLevel.INFO, "收衣提醒", "检测到降雨风险，当前有衣物正在晾晒，请及时查看并收衣。")
        : RuleResult.notMatched();
  }
}
