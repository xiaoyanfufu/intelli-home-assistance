package com.intelli.home.domain.alert;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 告警事件 —— 规则命中后的产物，落库并投递到 RabbitMQ。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent {

  /** 幂等键：同一设备同一时刻的同一规则只应产生一条告警 */
  private String messageId;

  private String deviceKey;

  /** 命中的规则编码 */
  private String ruleCode;

  /** 场景 */
  private String scene;

  private AlertLevel level;

  private String title;

  /** 告警内容，建议把触发时的采样值一并写进来，便于事后复盘 */
  private String content;

  /** 事件发生时间（毫秒时间戳） */
  private Long occurredAt;
}
