package com.intelli.home.domain.rule;

/** 规则场景 —— 与 rule_config.scene 字段一一对应。 */
public enum RuleScene {

  /** 火灾隐患：烟雾 + 温度双条件 */
  FIRE,

  /** 开窗建议：室内外温湿度差 */
  WINDOW,

  /** 晾晒建议：阳台湿度 + 天气预报 */
  DRYING,

  /** 设备离线提醒 */
  OFFLINE,
  LAUNDRY
}
