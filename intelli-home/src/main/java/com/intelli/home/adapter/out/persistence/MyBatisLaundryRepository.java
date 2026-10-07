package com.intelli.home.adapter.out.persistence;

import com.intelli.home.adapter.out.persistence.mapper.LaundryMapper;
import com.intelli.home.application.port.out.LaundryRepository;
import com.intelli.home.domain.home.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisLaundryRepository implements LaundryRepository {
  private final LaundryMapper mapper;

  public void create(LaundrySession session) {
    mapper.create(session);
  }

  public List<LaundrySession> list() {
    return mapper.list();
  }

  public List<LaundrySession> lockActive() {
    return mapper.lockActive();
  }

  public LaundrySession lock(String id) {
    return mapper.lock(id);
  }

  public boolean update(LaundrySession session, long expected) {
    return mapper.update(session, expected) == 1;
  }

  public WeatherSnapshot weather() {
    return mapper.weather();
  }

  public void saveWeather(WeatherSnapshot weather) {
    mapper.saveWeather(weather);
  }
}
