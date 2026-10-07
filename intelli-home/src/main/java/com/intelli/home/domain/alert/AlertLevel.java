package com.intelli.home.domain.alert;

/** 告警级别。 */
public enum AlertLevel {

  /** 提示：如晾晒建议 */
  INFO,

  /** 警告：如开窗建议、设备离线 */
  WARN,

  /** 严重：如火灾隐患 */
  CRITICAL
}
