package com.intelli.home.domain.device;

/** 设备安装位置。开窗、晾晒这些建议必须区分室内外，所以位置是一等字段。 */
public enum Location {

  /** 室内节点：温湿度、烟雾 */
  INDOOR,

  /** 阳台节点：微气候，温湿度、光照、降雨 */
  BALCONY
}
