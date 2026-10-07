package com.intelli.home.application.port.out;

import com.intelli.home.domain.home.*;
import java.util.List;

public interface LaundryRepository {
  void create(LaundrySession session);

  List<LaundrySession> list();

  List<LaundrySession> lockActive();

  LaundrySession lock(String id);

  boolean update(LaundrySession session, long expected);

  WeatherSnapshot weather();

  void saveWeather(WeatherSnapshot weather);
}
