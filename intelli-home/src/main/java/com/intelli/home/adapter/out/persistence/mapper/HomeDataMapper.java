package com.intelli.home.adapter.out.persistence.mapper;

import com.intelli.home.domain.home.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface HomeDataMapper {
  int insertModel(
      @Param("modelKey") String modelKey,
      @Param("version") int version,
      @Param("definition") String definition);

  String model(@Param("modelKey") String modelKey, @Param("version") int version);

  int register(HomeDevice device);

  HomeDevice device(@Param("deviceKey") String deviceKey);

  List<HomeDevice> devices();

  int insertTelemetry(
      @Param("sample") TelemetrySample sample, @Param("properties") String properties);

  List<Map<String, Object>> telemetry(
      @Param("deviceKey") String deviceKey,
      @Param("from") long from,
      @Param("to") long to,
      @Param("afterTime") long afterTime,
      @Param("afterId") String afterId,
      @Param("limit") int limit);

  int cleanupTelemetry(@Param("before") long before, @Param("limit") int limit);

  int insertChange(@Param("event") ChangeEvent event, @Param("payload") String payload);

  List<Map<String, Object>> changes(@Param("cursor") long cursor, @Param("limit") int limit);

  Long latestChange();

  Long earliestChange();

  Integer lockChangeClock();

  int cleanupChanges(@Param("before") long before, @Param("limit") int limit);
}
