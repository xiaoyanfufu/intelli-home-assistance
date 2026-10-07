package com.intelli.home.domain.home;

import java.util.Map;

public record DeviceModel(
    String modelKey,
    int version,
    String displayName,
    Map<String, Property> properties,
    Map<String, Action> actions) {
  public record Property(
      String displayName,
      String type,
      String unit,
      Double minimum,
      Double maximum,
      Map<String, String> values,
      boolean queryable,
      boolean calibrated) {}

  public record Action(
      String displayName,
      Map<String, Property> parameters,
      int timeoutSeconds,
      boolean idempotent) {}
}
