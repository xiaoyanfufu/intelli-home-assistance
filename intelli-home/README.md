# intelli-home Java 后端

Java 21、Spring Boot 3.5、MyBatis/MySQL、Redis、RabbitMQ、Paho MQTT。实现设备遥测、状态与历史、安全告警、家居扩展和 Python Agent 协作。

许可证：[MIT](LICENSE)。

## 分层

| 包 | 职责 |
|---|---|
| domain | 事件、快照、纯规则与家居模型，不依赖 Spring 和中间件 |
| application/port | 应用入口与外部能力契约 |
| application/service | 事件编排、离线检测、配置刷新、建议、日报与家居用例 |
| adapter/in | HTTP、SSE、MQTT、RabbitMQ 入站 |
| adapter/out | Redis、MyBatis、RabbitMQ、MQTT、Python HTTP、日志与 SMTP |
| config | 装配、属性、调度、迁移和消息拓扑 |

依赖方向为适配器 → 应用 → 领域。接口隔离协议与外部实现，不为每个内部类创建接口。Mapper SQL 和字段映射集中在 `src/main/resources/mapper/`，见 [持久化说明](src/main/resources/mapper/README.md)。

## 事件与告警链路

```text
HTTP Mock / MQTT → DeviceEventDispatcher
  → 物模型校验与原始遥测历史
  → Redis 原子状态更新（拒绝旧数据）→ MySQL 台账与快照
  → 新鲜属性 + 单份规则配置 → 领域规则
  → 告警与 Outbox 同事务写 MySQL
  → Outbox 轮询 → Broker confirm + mandatory routing 检查
  → RabbitMQ 消费 → 通知收据事务 → commit → 手动 ACK
```

规则输入区分原始事件和合并后的有效属性；本次评估使用同一份不可变配置快照。首次配置加载失败阻止启动，后续刷新失败保留最近有效配置。参数可编辑，新增规则逻辑需重新发布。

## 启动

服务位于统一仓库的 `intelli-home/`，根目录包含契约、基础设施和演示脚本，布局见 [项目 README](../README.md)。

```powershell
# 工作区根目录
docker compose -f infra/docker-compose.yml up -d --wait

# 本服务目录
mvn clean package
java -jar target/intelli-home-0.1.0-SNAPSHOT.jar
```

默认 HTTP 监听 `127.0.0.1:8080`。全新数据库由 Flyway V1–V4 初始化；旧数据库接管见 [FEATURES.md](FEATURES.md)。不要修改已应用迁移或通过删除数据卷升级。

Python 启动后使用 `AGENT_ENABLED=true` 和 `AGENT_BASE_URL=http://localhost:8000` 启用 Agent。无安全告警且数据新鲜时才请求生活建议，不为每条遥测调用模型。协议见 [Agent API](../contracts/agent-api.md)。

## 接口与扩展

| 接口 | 行为 |
|---|---|
| POST /api/mock/scenes/NORMAL、FIRE、DRYING | 本地模拟场景 |
| POST /api/mock/devices/{key}/events?location=INDOOR | 上报设备属性 |
| GET /api/devices/states | 设备快照与属性采样时间 |
| GET /api/alerts?limit=20 | 已持久化告警 |
| POST /api/devices/{key}/recommendation | 显式生成建议 |
| POST /api/daily-advice/run?dryRun=true | 日报预览，不投递或写收据 |
| POST /api/daily-advice/run | 当天日报投递，检查返回 status |
| POST /api/notifications/email | 默认关闭的固定收件人调试入口 |

物模型、历史、SSE、页面版本、规则编辑、收衣与模拟控制见 [家居扩展](../contracts/home-features.md)；完整 REST 路径见 [OpenAPI](../contracts/openapi.yaml)。MQTT 的主题、时间戳、稳定消息 ID、16 KiB 限制和命令回执见 [MQTT 协议](../contracts/mqtt/README.md)。

## 通知与日报

通知默认日志；显式选择 email 且启用邮件后使用同步 SMTP。失败回滚通知收据，MQ 消费最多尝试 3 次，耗尽后进入死信。连接、读取和写入分别配置超时，SMTP 接受不代表用户收件，也不能消除接受后事务失败造成的重复发送。

日报默认关闭，使用独立 `dailyAdviceScheduler`；其他任务使用 `taskScheduler`。默认在 `07:00–09:59 Asia/Shanghai` 每 15 分钟尝试，成功收据后跳过，失败在窗口内重试。日报不经过 MQ / Outbox，不写入告警列表。`intelli.scheduling.enabled=false` 关闭定时任务，手动预览仍可用。配置见 [邮件与日报](../contracts/daily-advice.md)。

## 状态和一致性边界

- Redis 使用 Hash 与 ZSet 索引，不使用 KEYS；按设备隔离。属性新鲜度为 5 分钟，缓存 TTL 为 6 小时。
- 最新属性按源事件时间处理乱序，离线根据接收时间判断；重复最近事件不刷新心跳。启动时从 MySQL 恢复 Redis，离线扫描持久台账。
- 告警冷却 60 秒，与消息幂等分别处理；同一离线周期使用稳定事件身份。
- MQ 故障不阻止告警落库；Outbox 每 3 秒尝试，确认或路由失败保留待投递记录。
- Redis 与 MySQL 无统一事务，没有持久入站事件日志；设备处理不保证跨进程崩溃零丢失。
- 当前支持单家庭、单实例。没有生产鉴权、Outbox 多实例抢占或外部通知恰好一次。

## 验证与调试

```powershell
mvn test
mvn spotless:check
mvn test '-Dhome.integration=true'
```

第一条只运行无需中间件的测试，第三条需要工作区中间件。完整故障验证使用根目录 `scripts/verify.ps1`，创建独立临时 Broker，不暂停正常 Broker。

IntelliJ 调试 MQ 时可启用 debug profile，并将断点 Suspend 设置为 Thread。debug profile 请求 60 秒心跳，正常配置为 5 秒；实际值由客户端与 Broker 协商。暂停整个 JVM 会阻止心跳，恢复后 ACK 可能遇到关闭的 Channel，消息需重新投递并依靠收据去重。通知事务中的断点也会延长数据库持锁时间。
