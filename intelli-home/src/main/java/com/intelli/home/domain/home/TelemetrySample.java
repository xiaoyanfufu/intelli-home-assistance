package com.intelli.home.domain.home;

import java.util.Map;

public record TelemetrySample(
    String eventId,
    String deviceKey,
    String modelKey,
    int modelVersion,
    long occurredAt,
    long receivedAt,
    Map<String, Object> properties) {}
