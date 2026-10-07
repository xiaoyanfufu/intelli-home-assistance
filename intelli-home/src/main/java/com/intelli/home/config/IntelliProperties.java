package com.intelli.home.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * intelli.* 业务配置绑定。
 *
 * <p>由 {@link PropertiesConfig} 的 {@code @ConfigurationPropertiesScan} 扫描注册。
 */
public class IntelliProperties {
  @Data
  @ConfigurationProperties(prefix = "intelli.notification")
  public static class Notification {
    private String channel = "log";
    private Email email = new Email();
    private Api api = new Api();

    @Data
    public static class Api {
      private boolean enabled;
      private int maxBodyLength = 4000;
    }

    @Data
    public static class Email {
      private boolean enabled;
      private String host = "";
      private int port = 465;
      private String security = "ssl";
      private String username = "";
      private String password = "";
      private String from = "";
      private java.util.List<String> to = new java.util.ArrayList<>();
      private String subjectPrefix = "[intelli-home]";
      private com.intelli.home.domain.alert.AlertLevel minLevel =
          com.intelli.home.domain.alert.AlertLevel.WARN;
      private java.util.Set<String> minLevelExemptRuleCodes =
          java.util.Set.of("DAILY_DIGEST", "MANUAL_EMAIL");
      private java.time.Duration timeout = java.time.Duration.ofSeconds(5);
      private boolean debug;
    }
  }

  @Data
  @ConfigurationProperties(prefix = "intelli.daily-advice")
  public static class DailyAdvice {
    private boolean enabled;
    private String cron = "0 0/15 7-9 * * *";
    private String zone = "Asia/Shanghai";
    private int historyHours = 24;
    private int maxDevices = 20;
    private int maxSamplesPerDevice = 20000;
    private String subjectPrefix = "[intelli-home] 今日生活建议";
  }

  /** MQTT 接入配置 */
  @Data
  @ConfigurationProperties(prefix = "intelli.mqtt")
  public static class Mqtt {
    private boolean enabled = true;
    private String brokerUrl = "tcp://localhost:1883";
    private String clientId = "intelli-home-backend";
    private String username = "";
    private String password = "";
    private String[] subscribeTopics = new String[] {"home/+/+/event"};
    private int qos = 1;
  }

  /** Agent 服务配置 */
  @Data
  @ConfigurationProperties(prefix = "intelli.agent")
  public static class Agent {
    private boolean enabled = false;
    private String baseUrl = "http://localhost:8000";
    private String timeout = "30s";
  }
}
