package com.intelli.home.adapter.out.persistence.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PageMapper {
  Map<String, Object> find(@Param("id") String id);

  List<Map<String, Object>> list();

  int create(
      @Param("id") String id,
      @Param("title") String title,
      @Param("definition") String definition,
      @Param("now") long now);

  int replace(
      @Param("id") String id,
      @Param("title") String title,
      @Param("definition") String definition,
      @Param("revision") long revision,
      @Param("expected") long expected,
      @Param("archived") boolean archived,
      @Param("now") long now);

  int recordRevision(
      @Param("id") String id,
      @Param("revision") long revision,
      @Param("definition") String definition,
      @Param("now") long now);

  String revision(@Param("id") String id, @Param("revision") long revision);

  List<Long> revisions(@Param("id") String id);
}
