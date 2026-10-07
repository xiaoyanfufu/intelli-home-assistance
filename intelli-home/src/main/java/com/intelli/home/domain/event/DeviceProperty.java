package com.intelli.home.domain.event;

/**
 * 物模型属性名常量。
 *
 * <p>集中定义避免各处硬编码字符串拼错，也方便面试时一句话讲清物模型边界。
 */
public final class DeviceProperty {

  private DeviceProperty() {}

  // ── 环境量 ──────────────────────────────
  public static final String TEMPERATURE = "temperature";
  public static final String HUMIDITY = "humidity";
  public static final String SMOKE = "smoke";
  public static final String LIGHT = "light";
  public static final String RAIN = "rain";

  // ── 状态量 ──────────────────────────────
  public static final String ONLINE = "online";
  public static final String BATTERY = "battery";
  public static final String LAST_SEEN_AT = "lastSeenAt";
}
