package com.intelli.home.domain.home;

public record LaundrySession(
    String id,
    String deviceKey,
    String status,
    long startedAt,
    Long completedAt,
    boolean riskActive,
    int riskCycle,
    long version) {}
