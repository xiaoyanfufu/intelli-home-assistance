package com.intelli.home.application.model;

import java.util.List;
import java.util.Map;

public record DailyAdviceRequest(
    int version,
    String requestId,
    String date,
    int windowHours,
    Map<String, Map<String, Object>> states,
    List<Map<String, Object>> history,
    Map<String, Object> alertSummary,
    Map<String, Object> coverage) {}
