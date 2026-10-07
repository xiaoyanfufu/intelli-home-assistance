package com.intelli.home.adapter.out.agent;

import com.intelli.home.application.port.out.WeatherGateway;
import com.intelli.home.domain.home.WeatherSnapshot;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpWeatherGateway implements WeatherGateway {
  private final String mode;
  private final RestClient client;

  public HttpWeatherGateway(
      @Value("\u0024{intelli.extensions.weather-mode:disabled}") String mode,
      @Value("\u0024{intelli.agent.base-url:http://localhost:8000}") String url) {
    this.mode = mode;
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build());
    factory.setReadTimeout(Duration.ofSeconds(5));
    client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
  }

  public WeatherSnapshot fetch() {
    long now = System.currentTimeMillis();
    if (!mode.equals("python"))
      return new WeatherSnapshot(
          "local", "disabled", now, now, false, null, null, "WEATHER_DISABLED");
    try {
      var snapshot = client.get().uri("/agent/weather").retrieve().body(WeatherSnapshot.class);
      if (snapshot == null) throw new IllegalStateException();
      return snapshot;
    } catch (Exception e) {
      return new WeatherSnapshot(
          "local", "python", now, now, false, null, null, "WEATHER_UNAVAILABLE");
    }
  }
}
