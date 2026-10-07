package com.intelli.home.adapter.out.persistence.mapper;

import com.intelli.home.domain.alert.AlertEvent;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlertMapper {

  int initializeCooldown(@Param("deviceKey") String deviceKey, @Param("ruleCode") String ruleCode);

  Long lockCooldown(@Param("deviceKey") String deviceKey, @Param("ruleCode") String ruleCode);

  boolean exists(@Param("messageId") String messageId);

  int insert(AlertEvent alert);

  int updateCooldown(
      @Param("deviceKey") String deviceKey,
      @Param("ruleCode") String ruleCode,
      @Param("occurredAt") long occurredAt);

  List<AlertEvent> findRecent(@Param("limit") int limit);
}
