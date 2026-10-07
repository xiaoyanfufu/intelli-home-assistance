package com.intelli.home.domain.rule;

import com.intelli.home.domain.alert.AlertLevel;

public record RuleResult(Status status, AlertLevel level, String title, String content) {
  public enum Status {
    MATCHED,
    NOT_MATCHED,
    INSUFFICIENT_DATA
  }

  public static RuleResult notMatched() {
    return new RuleResult(Status.NOT_MATCHED, null, null, null);
  }

  public static RuleResult insufficient() {
    return new RuleResult(Status.INSUFFICIENT_DATA, null, null, null);
  }

  public static RuleResult matched(AlertLevel level, String title, String content) {
    return new RuleResult(Status.MATCHED, level, title, content);
  }
}
