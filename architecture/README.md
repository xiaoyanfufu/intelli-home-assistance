# Intelli Home 架构

[打开交互架构图](intelli-home.html)。该文件自包含，可在本地浏览器打开；GitHub 文件页面不直接运行 HTML。README 中的 Mermaid 提供仓库内概览。

图覆盖设备接入、状态与规则、MyBatis 持久化、Outbox / RabbitMQ 通知、按需建议和独立日报。家居扩展与一致性边界在图下方说明卡中列出。

## 源码依据

图按当前工作区实现核对。Java 图中的服务和任务属于同一进程；箭头表示运行时调用或数据访问，不表示模块依赖。

| 机制 | 主要源码 |
|---|---|
| 遥测历史、状态、新鲜属性及规则编排 | `intelli-home/src/main/java/com/intelli/home/application/service/DeviceEventDispatcher.java` |
| 告警、Outbox、变更记录同事务 | `intelli-home/src/main/java/com/intelli/home/adapter/out/persistence/MyBatisAlertRepository.java` |
| 待投递轮询、发布确认及路由检查 | `intelli-home/src/main/java/com/intelli/home/adapter/out/messaging/OutboxPublisher.java` |
| 消费事务后手动 ACK | `intelli-home/src/main/java/com/intelli/home/adapter/in/messaging/NotificationConsumer.java` |
| 通知收据与外部发送 | `intelli-home/src/main/java/com/intelli/home/application/service/NotificationService.java` |
| 独立日报调度与聚合投递 | `intelli-home/src/main/java/com/intelli/home/config/DailyAdviceSchedulingConfig.java`、`application/service/DailyAdviceService.java` |
| SSE 基线及有限重放 | `intelli-home/src/main/java/com/intelli/home/adapter/in/http/RealtimeController.java`、`application/service/RealtimeService.java` |
| 提交顺序与变更游标 | `intelli-home/src/main/java/com/intelli/home/adapter/out/persistence/MyBatisChangeFeed.java` |
| 模拟控制、幂等及回执终态 | `intelli-home/src/main/java/com/intelli/home/application/service/CommandService.java` |
| Agent API 与天气、建议、日报图 | `intelli-home-agent/src/intelli_agent/main.py`、`weather.py`、`graph.py`、`daily.py` |

表中省略前缀的 Java 路径均以 `intelli-home/src/main/java/com/intelli/home/` 开始。项目改动尚未提交，图未绑定旧 HEAD；这里只提供工作区源码核对，不宣称提交级来源验证。

## 生成与校验

`intelli-home.architecture.json` 是可维护的图规格。使用 Archify 3.0.1 生成，自包含 HTML 已通过 showcase validate、deliver、strict check 和真实浏览器 browser-check，无诊断。未进行人工截图视觉审阅。

生成时的本机路径、浏览器和工具收据不纳入公开文件。`.archify/` 保留这些本地过程产物，Git 忽略；公开目录仅保留图规格、HTML 和本文档。

更新步骤：编辑 JSON，执行 Archify finalize，并用 `--out-dir` 将验证证据写入 `.archify/` 的新目录。生成器在 HTML 旁写入的 `*.delivery.json` 也被忽略。工具不会随项目运行。
