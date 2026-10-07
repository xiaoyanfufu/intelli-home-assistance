package com.intelli.home.adapter.in.http;

import com.intelli.home.application.service.*;
import com.intelli.home.domain.home.WeatherSnapshot;
import com.intelli.home.domain.rule.RuleDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LaundryController {
  private final LaundryService laundry;
  private final RuleManagementService rules;

  @Value("\u0024{intelli.extensions.weather-mode:disabled}")
  private String mode;

  public record Start(String deviceKey) {}

  public record Trial(boolean active, WeatherSnapshot weather, RuleDefinition definition) {}

  @PostMapping("/laundry-sessions")
  public Object start(@RequestBody Start request) {
    var session = laundry.start(request.deviceKey());
    laundry.evaluate();
    return session;
  }

  @GetMapping("/laundry-sessions")
  public Object list() {
    return laundry.list();
  }

  @PostMapping("/laundry-sessions/{id}/complete")
  public Object complete(@PathVariable String id) {
    return laundry.complete(id);
  }

  @GetMapping("/weather/current")
  public Object weather() {
    return laundry.weather();
  }

  @PostMapping("/mock/weather")
  public Object mock(@RequestBody WeatherSnapshot weather) {
    if (!"mock".equals(mode)) throw new IllegalArgumentException("Mock weather disabled");
    if (!"mock".equals(weather.source()))
      throw new IllegalArgumentException("Mock source required");
    laundry.saveWeather(weather);
    laundry.evaluate();
    return laundry.weather();
  }

  @PostMapping("/rules/COLLECT_LAUNDRY/evaluate")
  public Object trial(@RequestBody Trial input) {
    rules.validate("COLLECT_LAUNDRY", input.definition());
    return laundry.trial(input.active(), input.weather(), input.definition());
  }
}
