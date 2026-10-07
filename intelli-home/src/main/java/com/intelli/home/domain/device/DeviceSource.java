package com.intelli.home.domain.device;

/**
 * 设备接入来源。
 *
 * <p>存在的意义：接入层的差异（MQTT 是通信协议，华为云 IoT 是平台能力）在这里归一化， 规则引擎只认 DeviceEvent，不感知外部接入细节。
 */
public enum DeviceSource {

  /** 本地模拟设备：不依赖任何云平台，保证主链路先跑通 */
  MOCK,

  /** 直连 MQTT Broker（EMQX） */
  MQTT,

  /** 华为云 IoT 平台 */
  HUAWEI_IOT
}
