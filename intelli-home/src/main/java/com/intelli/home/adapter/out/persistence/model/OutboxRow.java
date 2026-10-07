package com.intelli.home.adapter.out.persistence.model;

import lombok.Data;

/** Database row representation, separate from the domain model. */
@Data
public class OutboxRow {
  private String messageId;
  private String payload;
}
