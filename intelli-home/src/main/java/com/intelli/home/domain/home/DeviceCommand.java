package com.intelli.home.domain.home;

import java.util.Map;

public record DeviceCommand(
    String id,
    String idempotencyKey,
    String deviceKey,
    String actionCode,
    Map<String, Object> parameters,
    String requester,
    String status,
    long createdAt,
    long expiresAt,
    Map<String, Object> result,
    Map<String, Object> lateResult,
    long version) {}
