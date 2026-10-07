package com.intelli.home.domain.home;

public record HomeDevice(
    String deviceKey,
    String displayName,
    String modelKey,
    int modelVersion,
    String source,
    String location,
    long createdAt) {}
