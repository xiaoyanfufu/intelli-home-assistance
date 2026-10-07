package com.intelli.home.adapter.out.persistence.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CommandMapper {
  int create(
      @Param("command") com.intelli.home.domain.home.DeviceCommand command,
      @Param("parameters") String parameters);

  int enqueue(@Param("id") String id);

  Map<String, Object> find(@Param("id") String id);

  Map<String, Object> request(@Param("deviceKey") String deviceKey, @Param("key") String key);

  Map<String, Object> lock(@Param("id") String id);

  List<Map<String, Object>> list(@Param("deviceKey") String deviceKey);

  List<Map<String, Object>> pending();

  List<Map<String, Object>> overdue(@Param("now") long now);

  int update(
      @Param("command") com.intelli.home.domain.home.DeviceCommand command,
      @Param("expected") long expected,
      @Param("result") String result,
      @Param("late") String late);

  int markPublished(@Param("id") String id);

  int failure(@Param("id") String id, @Param("error") String error);

  int mockInsert(
      @Param("id") String id, @Param("deviceKey") String deviceKey, @Param("result") String result);

  String mockResult(@Param("id") String id, @Param("deviceKey") String deviceKey);
}
