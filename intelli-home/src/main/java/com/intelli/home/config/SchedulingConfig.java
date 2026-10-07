package com.intelli.home.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 开启规则配置刷新、离线检测和 Outbox 等定时任务。 */
@Configuration
@EnableScheduling
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    prefix = "intelli.scheduling",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SchedulingConfig {
  // Explicit name keeps all existing @Scheduled tasks on their own scheduler when
  // the additional daily scheduler causes Boot's scheduler auto-configuration to back off.
  @org.springframework.context.annotation.Bean
  public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler(
      org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder builder) {
    return builder.threadNamePrefix("home-scheduled-").build();
  }
}
