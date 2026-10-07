package com.intelli.home.adapter.out.persistence.mapper;

import com.intelli.home.domain.home.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface LaundryMapper {
  int create(LaundrySession session);

  List<LaundrySession> list();

  List<LaundrySession> lockActive();

  LaundrySession lock(@Param("id") String id);

  int update(@Param("session") LaundrySession session, @Param("expected") long expected);

  WeatherSnapshot weather();

  int saveWeather(WeatherSnapshot weather);
}
