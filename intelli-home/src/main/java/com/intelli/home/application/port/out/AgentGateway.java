package com.intelli.home.application.port.out;

import com.intelli.home.application.model.*;

public interface AgentGateway {
  RecommendationResult recommend(RecommendationRequest request);

  default DailyAdviceResult dailyAdvice(DailyAdviceRequest request) {
    throw new UnsupportedOperationException("Daily advice is not supported");
  }
}
