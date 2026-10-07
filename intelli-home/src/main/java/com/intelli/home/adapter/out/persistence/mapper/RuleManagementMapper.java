package com.intelli.home.adapter.out.persistence.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RuleManagementMapper {
  Map<String, Object> find(@Param("code") String code);

  int update(
      @Param("code") String code,
      @Param("expected") long expected,
      @Param("enabled") boolean enabled,
      @Param("priority") int priority,
      @Param("params") String params);

  int recordRevision(
      @Param("code") String code,
      @Param("revision") long revision,
      @Param("definition") String definition,
      @Param("now") long now);

  List<String> revisions(@Param("code") String code);
}
