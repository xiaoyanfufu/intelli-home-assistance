package com.intelli.home.adapter.in.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.home.application.port.out.CommandRepository;
import com.intelli.home.application.service.DeviceCatalogService;
import com.intelli.home.config.IntelliProperties;
import com.intelli.home.domain.home.CommandReply;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "intelli.extensions", name = "mock-controls", havingValue = "true")
public class MockControlSimulator implements SmartLifecycle, MqttCallbackExtended {
  private final IntelliProperties.Mqtt settings;
  private final CommandRepository commands;
  private final DeviceCatalogService devices;
  private final ObjectMapper json;
  private volatile boolean running;
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
      if (client != null && client.isConnected()) return;
      if (client == null) {
        client = new MqttClient(settings.getBrokerUrl(), settings.getClientId() + "-mock-control");
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
      log.warn("Mock control simulator unavailable: {}", e.getMessage());
    }
  }

  public void connectComplete(boolean reconnect, String uri) {
    try {
      client.subscribe("home/+/+/command", 1);
    } catch (MqttException e) {
      log.warn("Mock subscription failed", e);
    }
  }

  public void messageArrived(String topic, MqttMessage message) throws Exception {
    try {
      if (message.isRetained() || message.getPayload().length > 16384)
        throw new IllegalArgumentException("Invalid command payload");
      var body = json.readTree(message.getPayload());
      String id = body.path("commandId").asText();
      var command = commands.find(id);
      String[] parts = topic.split("/", -1);
      if (command == null
          || parts.length != 4
          || !command.deviceKey().equals(parts[2])
          || !command.deviceKey().equals(body.path("deviceKey").asText())
          || body.path("version").asInt() != 1
          || !command.actionCode().equals(body.path("action").asText())
          || !"setPower".equals(command.actionCode())
          || !json.valueToTree(command.parameters()).equals(body.path("parameters")))
        throw new IllegalArgumentException("Unknown/mismatched command");
      var device = devices.require(command.deviceKey());
      if (!device.source().equals("MOCK")
          || !device.modelKey().equals("mock-switch")
          || !parts[1].equals(device.location().toLowerCase(Locale.ROOT)))
        throw new IllegalArgumentException("Not a mock control device");
      long now = System.currentTimeMillis();
      boolean expired = now >= command.expiresAt();
      var result =
          commands.executeMockOnce(
              command.id(),
              command.deviceKey(),
              expired
                  ? Map.of("error", "EXPIRED")
                  : Map.of("power", command.parameters().get("power")));
      boolean success = !result.containsKey("error");
      var reply =
          new CommandReply(
              1,
              command.id(),
              command.deviceKey(),
              success,
              now,
              result,
              success ? result : Map.of());
      client.publish(
          "home/" + parts[1] + "/" + parts[2] + "/command-reply",
          json.writeValueAsBytes(reply),
          1,
          false);
    } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) {
      log.warn("Discarding invalid mock command topic={} reason={}", topic, e.getMessage());
    }
  }

  public void connectionLost(Throwable cause) {
    log.warn("Mock simulator disconnected");
  }

  public void deliveryComplete(IMqttDeliveryToken token) {}

  public boolean isRunning() {
    return running;
  }

  public int getPhase() {
    return 110;
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
      log.warn("Mock simulator shutdown failed", e);
    }
  }
}
