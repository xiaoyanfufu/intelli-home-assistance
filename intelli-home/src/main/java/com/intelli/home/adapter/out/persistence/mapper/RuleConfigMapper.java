package com.intelli.home.adapter.out.persistence.mapper;

import com.intelli.home.adapter.out.persistence.model.RuleConfigRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RuleConfigMapper {

  List<RuleConfigRow> findAll();
}
