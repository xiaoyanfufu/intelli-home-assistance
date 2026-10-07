package com.intelli.home.adapter.out.persistence.mapper;

import com.intelli.home.adapter.out.persistence.model.DeviceSnapshotRow;
import com.intelli.home.domain.event.DeviceEvent;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceSnapshotMapper {

  int insertIfAbsent(DeviceEvent event);

  DeviceSnapshotRow lockByDeviceKey(@Param("deviceKey") String deviceKey);

  int update(DeviceSnapshotRow row);

  List<DeviceSnapshotRow> findAll();
}
