package com.intelli.home;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 个人智能居家助手 —— 后端入口。
 *
 * <p>主链路：统一接入 → DeviceEvent → Redis 状态 → RuleEngine → 告警与 Outbox → RabbitMQ → 通知
 *
 * <p>Agent 链路：DeviceEvent + 天气 → LangGraph → Recommendation
 */
@SpringBootApplication
public class IntelliHomeApplication {
  public static void main(String[] args) {
    SpringApplication.run(IntelliHomeApplication.class, args);
  }
}
