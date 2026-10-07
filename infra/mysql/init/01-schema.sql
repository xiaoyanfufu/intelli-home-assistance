-- intelli-home 初始表结构
-- 旧版结构参考，不再挂载至 docker-entrypoint-initdb.d。
-- 新库通过 Java Flyway 迁移初始化；已有库升级不得删除数据卷。

USE intelli_home;

-- ── 设备表：一个物理节点（室内节点 / 阳台节点） ───────────────
CREATE TABLE IF NOT EXISTS device (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_key      VARCHAR(64)  NOT NULL COMMENT '设备唯一标识，来自平台侧 deviceId',
    device_name     VARCHAR(64)  NOT NULL COMMENT '设备名称，如 室内节点',
    source          VARCHAR(32)  NOT NULL COMMENT '接入来源：MOCK / MQTT / HUAWEI_IOT',
    location        VARCHAR(32)  NOT NULL COMMENT '安装位置：INDOOR / BALCONY',
    online          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '在线状态，由心跳/离线事件维护',
    last_seen_at    DATETIME     NULL COMMENT '最近一次上报时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_key (device_key),
    KEY idx_source_location (source, location)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备台账';

-- ── 设备状态表：属性当前值的落库快照 ─────────────────────────
-- 热路径读的是 Redis，这张表用于重启后回填缓存与历史追溯。
CREATE TABLE IF NOT EXISTS device_state (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    device_key      VARCHAR(64)  NOT NULL COMMENT '设备唯一标识',
    property_key    VARCHAR(64)  NOT NULL COMMENT '物模型属性名，如 temperature',
    property_value  VARCHAR(255) NOT NULL COMMENT '属性值（统一按字符串存，用时再转型）',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_property (device_key, property_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备属性最新值';

-- ── 规则配置表：让规则可动态增删，不用改代码重启 ─────────────
CREATE TABLE IF NOT EXISTS rule_config (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    rule_code       VARCHAR(64)  NOT NULL COMMENT '规则编码，对应一个 Rule 实现',
    rule_name       VARCHAR(64)  NOT NULL COMMENT '规则名称，如 火灾隐患',
    scene           VARCHAR(32)  NOT NULL COMMENT '场景：FIRE / WINDOW / DRYING / OFFLINE',
    enabled         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    priority        INT          NOT NULL DEFAULT 100 COMMENT '优先级，数值越小越先执行',
    params_json     JSON         NULL COMMENT '规则参数，如阈值 {"temp":45,"smoke":300}',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_code (rule_code),
    KEY idx_scene_enabled (scene, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则配置';

-- ── 告警事件表：RabbitMQ 消费端的落库目标 ────────────────────
-- message_id 唯一，用于消费幂等——重复投递不会产生重复告警。
CREATE TABLE IF NOT EXISTS alert_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    message_id      VARCHAR(64)  NOT NULL COMMENT '消息唯一 ID，用于幂等',
    device_key      VARCHAR(64)  NOT NULL COMMENT '触发设备',
    rule_code       VARCHAR(64)  NOT NULL COMMENT '命中规则',
    scene           VARCHAR(32)  NOT NULL COMMENT '场景',
    level           VARCHAR(16)  NOT NULL COMMENT '告警级别：INFO / WARN / CRITICAL',
    title           VARCHAR(128) NOT NULL COMMENT '告警标题',
    content         VARCHAR(512) NULL COMMENT '告警内容，含触发时的采样值',
    occurred_at     DATETIME     NOT NULL COMMENT '事件发生时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_id (message_id),
    KEY idx_device_occurred (device_key, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警事件';

-- ── 初始化几条规则配置，Day 3 规则引擎直接可用 ────────────────
INSERT INTO rule_config (rule_code, rule_name, scene, priority, params_json) VALUES
    ('FIRE_RISK',  '火灾隐患',   'FIRE',    10, JSON_OBJECT('temperature', 45, 'smoke', 300)),
    ('WINDOW_SUGGEST', '开窗建议', 'WINDOW',  20, JSON_OBJECT('humidityDiff', 15, 'tempDiff', 3)),
    ('DRYING_SUGGEST', '晾晒建议', 'DRYING',  30, JSON_OBJECT('balconyHumidity', 70)),
    ('DEVICE_OFFLINE', '设备离线', 'OFFLINE', 40, JSON_OBJECT('offlineSeconds', 300))
ON DUPLICATE KEY UPDATE rule_name = VALUES(rule_name);
