package com.intelli.home.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DailyAdviceConfig {
  @Bean
  public Clock dailyAdviceClock() {
    return Clock.systemUTC();
  }
}
