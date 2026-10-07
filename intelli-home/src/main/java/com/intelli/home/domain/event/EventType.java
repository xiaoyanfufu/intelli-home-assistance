package com.intelli.home.domain.event;

/** 统一事件类型。 */
public enum EventType {

  /** 设备属性上报：温湿度、烟雾等 */
  TELEMETRY,

  /** 设备上下线、心跳等状态变化 */
  STATUS,

  /** 命令下发回执 */
  COMMAND_REPLY
}
