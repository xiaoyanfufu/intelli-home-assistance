package com.intelli.home.adapter.out.messaging;

import com.intelli.home.application.port.out.NotificationGateway;
import com.intelli.home.config.IntelliProperties;
import com.intelli.home.domain.alert.AlertEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "intelli.notification", name = "channel", havingValue = "email")
public class EmailNotificationGateway implements NotificationGateway {
  private final IntelliProperties.Notification settings;
  private final ObjectProvider<JavaMailSender> sender;

  public EmailNotificationGateway(
      IntelliProperties.Notification settings, ObjectProvider<JavaMailSender> sender) {
    this.settings = settings;
    this.sender = sender;
  }

  public void send(AlertEvent alert) {
    var email = settings.getEmail();
    // Disabled email must fail delivery, rather than committing a misleading receipt.
    if (!email.isEnabled()) throw new IllegalStateException("Email delivery is disabled");
    // A filtered notification is handled successfully; its receipt intentionally commits.
    if (!email.getMinLevelExemptRuleCodes().contains(alert.getRuleCode())
        && alert.getLevel().ordinal() < email.getMinLevel().ordinal()) return;
    var message = new SimpleMailMessage();
    message.setFrom(email.getFrom().isBlank() ? email.getUsername() : email.getFrom());
    message.setTo(email.getTo().toArray(String[]::new));
    message.setSubject(
        email.getSubjectPrefix()
            + " ["
            + alert.getLevel()
            + "] "
            + alert.getTitle()
            + " ["
            + alert.getMessageId()
            + "]");
    message.setText(
        "messageId: "
            + alert.getMessageId()
            + "\ndeviceKey: "
            + alert.getDeviceKey()
            + "\nruleCode: "
            + alert.getRuleCode()
            + "\nscene: "
            + alert.getScene()
            + "\nlevel: "
            + alert.getLevel()
            + "\ntitle: "
            + alert.getTitle()
            + "\noccurredAt: "
            + alert.getOccurredAt()
            + "\n\n"
            + alert.getContent());
    sender.getObject().send(message);
  }
}
