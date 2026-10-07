# MyBatis 持久化

SQL 集中在本目录；表结构由 ../db/schema.sql 定义。更改查询或列映射时从对应 XML 开始，不需要在业务服务里拼接 SQL。

| Mapper | 表 | 主要职责 |
|---|---|---|
| DeviceSnapshotMapper | home_device_snapshot | 初始行插入、按设备加锁、快照更新与读取 |
| RuleConfigMapper | rule_config | 加载规则参数 |
| AlertMapper | alert_event、alert_cooldown | 告警幂等、冷却行锁、告警写入与查询 |
| OutboxMapper | alert_outbox | 写入待发布消息、扫描、发布结果记录 |
| NotificationReceiptMapper | notification_delivery | 幂等认领通知 |

Java 接口位于 adapter/out/persistence/mapper，@Mapper 由 Starter 自动扫描，Spring 创建代理并注入适配器。XML namespace 对应接口全名，语句 id 对应方法名；参数使用 #{} 绑定，不使用文本拼接。

数据流：应用出站接口 → MyBatis* 适配器 → Mapper 代理 → Mapper XML → MySQL。

DeviceSnapshotRow、RuleConfigRow、OutboxRow 是数据库行模型。设备属性与属性时间仍使用 MySQL JSON 列，适配器通过 ObjectMapper 转换为领域数据。简单的告警查询通过 resultMap 映射 AlertEvent；领域类无需增加 MyBatis 注解。

事务保持原有行为：

- MyBatisDeviceRegistry.record 在同一 Spring 事务内初始化设备、SELECT FOR UPDATE、合并并更新快照。
- MyBatisAlertRepository.record 将冷却锁、告警写入和 Outbox 写入放在同一事务。
- NotificationService.handle 的事务覆盖通知收据 Mapper 调用；发送失败回滚收据。
- OutboxPublisher 不持有跨网络发布的数据库事务，确认成功后更新发布标记；仍是至少一次投递。

所有 Mapper 使用同一 DataSource 和 Spring 事务管理，不手动创建 SqlSession、commit 或 rollback。告警时间的 TypeHandler 使用 JDBC Timestamp 转换，SQL DATETIME 保持秒精度；设备快照的时间列仍为 BIGINT 毫秒。

测试中的 JdbcTemplate 用来独立检查表数据、构造故障并清理本次测试设备，生产代码已移除 JdbcTemplate。
