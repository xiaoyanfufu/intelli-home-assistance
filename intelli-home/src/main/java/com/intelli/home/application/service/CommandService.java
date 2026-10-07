package com.intelli.home.application.service;

import com.intelli.home.application.port.out.*;
import com.intelli.home.domain.device.*;
import com.intelli.home.domain.event.*;
import com.intelli.home.domain.home.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommandService {
  private final CommandRepository repository;
  private final DeviceCatalogService devices;
  private final DeviceRegistry registry;
  private final ChangeFeed changes;
  private final boolean mockEnabled;
  private final DeviceStateStore states;
  private final TelemetryHistory history;

  public CommandService(
      CommandRepository repository,
      DeviceCatalogService devices,
      DeviceRegistry registry,
      ChangeFeed changes,
      DeviceStateStore states,
      TelemetryHistory history,
      @Value("\u0024{intelli.extensions.mock-controls:false}") boolean mockEnabled) {
    this.repository = repository;
    this.devices = devices;
    this.registry = registry;
    this.changes = changes;
    this.mockEnabled = mockEnabled;
    this.states = states;
    this.history = history;
  }

  public DeviceCommand require(String id) {
    var command = repository.find(id);
    if (command == null) throw new IllegalArgumentException("Unknown command");
    return command;
  }

  public List<DeviceCommand> list(String deviceKey) {
    devices.require(deviceKey);
    return repository.list(deviceKey);
  }

  public DeviceCommand submit(
      String deviceKey, String requestKey, String action, Map<String, Object> parameters) {
    var device = devices.require(deviceKey);
    if (!mockEnabled || !device.source().equals("MOCK") || !device.modelKey().equals("mock-switch"))
      throw new IllegalArgumentException("Controls are currently limited to enabled mock devices");
    if (requestKey == null || !requestKey.matches("[a-zA-Z0-9_-]{1,128}"))
      throw new IllegalArgumentException("Invalid idempotency key");
    var spec = devices.capabilities(deviceKey).actions().get(action);
    if (spec == null
        || !spec.idempotent()
        || parameters == null
        || !parameters.keySet().equals(spec.parameters().keySet()))
      throw new IllegalArgumentException("Unsupported action or parameters");
    var checked = new HashMap<String, Object>();
    parameters.forEach(
        (k, v) -> checked.put(k, devices.validateValue(spec.parameters().get(k), v)));
    var existing = repository.findRequest(deviceKey, requestKey);
    if (existing != null) return sameRequest(existing, action, checked);
    long now = System.currentTimeMillis();
    var snapshot =
        registry.snapshots().stream()
            .filter(s -> s.deviceKey().equals(deviceKey))
            .findFirst()
            .orElse(null);
    if (snapshot == null || now - snapshot.lastSeenAt() > 300000)
      throw new IllegalArgumentException("Device is offline or has no recent heartbeat");
    var command =
        new DeviceCommand(
            UUID.randomUUID().toString(),
            requestKey,
            deviceKey,
            action,
            Map.copyOf(checked),
            "local",
            "PENDING",
            now,
            now + spec.timeoutSeconds() * 1000L,
            null,
            null,
            1);
    try {
      repository.create(command);
    } catch (org.springframework.dao.DuplicateKeyException e) {
      return sameRequest(repository.findRequest(deviceKey, requestKey), action, checked);
    }
    return command;
  }

  private DeviceCommand sameRequest(
      DeviceCommand existing, String action, Map<String, Object> params) {
    if (existing == null
        || !existing.actionCode().equals(action)
        || !existing.parameters().equals(params))
      throw new VersionConflictException("Idempotency key reused with a different request");
    return existing;
  }

  private DeviceCommand changed(
      DeviceCommand old, String status, Map<String, Object> result, Map<String, Object> late) {
    return new DeviceCommand(
        old.id(),
        old.idempotencyKey(),
        old.deviceKey(),
        old.actionCode(),
        old.parameters(),
        old.requester(),
        status,
        old.createdAt(),
        old.expiresAt(),
        result,
        late,
        old.version() + 1);
  }

  private void save(DeviceCommand old, DeviceCommand next) {
    if (!repository.update(next, old.version()))
      throw new VersionConflictException("Command changed");
    changes.append(
        new ChangeEvent(
            0,
            "command:" + next.id() + ":" + next.version(),
            "command.changed",
            next.deviceKey(),
            System.currentTimeMillis(),
            next.version(),
            Map.of("command", next)));
  }

  @Transactional
  public void published(String id) {
    var old = repository.lock(id);
    if (old == null) return;
    if (old.status().equals("PENDING"))
      save(old, changed(old, "DISPATCHED", old.result(), old.lateResult()));
    repository.markPublished(id);
  }

  @Transactional
  public void timeout(String id, long now) {
    var old = repository.lock(id);
    if (old != null
        && Set.of("PENDING", "DISPATCHED").contains(old.status())
        && old.expiresAt() <= now)
      save(old, changed(old, "TIMED_OUT", old.result(), old.lateResult()));
  }

  @Transactional
  public void reply(String topicDevice, String location, CommandReply reply) {
    long now = System.currentTimeMillis();
    if (reply == null
        || reply.version() != 1
        || !topicDevice.equals(reply.deviceKey())
        || reply.commandId() == null
        || reply.occurredAt() < 0
        || reply.occurredAt() > now + 300000
        || reply.result() == null
        || reply.properties() == null) throw new IllegalArgumentException("Invalid command reply");
    var old = repository.lock(reply.commandId());
    if (old == null
        || !old.deviceKey().equals(topicDevice)
        || !devices.require(topicDevice).location().equals(location))
      throw new IllegalArgumentException("Reply identity mismatch");
    if (Set.of("SUCCEEDED", "FAILED").contains(old.status())) return;
    if (old.status().equals("TIMED_OUT") || old.expiresAt() <= now) {
      if (old.lateResult() != null) return;
      save(
          old,
          changed(
              old,
              "TIMED_OUT",
              old.result(),
              Map.of(
                  "success",
                  reply.success(),
                  "result",
                  reply.result(),
                  "occurredAt",
                  reply.occurredAt())));
      return;
    }
    if (!reply.properties().isEmpty()) {
      var telemetry =
          DeviceEvent.builder()
              .messageId(DeviceEvent.stableId("command-state", old.id()))
              .deviceKey(topicDevice)
              .source(DeviceSource.MOCK)
              .location(Location.valueOf(location))
              .eventType(EventType.TELEMETRY)
              .occurredAt(reply.occurredAt())
              .receivedAt(now)
              .properties(reply.properties())
              .build();
      telemetry.validate();
      var device = devices.normalize(telemetry);
      history.append(
          new TelemetrySample(
              telemetry.getMessageId(),
              topicDevice,
              device.modelKey(),
              device.modelVersion(),
              telemetry.getOccurredAt(),
              now,
              telemetry.getProperties()));
      if (states.update(telemetry)) registry.record(telemetry);
    }
    save(old, changed(old, reply.success() ? "SUCCEEDED" : "FAILED", reply.result(), null));
  }
}
