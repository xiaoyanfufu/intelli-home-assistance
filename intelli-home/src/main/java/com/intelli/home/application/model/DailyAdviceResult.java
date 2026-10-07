package com.intelli.home.application.model;

import java.util.Map;

public record DailyAdviceResult(
    String advice, Map<String, Object> analysis, boolean degraded, String degradationReason) {}
