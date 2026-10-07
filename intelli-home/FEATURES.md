# 家居扩展的实现与调试

入口协议见 [home-features.md](../contracts/home-features.md)，完整路径见 [openapi.yaml](../contracts/openapi.yaml)。

## 包职责

- domain/home：物模型、历史样本、页面、天气、晾晒任务、命令与变更信封；domain/rule/LaundryRule 为纯确定性收衣判断。
- application/service：DeviceCatalogService 校验能力；TelemetryHistoryService 查询/聚合；RealtimeService 基线；PageService 页面版本；RuleManagementService 配置目录/试运行；LaundryService 风险周期；CommandService 命令状态机。
- application/port/out：持久化、天气和命令发送接口；不绑定 MyBatis/MQTT。
- adapter/in/http：REST 和 SSE；adapter/in/mqtt 接入遥测与独立回执用例。模拟执行器仅在显式打开 mock-controls 后注册。
- adapter/out/persistence：MyBatis 仓储和 Mapper XML；adapter/out/messaging：MQTT 命令发送；adapter/out/agent：结构化 Python 天气桥接。
- resources/db/migration：Flyway V1–V4；原 schema.sql 不再在启动时运行。

## 运行

先启动工作区的 MySQL/Redis/RabbitMQ/EMQX。Java：`mvn spring-boot:run`；Python 按原 README 启动。HTTP 默认只监听本机。

全新数据库直接应用 V1–V4；已有项目库首次升级需显式 `ADOPT_EXISTING_SCHEMA=true`，先检查旧八张表，记录 baseline=1 后应用增量迁移。未知结构默认拒绝，不能拿此开关接管别的库。存在 Flyway 历史表的数据库无需再次启用。不要修改已应用的迁移脚本。

演示天气和控制：启动参数 `--intelli.extensions.weather-mode=mock --intelli.extensions.mock-controls=true`；真实天气桥接用 weather-mode=python，需 Python 已配置供应商，不能把未配置当作晴天。

运行 `scripts/demo-features.ps1` 创建独立演示设备、读取能力/趋势、保存与恢复页面，并返回 SSE 地址。脚本保留自己的演示数据供调试。

完整测试：`scripts/verify.ps1`，临时 RabbitMQ 使用 5673，验证后删除，只暂停它进行故障测试。需要 Maven 缓存、Docker 和 Python .venv。

断点建议：DeviceEventDispatcher（历史写入与旧状态判断）→ MyBatisDeviceRegistry（已接受快照及 change feed）；LaundryService.evaluate（风险周期）→ LaundryRule.evaluate；CommandService.submit/reply/timeout（命令最终状态）→ MockControlSimulator（设备去重）。SSE 从 home_change_event 读取已提交数据，与 Rabbit 通知消费独立。

## 维护边界

页面绑定和属性类型在服务层明确校验；JSON Schema 用于对外契约。数据库乐观版本防止覆盖，事件 clock 保证提交顺序，单实例命令 Outbox 持续重试；这些机制各自覆盖自己的范围。Redis 与数据库没有分布式事务，历史处理进度尚未持久化。SSE 有限保留、固定连接上限，不适合公网规模。

当前只做模拟 setPower，没有真实晾衣架动作。尚未提供前端页面渲染。规则编辑只能修改现有逻辑参数，不能生成任意代码。原 Python 通风/晾晒建议保留，自动收衣由 Java 独立判断。
