package com.intelli.home.config;

import com.intelli.home.application.service.DailyAdviceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "intelli.daily-advice", name = "enabled", havingValue = "true")
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
    "${intelli.scheduling.enabled:true}")
public class DailyAdviceSchedulingConfig {
  @Bean
  public ThreadPoolTaskScheduler dailyAdviceScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("daily-advice-");
    scheduler.setWaitForTasksToCompleteOnShutdown(false);
    return scheduler;
  }

  @Bean
  public DailyTask dailyAdviceTask(DailyAdviceService service) {
    return new DailyTask(service);
  }

  public record DailyTask(DailyAdviceService service) {
    @Scheduled(
        cron = "${intelli.daily-advice.cron:0 0/15 7-9 * * *}",
        zone = "${intelli.daily-advice.zone:Asia/Shanghai}",
        scheduler = "dailyAdviceScheduler")
    public void tick() {
      service.run(false);
    }
  }
}
