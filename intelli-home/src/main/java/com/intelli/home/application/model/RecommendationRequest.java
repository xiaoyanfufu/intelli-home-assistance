package com.intelli.home.application.model;

import com.intelli.home.domain.alert.AlertEvent;
import java.util.*;

public record RecommendationRequest(
    int version,
    String requestId,
    String deviceKey,
    String location,
    Map<String, Object> properties,
    Map<String, Map<String, Object>> states,
    List<AlertEvent> safetyAlerts) {}
