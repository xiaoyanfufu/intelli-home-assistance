package com.intelli.home.config;

import jakarta.mail.internet.InternetAddress;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "intelli.notification", name = "channel", havingValue = "email")
public class EmailNotificationConfig {
  @Bean
  @ConditionalOnProperty(
      prefix = "intelli.notification.email",
      name = "enabled",
      havingValue = "true")
  public JavaMailSenderImpl notificationMailSender(IntelliProperties.Notification settings) {
    var email = settings.getEmail();
    if (email.getHost().isBlank() || email.getTo().isEmpty())
      throw new IllegalArgumentException("Email host and recipients are required");
    String from = email.getFrom().isBlank() ? email.getUsername() : email.getFrom();
    try {
      new InternetAddress(from, true).validate();
      for (String recipient : email.getTo()) new InternetAddress(recipient, true).validate();
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid email sender or recipient");
    }
    if (email.getPort() < 1
        || email.getPort() > 65535
        || email.getTimeout().toMillis() < 1
        || email.getTimeout().isNegative()
        || email.getTimeout().toMillis() > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Invalid email port or timeout");
    if (!email.getUsername().isBlank() && email.getPassword().isBlank())
      throw new IllegalArgumentException("Authenticated SMTP requires a password");
    if (!java.util.Set.of("ssl", "starttls", "none").contains(email.getSecurity()))
      throw new IllegalArgumentException("Email security must be ssl, starttls or none");
    var sender = new JavaMailSenderImpl();
    sender.setHost(email.getHost());
    sender.setPort(email.getPort());
    sender.setUsername(email.getUsername());
    sender.setPassword(email.getPassword());
    sender.setDefaultEncoding("UTF-8");
    var properties = sender.getJavaMailProperties();
    properties.setProperty("mail.smtp.auth", Boolean.toString(!email.getUsername().isBlank()));
    properties.setProperty(
        "mail.smtp.ssl.enable", Boolean.toString("ssl".equals(email.getSecurity())));
    properties.setProperty(
        "mail.smtp.starttls.enable", Boolean.toString("starttls".equals(email.getSecurity())));
    properties.setProperty(
        "mail.smtp.starttls.required", Boolean.toString("starttls".equals(email.getSecurity())));
    properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
    for (String name : java.util.List.of("connectiontimeout", "timeout", "writetimeout"))
      properties.setProperty("mail.smtp." + name, Long.toString(email.getTimeout().toMillis()));
    properties.setProperty("mail.debug", Boolean.toString(email.isDebug()));
    return sender;
  }
}
