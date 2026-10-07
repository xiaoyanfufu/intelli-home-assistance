package com.intelli.home.domain.rule;

public interface Rule {
  String code();

  RuleScene scene();

  boolean supports(RuleContext context);

  RuleResult evaluate(RuleContext context);
}
