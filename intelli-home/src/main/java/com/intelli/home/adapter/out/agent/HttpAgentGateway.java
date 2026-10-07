package com.intelli.home.adapter.out.agent;

import com.intelli.home.application.model.*;
import com.intelli.home.application.port.out.AgentGateway;
import com.intelli.home.config.IntelliProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpAgentGateway implements AgentGateway {
  private final RestClient client;
  private final boolean enabled;

  public HttpAgentGateway(RestClient.Builder builder, IntelliProperties.Agent settings) {
    enabled = settings.isEnabled();
    Duration timeout =
        org.springframework.boot.convert.DurationStyle.detectAndParse(settings.getTimeout());
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build());
    factory.setReadTimeout(timeout);
    client = builder.baseUrl(settings.getBaseUrl()).requestFactory(factory).build();
  }

  public RecommendationResult recommend(RecommendationRequest request) {
    if (!enabled)
      return new RecommendationResult(
          "Agent 未启用，可查看设备状态与规则告警。", "UNKNOWN", java.util.Map.of(), true, "AGENT_DISABLED");
    var result =
        client
            .post()
            .uri("/agent/recommend")
            .body(request)
            .retrieve()
            .body(RecommendationResult.class);
    if (result == null || result.recommendation() == null || result.recommendation().isBlank())
      throw new IllegalStateException("Invalid Agent response");
    return result;
  }

  public DailyAdviceResult dailyAdvice(DailyAdviceRequest request) {
    if (!enabled) throw new IllegalStateException("AGENT_DISABLED");
    var result =
        client
            .post()
            .uri("/agent/daily-advice")
            .body(request)
            .retrieve()
            .body(DailyAdviceResult.class);
    if (result == null
        || result.advice() == null
        || result.advice().isBlank()
        || result.advice().length() > 20000)
      throw new IllegalStateException("Invalid daily advice response");
    return result;
  }
}
