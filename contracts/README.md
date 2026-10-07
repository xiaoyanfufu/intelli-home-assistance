# 多端协议契约

设备端、Java、Python 和调试工具共同阅读这里的协议，不需要通过解析器源码猜字段。

| 协议 | 人读文档 | 机器可读定义 | 示例 |
|---|---|---|---|
| MQTT 遥测 v1 | [MQTT 协议](mqtt/README.md) | [JSON Schema](mqtt/telemetry-v1.schema.json) | [室内](mqtt/examples/indoor.json)、[阳台](mqtt/examples/balcony.json) |
| Java → Python Agent v1 | [Agent API](agent-api.md) | 尚未在此目录维护 OpenAPI 文件 | 文档内示例 |

当前 MQTT 使用 UTF-8 JSON，没有 Protobuf。JSON Schema 是发送方契约和独立校验依据，当前 Java 解析器尚未直接加载 Schema，不能宣称两者自动保持同步。

修改契约时需同时检查发送方、接收方、示例和兼容性。现有 MQTT v1 的版本由本文档管理，线上报文没有 version 字段；不能直接加 version 字段冒充版本协商，因为旧解析器会把它当成设备属性。破坏性升级需要先设计独立主题或明确版本识别机制。

数据库表定义位于 `intelli-home/src/main/resources/db/schema.sql`，MyBatis SQL 与字段映射位于 `intelli-home/src/main/resources/mapper/*.xml`，适配器位于 `adapter/out/persistence`。数据库结构和对外消息协议分别维护，不能将表字段直接视为设备协议。
