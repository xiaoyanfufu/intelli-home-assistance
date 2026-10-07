package com.intelli.home.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.home.application.port.out.CommandGateway;
import com.intelli.home.application.service.DeviceCatalogService;
import com.intelli.home.config.IntelliProperties;
import com.intelli.home.domain.home.DeviceCommand;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MqttCommandGateway implements CommandGateway {
  private final IntelliProperties.Mqtt settings;
  private final DeviceCatalogService devices;
  private final ObjectMapper json;

  public void publish(DeviceCommand command) {
    var device = devices.require(command.deviceKey());
    try (var client = new MqttClient(settings.getBrokerUrl(), "home-command-" + command.id())) {
      var options = new MqttConnectOptions();
      options.setConnectionTimeout(3);
      options.setKeepAliveInterval(30);
      if (settings.getUsername() != null && !settings.getUsername().isBlank()) {
        options.setUserName(settings.getUsername());
        options.setPassword(settings.getPassword().toCharArray());
      }
      client.connect(options);
      client.setTimeToWait(5000);
      var payload =
          Map.of(
              "version",
              1,
              "commandId",
              command.id(),
              "deviceKey",
              command.deviceKey(),
              "action",
              command.actionCode(),
              "parameters",
              command.parameters(),
              "expiresAt",
              command.expiresAt());
      client.publish(
          "home/"
              + device.location().toLowerCase(java.util.Locale.ROOT)
              + "/"
              + device.deviceKey()
              + "/command",
          json.writeValueAsBytes(payload),
          1,
          false);
      client.disconnect();
    } catch (Exception e) {
      throw new IllegalStateException("Command publication failed", e);
    }
  }
}
