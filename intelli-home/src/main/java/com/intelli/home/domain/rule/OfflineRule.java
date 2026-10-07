package com.intelli.home.domain.rule;

import com.intelli.home.domain.alert.AlertLevel;
import com.intelli.home.domain.event.EventType;

public class OfflineRule implements Rule {
  public String code() {
    return "DEVICE_OFFLINE";
  }

  public RuleScene scene() {
    return RuleScene.OFFLINE;
  }

  public boolean supports(RuleContext c) {
    return c.sourceEvent().getEventType() == EventType.STATUS;
  }

  public RuleResult evaluate(RuleContext c) {
    return Boolean.FALSE.equals(c.effectiveProperties().get("online"))
        ? RuleResult.matched(AlertLevel.WARN, "设备离线", "设备超过心跳期限未上报")
        : RuleResult.notMatched();
  }
}
