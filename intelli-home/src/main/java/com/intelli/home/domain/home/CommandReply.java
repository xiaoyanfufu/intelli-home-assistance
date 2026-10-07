package com.intelli.home.domain.home;

import java.util.Map;

public record CommandReply(
    int version,
    String commandId,
    String deviceKey,
    boolean success,
    long occurredAt,
    Map<String, Object> result,
    Map<String, Object> properties) {}
