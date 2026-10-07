package com.intelli.home.domain.home;

import java.util.Map;

public record ChangeEvent(
    long id,
    String eventKey,
    String type,
    String deviceKey,
    long occurredAt,
    long resourceVersion,
    Map<String, Object> payload) {}
