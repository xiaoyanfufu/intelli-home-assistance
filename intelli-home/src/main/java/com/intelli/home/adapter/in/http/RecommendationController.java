package com.intelli.home.adapter.in.http;

import com.intelli.home.application.model.RecommendationResult;
import com.intelli.home.application.port.in.RecommendationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RecommendationController {
  private final RecommendationUseCase recommendation;

  @PostMapping("/api/devices/{deviceKey}/recommendation")
  public RecommendationResult generate(@PathVariable String deviceKey) {
    return recommendation.recommend(deviceKey);
  }
}
