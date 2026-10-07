package com.intelli.home.adapter.out.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationReceiptMapper {

  int claim(@Param("messageId") String messageId);

  boolean exists(@Param("messageId") String messageId);
}
