package com.intelli.home.adapter;

import static org.assertj.core.api.Assertions.*;

import com.intelli.home.adapter.out.messaging.*;
import com.intelli.home.application.port.out.NotificationGateway;
import com.intelli.home.config.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

class NotificationChannelWiringTest {
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(IntelliProperties.Notification.class)
  static class Properties {}

  final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              Properties.class,
              EmailNotificationConfig.class,
              EmailNotificationGateway.class,
              LogNotificationGateway.class);

  @Test
  void defaultIsLog() {
    runner.run(
        c ->
            assertThat(c)
                .hasSingleBean(NotificationGateway.class)
                .hasSingleBean(LogNotificationGateway.class));
  }

  @Test
  void disabledEmailStartsButCannotClaimSuccessfulDelivery() {
    runner
        .withPropertyValues("intelli.notification.channel=email")
        .run(
            c -> {
              assertThat(c)
                  .hasSingleBean(NotificationGateway.class)
                  .doesNotHaveBean(JavaMailSender.class);
              assertThatThrownBy(
                      () ->
                          c.getBean(NotificationGateway.class)
                              .send(new com.intelli.home.domain.alert.AlertEvent()))
                  .isInstanceOf(IllegalStateException.class);
            });
  }

  @Test
  void enabledEmailValidatesConfiguration() {
    runner
        .withPropertyValues(
            "intelli.notification.channel=email", "intelli.notification.email.enabled=true")
        .run(c -> assertThat(c).hasFailed());
  }

  @Test
  void enabledEmailHasOnlyOneGatewayAndTlsProperties() {
    runner
        .withPropertyValues(
            "intelli.notification.channel=email",
            "intelli.notification.email.enabled=true",
            "intelli.notification.email.host=127.0.0.1",
            "intelli.notification.email.from=sender@example.test",
            "intelli.notification.email.to=receiver@example.test",
            "intelli.notification.email.security=starttls")
        .run(
            c -> {
              assertThat(c)
                  .hasSingleBean(NotificationGateway.class)
                  .hasSingleBean(JavaMailSender.class);
              var sender = c.getBean(org.springframework.mail.javamail.JavaMailSenderImpl.class);
              assertThat(sender.getJavaMailProperties())
                  .containsEntry("mail.smtp.starttls.required", "true")
                  .containsEntry("mail.smtp.connectiontimeout", "5000")
                  .containsEntry("mail.smtp.ssl.enable", "false");
            });
  }
}
