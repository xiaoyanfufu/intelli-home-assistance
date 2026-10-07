package com.intelli.home.adapter.out.persistence.mapper;

import com.intelli.home.adapter.out.persistence.model.OutboxRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OutboxMapper {

  int insert(@Param("messageId") String messageId, @Param("payload") String payload);

  List<OutboxRow> findPending(@Param("limit") int limit);

  int markPublished(@Param("messageId") String messageId);

  int recordFailure(@Param("messageId") String messageId, @Param("error") String error);
}
