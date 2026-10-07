package com.intelli.home.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.home.adapter.out.persistence.mapper.DeviceSnapshotMapper;
import com.intelli.home.adapter.out.persistence.model.DeviceSnapshotRow;
import com.intelli.home.application.port.out.ChangeFeed;
import com.intelli.home.application.port.out.DeviceRegistry;
import com.intelli.home.domain.device.*;
import com.intelli.home.domain.event.DeviceEvent;
import com.intelli.home.domain.home.ChangeEvent;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MyBatisDeviceRegistry implements DeviceRegistry {
  private final DeviceSnapshotMapper snapshots;
  private final ObjectMapper json;
  private final ChangeFeed changes;

  @Transactional
  public void record(DeviceEvent event) {
    snapshots.insertIfAbsent(event);
    var row = snapshots.lockByDeviceKey(event.getDeviceKey());
    if (row.getOccurredAt() > event.getOccurredAt()
        || event.getMessageId().equals(row.getEventId())) return;
    try {
      Map<String, Object> props =
          json.readValue(row.getPropertiesJson(), new TypeReference<Map<String, Object>>() {});
      Map<String, Long> times =
          json.readValue(row.getTimesJson(), new TypeReference<Map<String, Long>>() {});
      props.putAll(event.getProperties());
      event.getProperties().keySet().forEach(k -> times.put(k, event.getOccurredAt()));
      row.setLocation(event.getLocation().name());
      row.setSource(event.getSource().name());
      row.setEventId(event.getMessageId());
      row.setOccurredAt(event.getOccurredAt());
      row.setLastSeenAt(event.getReceivedAt());
      row.setPropertiesJson(json.writeValueAsString(props));
      row.setTimesJson(json.writeValueAsString(times));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot serialize device snapshot", e);
    }
    snapshots.update(row);
    changes.append(
        new ChangeEvent(
            0,
            "state:" + event.getMessageId(),
            "device.state.changed",
            event.getDeviceKey(),
            System.currentTimeMillis(),
            row.getVersion() + 1,
            Map.of("snapshot", toSnapshot(row))));
  }

  public List<DeviceSnapshot> snapshots() {
    return snapshots.findAll().stream().map(this::toSnapshot).toList();
  }

  private DeviceSnapshot toSnapshot(DeviceSnapshotRow row) {
    try {
      return new DeviceSnapshot(
          row.getDeviceKey(),
          row.getEventId(),
          Location.valueOf(row.getLocation()),
          row.getOccurredAt(),
          row.getLastSeenAt(),
          json.readValue(row.getPropertiesJson(), new TypeReference<Map<String, Object>>() {}),
          json.readValue(row.getTimesJson(), new TypeReference<Map<String, Long>>() {}));
    } catch (Exception e) {
      throw new IllegalStateException("Invalid stored snapshot", e);
    }
  }
}
