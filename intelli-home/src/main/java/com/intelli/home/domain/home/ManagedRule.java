package com.intelli.home.domain.home;

import com.intelli.home.domain.rule.RuleDefinition;

public record ManagedRule(long revision, RuleDefinition definition) {}
