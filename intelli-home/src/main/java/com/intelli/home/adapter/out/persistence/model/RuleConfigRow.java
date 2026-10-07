package com.intelli.home.adapter.out.persistence.model;

import lombok.Data;

/** Database row representation, separate from the domain model. */
@Data
public class RuleConfigRow {
  private String ruleCode;
  private boolean enabled;
  private int priority;
  private String paramsJson;
}
