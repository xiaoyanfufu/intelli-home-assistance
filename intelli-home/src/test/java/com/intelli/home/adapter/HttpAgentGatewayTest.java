package com.intelli.home.adapter;

import static org.junit.jupiter.api.Assertions.*;

import com.intelli.home.adapter.out.agent.HttpAgentGateway;
import com.intelli.home.application.model.RecommendationRequest;
import com.intelli.home.config.IntelliProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpAgentGatewayTest {
  @Test
  void sendsJsonBodyWithoutHttp2Upgrade() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var body = new AtomicReference<String>();
    var upgrade = new AtomicReference<String>();
    server.createContext(
        "/agent/recommend",
        exchange -> {
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
          byte[] response =
              "{\"recommendation\":\"template\",\"scenario\":\"NORMAL\",\"analysis\":{},\"degraded\":true,\"degradationReason\":\"LLM_NOT_CONFIGURED\"}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      var settings = new IntelliProperties.Agent();
      settings.setEnabled(true);
      settings.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
      var client = new HttpAgentGateway(RestClient.builder(), settings);
      var result =
          client.recommend(
              new RecommendationRequest(
                  1,
                  "request-1",
                  "device-1",
                  "INDOOR",
                  Map.of("temperature", 24),
                  Map.of(),
                  List.of()));
      assertEquals("LLM_NOT_CONFIGURED", result.degradationReason());
      assertNull(upgrade.get());
      var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body.get());
      assertEquals("device-1", json.get("deviceKey").asText());
      assertEquals(24, json.get("properties").get("temperature").asInt());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void disabledAgentReturnsExplicitFallback() {
    var client = new HttpAgentGateway(RestClient.builder(), new IntelliProperties.Agent());
    assertEquals(
        "AGENT_DISABLED",
        client
            .recommend(
                new RecommendationRequest(
                    1, "req", "device", "INDOOR", Map.of(), Map.of(), List.of()))
            .degradationReason());
  }
}
