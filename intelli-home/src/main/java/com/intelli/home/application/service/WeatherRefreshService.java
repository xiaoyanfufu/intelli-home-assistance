package com.intelli.home.application.service;

import com.intelli.home.application.port.out.WeatherGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WeatherRefreshService {
  private final WeatherGateway gateway;
  private final LaundryService laundry;
  private final String mode;

  public WeatherRefreshService(
      WeatherGateway gateway,
      LaundryService laundry,
      @Value("\u0024{intelli.extensions.weather-mode:disabled}") String mode) {
    this.gateway = gateway;
    this.laundry = laundry;
    this.mode = mode;
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 60000)
  public void refresh() {
    if (!"mock".equals(mode)) {
      laundry.saveWeather(gateway.fetch());
      laundry.evaluate();
    }
  }
}
