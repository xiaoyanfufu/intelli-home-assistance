package com.intelli.home.adapter.out.persistence;

import static com.intelli.home.adapter.out.persistence.JsonRows.*;

import com.intelli.home.adapter.out.persistence.mapper.HomeDataMapper;
import com.intelli.home.application.port.out.ChangeFeed;
import com.intelli.home.domain.home.ChangeEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MyBatisChangeFeed implements ChangeFeed {
  private final HomeDataMapper mapper;
  private final JsonRows json;

  @Transactional
  public void append(ChangeEvent event) {
    // Serialize cursor assignment through commit, so a later commit cannot hide an earlier event.
    mapper.lockChangeClock();
    mapper.insertChange(event, json.write(event.payload()));
  }

  public List<ChangeEvent> after(long cursor, int limit) {
    return mapper.changes(cursor, limit).stream()
        .map(
            r ->
                new ChangeEvent(
                    number(r, "id"),
                    text(r, "event_key"),
                    text(r, "type"),
                    text(r, "device_key"),
                    number(r, "occurred_at"),
                    number(r, "resource_version"),
                    json.map(r.get("payload"))))
        .toList();
  }

  public long latest() {
    return mapper.latestChange();
  }

  public long earliest() {
    return mapper.earliestChange();
  }

  public int cleanup(long before, int limit) {
    return mapper.cleanupChanges(before, limit);
  }
}
