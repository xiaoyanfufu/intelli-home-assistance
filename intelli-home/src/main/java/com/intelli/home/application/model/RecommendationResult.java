package com.intelli.home.application.model;

import java.util.Map;

public record RecommendationResult(
    String recommendation,
    String scenario,
    Map<String, Object> analysis,
    boolean degraded,
    String degradationReason) {}
