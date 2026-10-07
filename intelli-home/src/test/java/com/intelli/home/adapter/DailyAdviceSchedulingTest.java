package com.intelli.home.adapter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.intelli.home.application.service.DailyAdviceService;
import com.intelli.home.config.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class DailyAdviceSchedulingTest {
  static final class Probe {
    final AtomicReference<String> thread = new AtomicReference<>();
    volatile CountDownLatch observed = new CountDownLatch(1);

    @Scheduled(fixedDelay = 50)
    public void tick() {
      thread.set(Thread.currentThread().getName());
      observed.countDown();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class TestBeans {
    @Bean
    Probe probe() {
      return new Probe();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ComponentScan(
      basePackageClasses = DailyAdviceSchedulingConfig.class,
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = FilterType.ASSIGNABLE_TYPE,
              classes = {DailyAdviceSchedulingConfig.class, SchedulingConfig.class}))
  static class ScannedScheduling {}

  @Test
  void packageScanningRespectsDailyDisabledDefault() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
        .withUserConfiguration(ScannedScheduling.class)
        .withBean(DailyAdviceService.class, () -> mock(DailyAdviceService.class))
        .run(
            c -> {
              assertNull(c.getStartupFailure());
              assertFalse(c.containsBean("dailyAdviceScheduler"));
              assertTrue(c.containsBean("taskScheduler"));
            });
  }

  ApplicationContextRunner runner(DailyAdviceService service) {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
        .withUserConfiguration(
            SchedulingConfig.class, DailyAdviceSchedulingConfig.class, TestBeans.class)
        .withBean(DailyAdviceService.class, () -> service)
        .withPropertyValues(
            "intelli.daily-advice.enabled=true", "intelli.daily-advice.cron=*/1 * * * * *");
  }

  @Test
  void blockedDailyTaskDoesNotBlockExistingScheduler() {
    var service = mock(DailyAdviceService.class);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var dailyThread = new AtomicReference<String>();
    when(service.run(false))
        .thenAnswer(
            i -> {
              dailyThread.set(Thread.currentThread().getName());
              entered.countDown();
              release.await(5, TimeUnit.SECONDS);
              return java.util.Map.of("status", "test");
            });
    runner(service)
        .withPropertyValues("spring.task.scheduling.pool.size=2")
        .run(
            c -> {
              try {
                assertNull(c.getStartupFailure());
                assertEquals(
                    1,
                    c.getBean("dailyAdviceScheduler", ThreadPoolTaskScheduler.class).getPoolSize());
                assertEquals(
                    2, c.getBean("taskScheduler", ThreadPoolTaskScheduler.class).getPoolSize());
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                var probe = c.getBean(Probe.class);
                probe.observed = new CountDownLatch(1);
                assertTrue(probe.observed.await(1, TimeUnit.SECONDS));
                assertTrue(probe.thread.get().startsWith("home-scheduled-"));
                assertTrue(dailyThread.get().startsWith("daily-advice-"));
              } finally {
                release.countDown();
              }
            });
  }

  @Test
  void globalSchedulingSwitchDisablesDailySchedulerButKeepsService() {
    runner(mock(DailyAdviceService.class))
        .withPropertyValues("intelli.scheduling.enabled=false")
        .run(
            c -> {
              assertNull(c.getStartupFailure());
              assertFalse(c.containsBean("dailyAdviceScheduler"));
              assertFalse(c.containsBean("dailyAdviceTask"));
              assertNotNull(c.getBean(DailyAdviceService.class));
            });
  }

  @Test
  void dailySwitchDoesNotDisableExistingScheduler() {
    runner(mock(DailyAdviceService.class))
        .withPropertyValues("intelli.daily-advice.enabled=false")
        .run(
            c -> {
              assertNull(c.getStartupFailure());
              assertFalse(c.containsBean("dailyAdviceScheduler"));
              assertTrue(c.containsBean("taskScheduler"));
            });
  }
}
