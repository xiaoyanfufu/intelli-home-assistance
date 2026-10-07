package com.intelli.home.adapter.out.persistence.model;

import lombok.Data;

/** Database row representation, separate from the domain model. */
@Data
public class DeviceSnapshotRow {
  private long version;
  private String deviceKey;
  private String location;
  private String source;
  private String eventId;
  private long occurredAt;
  private long lastSeenAt;
  private String propertiesJson;
  private String timesJson;
}
