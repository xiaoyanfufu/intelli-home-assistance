package com.intelli.home.adapter.in.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.home.application.port.in.DeviceEventHandler;
import com.intelli.home.application.service.CommandService;
import com.intelli.home.config.IntelliProperties;
import com.intelli.home.domain.home.CommandReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "intelli.mqtt", name = "enabled", havingValue = "true")
public class MqttDeviceAccessAdapter implements SmartLifecycle, MqttCallbackExtended {
  private final IntelliProperties.Mqtt settings;
  private final MqttDeviceEventParser parser;
  private final DeviceEventHandler handler;
  private final CommandService commands;
  private final ObjectMapper json;
  private volatile boolean running;
  private volatile boolean subscribed;
  private MqttClient client;

  public synchronized void start() {
    running = true;
    connect();
  }

  @Scheduled(fixedDelay = 10000, initialDelay = 10000)
  public synchronized void reconnect() {
    if (running) connect();
  }

  private void connect() {
    try {
      if (client != null && client.isConnected()) {
        if (!subscribed) subscribe();
        return;
      }
      if (client == null) {
        client =
            new MqttClient(
                settings.getBrokerUrl(), settings.getClientId(), new MemoryPersistence());
        client.setCallback(this);
      }
      var options = new MqttConnectOptions();
      options.setCleanSession(false);
      options.setConnectionTimeout(3);
      options.setKeepAliveInterval(30);
      if (settings.getUsername() != null && !settings.getUsername().isBlank()) {
        options.setUserName(settings.getUsername());
        options.setPassword(settings.getPassword().toCharArray());
      }
      client.connect(options);
    } catch (MqttException e) {
      log.warn("MQTT unavailable; retry scheduled: {}", e.getMessage());
    }
  }

  public boolean isRunning() {
    return running;
  }

  public int getPhase() {
    return 100;
  }

  public synchronized void stop() {
    running = false;
    try {
      if (client != null) {
        if (client.isConnected()) client.disconnect();
        client.close();
        client = null;
      }
    } catch (MqttException e) {
      log.warn("MQTT shutdown failed", e);
    }
  }

  public void connectComplete(boolean reconnect, String uri) {
    subscribe();
  }

  private void subscribe() {
    try {
      for (String topic : settings.getSubscribeTopics()) client.subscribe(topic, settings.getQos());
      subscribed = true;
      log.info("MQTT subscriptions ready");
    } catch (MqttException e) {
      subscribed = false;
      log.error("MQTT subscribe failed", e);
    }
  }

  public void messageArrived(String topic, MqttMessage message) throws Exception {
    if (topic.endsWith("/command-reply")) {
      String[] parts = topic.split("/", -1);
      try {
        if (parts.length != 4 || !parts[0].equals("home") || message.getPayload().length > 16384)
          throw new IllegalArgumentException("Invalid reply topic/payload");
        commands.reply(
            parts[2],
            parts[1].toUpperCase(java.util.Locale.ROOT),
            json.readValue(message.getPayload(), CommandReply.class));
      } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) {
        log.warn("Discarding invalid command reply topic={} reason={}", topic, e.getMessage());
      }
      return;
    }
    com.intelli.home.domain.event.DeviceEvent event;
    try {
      event = parser.parse(topic, message.getPayload());
    } catch (IllegalArgumentException e) {
      log.warn("Discarding invalid MQTT event topic={} reason={}", topic, e.getMessage());
      return;
    }
    // Paho acknowledges after callback returns; processing failures propagate and break the
    // connection.
    handler.handle(event);
  }

  public void connectionLost(Throwable cause) {
    subscribed = false;
    log.warn("MQTT disconnected; retry scheduled", cause);
  }

  public void deliveryComplete(IMqttDeliveryToken token) {}
}
