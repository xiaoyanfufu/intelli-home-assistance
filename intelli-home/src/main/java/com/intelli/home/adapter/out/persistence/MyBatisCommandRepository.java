package com.intelli.home.adapter.out.persistence;

import static com.intelli.home.adapter.out.persistence.JsonRows.*;

import com.intelli.home.adapter.out.persistence.mapper.CommandMapper;
import com.intelli.home.application.port.out.CommandRepository;
import com.intelli.home.domain.home.DeviceCommand;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MyBatisCommandRepository implements CommandRepository {
  private final CommandMapper mapper;
  private final JsonRows json;

  private DeviceCommand command(Map<String, Object> row) {
    if (row == null) return null;
    return new DeviceCommand(
        text(row, "id"),
        text(row, "idempotency_key"),
        text(row, "device_key"),
        text(row, "action_code"),
        json.map(row.get("parameters")),
        text(row, "requester"),
        text(row, "status"),
        number(row, "created_at"),
        number(row, "expires_at"),
        row.get("result") == null ? null : json.map(row.get("result")),
        row.get("late_result") == null ? null : json.map(row.get("late_result")),
        number(row, "version"));
  }

  @Transactional
  public void create(DeviceCommand command) {
    mapper.create(command, json.write(command.parameters()));
    mapper.enqueue(command.id());
  }

  public DeviceCommand find(String id) {
    return command(mapper.find(id));
  }

  public DeviceCommand findRequest(String deviceKey, String key) {
    return command(mapper.request(deviceKey, key));
  }

  public DeviceCommand lock(String id) {
    return command(mapper.lock(id));
  }

  public List<DeviceCommand> list(String deviceKey) {
    return mapper.list(deviceKey).stream().map(this::command).toList();
  }

  public List<DeviceCommand> pending() {
    return mapper.pending().stream().map(this::command).toList();
  }

  public List<DeviceCommand> overdue(long now) {
    return mapper.overdue(now).stream().map(this::command).toList();
  }

  public boolean update(DeviceCommand command, long expected) {
    return mapper.update(
            command,
            expected,
            command.result() == null ? null : json.write(command.result()),
            command.lateResult() == null ? null : json.write(command.lateResult()))
        == 1;
  }

  public void markPublished(String id) {
    mapper.markPublished(id);
  }

  public void failure(String id, String error) {
    mapper.failure(id, error);
  }

  @Transactional
  public Map<String, Object> executeMockOnce(
      String id, String deviceKey, Map<String, Object> result) {
    mapper.mockInsert(id, deviceKey, json.write(result));
    var raw = mapper.mockResult(id, deviceKey);
    if (raw == null) throw new IllegalArgumentException("Command belongs to another device");
    return json.map(raw);
  }
}
