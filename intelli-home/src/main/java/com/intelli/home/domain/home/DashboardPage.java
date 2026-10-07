package com.intelli.home.domain.home;

public record DashboardPage(
    String id, long revision, boolean archived, long updatedAt, PageDefinition definition) {}
