package com.intelli.home.adapter;

import static org.junit.jupiter.api.Assertions.*;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.intelli.home.adapter.out.messaging.EmailNotificationGateway;
import com.intelli.home.config.*;
import com.intelli.home.domain.alert.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class EmailNotificationGatewayTest {
  GreenMail smtp;
  IntelliProperties.Notification settings;
  EmailNotificationGateway gateway;

  @BeforeEach
  void setup() {
    smtp = new GreenMail(new ServerSetup(0, "127.0.0.1", "smtp"));
    smtp.start();
    smtp.setUser("receiver@example.test", "receiver", "password");
    settings = new IntelliProperties.Notification();
    var email = settings.getEmail();
    email.setEnabled(true);
    email.setHost("127.0.0.1");
    email.setPort(smtp.getSmtp().getPort());
    email.setSecurity("none");
    email.setFrom("sender@example.test");
    email.setTo(java.util.List.of("receiver@example.test"));
    var sender = new EmailNotificationConfig().notificationMailSender(settings);
    var beans = new DefaultListableBeanFactory();
    beans.registerSingleton("sender", sender);
    gateway = new EmailNotificationGateway(settings, beans.getBeanProvider(JavaMailSender.class));
  }

  @AfterEach
  void stop() {
    smtp.stop();
  }

  AlertEvent alert(String rule, AlertLevel level) {
    return AlertEvent.builder()
        .messageId("email-unit")
        .deviceKey("device")
        .ruleCode(rule)
        .scene("FIRE")
        .level(level)
        .title("火灾隐患")
        .content("温度过高，请确认现场安全。")
        .occurredAt(123L)
        .build();
  }

  @Test
  void sendsUtf8WithIdentityAndConfiguredRecipient() throws Exception {
    gateway.send(alert("FIRE_RISK", AlertLevel.CRITICAL));
    assertTrue(smtp.waitForIncomingEmail(2000, 1));
    var message = smtp.getReceivedMessages()[0];
    assertTrue(message.getSubject().contains("CRITICAL"));
    assertTrue(message.getSubject().contains("email-unit"));
    assertEquals("receiver@example.test", message.getAllRecipients()[0].toString());
    var text = message.getContent().toString();
    for (var expected : java.util.List.of("email-unit", "device", "FIRE_RISK", "123", "温度过高"))
      assertTrue(text.contains(expected));
  }

  @Test
  void lowLevelFilteredButDigestExempt() {
    gateway.send(alert("NORMAL", AlertLevel.INFO));
    assertEquals(0, smtp.getReceivedMessages().length);
    gateway.send(alert("DAILY_DIGEST", AlertLevel.INFO));
    assertTrue(smtp.waitForIncomingEmail(2000, 1));
  }

  @Test
  void connectionFailurePropagatesToTransaction() {
    smtp.stop();
    assertThrows(
        MailSendException.class, () -> gateway.send(alert("FIRE_RISK", AlertLevel.CRITICAL)));
  }
}
