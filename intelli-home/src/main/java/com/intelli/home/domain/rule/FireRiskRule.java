package com.intelli.home.domain.rule;

import com.intelli.home.domain.alert.AlertLevel;
import com.intelli.home.domain.device.Location;
import com.intelli.home.domain.event.EventType;

public class FireRiskRule implements Rule {
  public String code() {
    return "FIRE_RISK";
  }

  public RuleScene scene() {
    return RuleScene.FIRE;
  }

  public boolean supports(RuleContext c) {
    return c.sourceEvent().getLocation() == Location.INDOOR
        && c.sourceEvent().getEventType() == EventType.TELEMETRY;
  }

  public RuleResult evaluate(RuleContext c) {
    Double temperature = c.number("temperature"), smoke = c.number("smoke");
    if ((temperature != null && temperature >= c.parameter("temperature", 45))
        || (smoke != null && smoke >= c.parameter("smoke", 300)))
      return RuleResult.matched(
          AlertLevel.CRITICAL,
          "火灾隐患",
          "环境指标达到预设隐患阈值，请确认现场情况并按应急预案处理。温度=" + temperature + "，烟雾=" + smoke);
    return temperature == null || smoke == null
        ? RuleResult.insufficient()
        : RuleResult.notMatched();
  }
}
