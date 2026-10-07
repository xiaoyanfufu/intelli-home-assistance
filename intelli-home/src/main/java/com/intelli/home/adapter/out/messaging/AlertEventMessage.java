package com.intelli.home.adapter.out.messaging;

import com.intelli.home.domain.alert.AlertEvent;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 投递到 RabbitMQ 的消息体。
 *
 * <p>用包装类而不是直接发 AlertEvent，是为了给消息带上独立的 {@code messageId} 与投递元信息， 消费端凭 {@code messageId} 做幂等（唯一索引 +
 * 存在即跳过）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEventMessage {

  /** 消息唯一 ID，消费幂等的依据 */
  private String messageId;

  /** 生产者标识，便于排查是哪个实例发的 */
  private String producer;

  /** 投递时间（毫秒时间戳） */
  private Long publishedAt;

  /** 业务负载 */
  private AlertEvent payload;

  public static AlertEventMessage of(AlertEvent event, String producer) {
    return AlertEventMessage.builder()
        .messageId(
            event.getMessageId() != null ? event.getMessageId() : UUID.randomUUID().toString())
        .producer(producer)
        .publishedAt(System.currentTimeMillis())
        .payload(event)
        .build();
  }
}
