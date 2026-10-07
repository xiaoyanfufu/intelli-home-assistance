package com.intelli.home.adapter.out.redis;

import com.intelli.home.application.port.out.DeviceStateStore;
import com.intelli.home.domain.device.*;
import com.intelli.home.domain.event.DeviceEvent;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisDeviceStateStore implements DeviceStateStore {
  private final StringRedisTemplate redis;
  private static final String PREFIX = "home:v2:state:", INDEX = "home:v2:devices";
  private static final DefaultRedisScript<Long> UPDATE =
      new DefaultRedisScript<>(
          """
        local old=tonumber(redis.call('HGET',KEYS[1],'occurredAt') or '-1')
        if tonumber(ARGV[1])<old then return 0 end
        local duplicate=redis.call('HGET',KEYS[1],'eventId')==ARGV[2]
        redis.call('HSET',KEYS[1],'occurredAt',ARGV[1],'eventId',ARGV[2],'location',ARGV[3])
        if not duplicate then redis.call('HSET',KEYS[1],'lastSeenAt',ARGV[4]) end
        for i=6,#ARGV,2 do
          redis.call('HSET',KEYS[1],'p:'..ARGV[i],ARGV[i+1])
          if not duplicate then redis.call('HSET',KEYS[1],'t:'..ARGV[i],ARGV[1]) end
        end
        redis.call('EXPIRE',KEYS[1],21600)
        redis.call('ZADD',KEYS[2],ARGV[4],ARGV[5])
        return 1
        """,
          Long.class);

  public boolean update(DeviceEvent event) {
    var args =
        new ArrayList<String>(
            List.of(
                event.getOccurredAt().toString(),
                event.getMessageId(),
                event.getLocation().name(),
                event.getReceivedAt().toString(),
                event.getDeviceKey()));
    event
        .getProperties()
        .forEach(
            (key, value) -> {
              args.add(key);
              args.add(String.valueOf(value));
            });
    Long result =
        redis.execute(UPDATE, List.of(PREFIX + event.getDeviceKey(), INDEX), args.toArray());
    return result != null && result == 1;
  }

  public Map<String, DeviceSnapshot> snapshots() {
    long now = System.currentTimeMillis();
    redis.opsForZSet().removeRangeByScore(INDEX, 0, now - 21600000);
    Set<String> keys = redis.opsForZSet().range(INDEX, 0, -1);
    var result = new TreeMap<String, DeviceSnapshot>();
    if (keys == null) return Map.of();
    for (String key : keys) {
      var raw = redis.opsForHash().entries(PREFIX + key);
      if (raw.isEmpty()) continue;
      var props = new HashMap<String, Object>();
      var times = new HashMap<String, Long>();
      raw.forEach(
          (k, v) -> {
            String field = k.toString();
            if (field.startsWith("p:")) props.put(field.substring(2), v.toString());
            if (field.startsWith("t:")) times.put(field.substring(2), Long.parseLong(v.toString()));
          });
      result.put(
          key,
          new DeviceSnapshot(
              key,
              String.valueOf(raw.getOrDefault("eventId", "")),
              Location.valueOf(raw.get("location").toString()),
              Long.parseLong(raw.get("occurredAt").toString()),
              Long.parseLong(raw.get("lastSeenAt").toString()),
              props,
              times));
    }
    return Map.copyOf(result);
  }

  public void restore(DeviceSnapshot snapshot) {
    // Atomic restore only when cache is absent; never overwrite a concurrent live event.
    var fields = new ArrayList<String>();
    fields.addAll(
        List.of(
            "location",
            snapshot.location().name(),
            "eventId",
            snapshot.eventId(),
            "occurredAt",
            Long.toString(snapshot.occurredAt()),
            "lastSeenAt",
            Long.toString(snapshot.lastSeenAt())));
    snapshot
        .properties()
        .forEach(
            (k, v) ->
                fields.addAll(
                    List.of(
                        "p:" + k,
                        String.valueOf(v),
                        "t:" + k,
                        Long.toString(snapshot.propertyTimes().getOrDefault(k, 0L)))));
    var script =
        new DefaultRedisScript<Long>(
            "if redis.call('EXISTS',KEYS[1])==1 then return 0 end; redis.call('HSET',KEYS[1],unpack(ARGV)); redis.call('EXPIRE',KEYS[1],21600); redis.call('ZADD',KEYS[2],redis.call('HGET',KEYS[1],'lastSeenAt'),KEYS[3]); return 1",
            Long.class);
    redis.execute(
        script,
        List.of(PREFIX + snapshot.deviceKey(), INDEX, snapshot.deviceKey()),
        fields.toArray());
  }
}
