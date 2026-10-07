# 家居扩展契约 v1

REST 完整路径清单见 openapi.yaml；此目录用于设备端、页面生成方和后端共同审阅协议，不要求设备开发者阅读 Java 源码。JSON Schema 是文档和契约校验输入；运行时由 Java 应用服务显式校验，未引入通用 Schema 执行器。

## 时间、错误和版本

时间均为 Unix 毫秒，历史范围为 `[from,to)`，最大 31 天。原始查询 limit 1–500，返回 items 和 nextCursor，游标不透明。聚合按 UTC 采样时间整分钟/整小时分桶，不补空桶，不把缺值补零；单次最多读取 10000 条样本和返回 2000 桶。

非法参数 400；乐观锁冲突 409；存储不可用 503。页面和规则写入头 `If-Match: 1` 使用当前实现的十进制 revision；不是带引号的 HTTP ETag。命令头 `Idempotency-Key` 在同设备内唯一，同键不同动作/参数返回 409。当前固定 local 用户，默认 HTTP 仅绑定 127.0.0.1，无公网身份认证。

## 能力与数据

environment:1 是只读温湿度/烟雾模型；mock-switch:1 增加 power:boolean 与 setPower(power:boolean)。温度 ℃、湿度 %，smoke 为未标定模拟读数，unit=null、calibrated=false。未知设备自动绑定 environment:1，未知属性保留为扩展值但不成为查询/控制能力。数值字符串兼容转换；部分上报的原始历史与合并后的最新快照分开。

模型 v1 先定义属性和动作；事件信封沿用既有 MQTT v1，不单独发布模型事件目录。属性没有独立可写标识，写入仅通过模型动作。动作回执统一采用 command-reply.schema.json，不为每动作重复声明应答模型。

## 首次同步及 SSE

先 GET /api/events/snapshot，获得 devices 与 cursor，再 GET /api/events/stream?cursor=...。设备状态读取该基线的 MySQL 快照，resourceVersion 单调递增。支持 deviceKey 筛选和 Last-Event-ID；头部优先于查询参数。事件信封字段 id/eventKey/type/deviceKey/occurredAt/resourceVersion/payload。类型包括 device.state.changed、alert.created、laundry.changed、command.changed。

记录与对应业务写入处于同一个 MySQL 事务；提交顺序通过 change clock 行锁序列化。SSE 轮询已提交记录，不消费通知队列。10 秒 heartbeat，默认 1 小时有限重放，最多 8 个客户端，每连接独立工作线程，连接 60 秒后重连；慢连接不会阻塞设备处理，但会占用一个连接名额。游标过期/超出范围收到 sync.required，客户端重新取基线。历史保留默认 30 天，每小时分批清理；可设置 intelli.retention.telemetry-days 和 events-hours。

## 页面

保存 PageDefinition，而非可执行 HTML/JS。schemaVersion=1，12 列布局，最多 30 个组件、64KiB。组件类型为 device-property/history-chart/alert-list/recommendation，受控 binding 可引用设备、属性、rangeSeconds、interval、limit；拒绝 SQL、文件路径或任意 URL。示例见 examples/environment-page.json。更新、归档、恢复都创建新 revision；恢复不会覆盖历史。

## 规则和收衣

实际可编辑规则只有 FIRE_RISK、DEVICE_OFFLINE、COLLECT_LAUNDRY；目录响应包含参数边界。GET /api/rules 返回持久版本与活动状态；刷新失败时保留旧有效快照，响应 active=false、activeRevision=-1，可等待定时刷新恢复。编辑不支持动态代码。试运行不保存告警或控制设备。

收衣使用独立家庭上下文：ACTIVE 阳台晾晒任务 + 新鲜结构化天气；不把天气塞入设备 properties。raining=true 或 rainProbability 达到阈值触发；缺失/过期不作确定判断。一次持续风险只提醒一次；风险解除后再次出现开启下一周期，结束任务停止提醒。当前周期重置策略固定，阈值和天气最大年龄可编辑；返潮和自动通风暂未扩展为独立自动规则。

intelli.extensions.weather-mode 默认 disabled；mock 时可 POST /api/mock/weather（source 必须 mock），python 时通过 /agent/weather 获取供应商观测。Python 使用供应商结构化天气代码，不用文本匹配判定降雨，不调用 LLM。原按需通风/晾晒建议兼容保留；Java 收衣提醒不调用这套策略，安全和自动收衣规则由 Java 独立执行。

## MQTT 控制

主题 home/{indoor|balcony}/{deviceKey}/command 与 command-reply，QoS 1，禁止 retained，最大消息 16KiB。命令 version/commandId/deviceKey/action/parameters/expiresAt；回执 version/commandId/deviceKey/success/occurredAt/result/properties。示例和 Schema 见 mqtt。

intelli.extensions.mock-controls 默认 false。开启后只接受 source=MOCK、model=mock-switch:1、最近 5 分钟在线的设备；没有真实硬件驱动。状态 PENDING→DISPATCHED→SUCCEEDED/FAILED，过期为 TIMED_OUT。发布成功不等于执行成功，只有匹配设备回执可完成命令。重复投递由模拟器持久去重，晚到回执只记 lateResult，不修改最终状态或设备快照。命令通过单独状态同步路径接受回执实际属性，不触发火灾规则。

## 一致性边界

历史事件去重不阻止后续处理重试；但当前未建立持久入站处理进度和恢复工作器。Redis 状态更新和 MySQL 写入不在一个事务中，进程崩溃仍有处理窗口。Outbox保证其数据库事务范围，不代表整条链路零丢失或外部通知恰好一次。单实例、单家庭；没有前端页面渲染、真实硬件或公网认证。
