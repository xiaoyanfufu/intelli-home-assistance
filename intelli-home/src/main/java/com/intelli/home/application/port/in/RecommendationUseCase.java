package com.intelli.home.application.port.in;

import com.intelli.home.application.model.RecommendationResult;

public interface RecommendationUseCase {
  RecommendationResult recommend(String deviceKey);
}
