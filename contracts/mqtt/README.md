# MQTT 遥测协议 v1

适用范围：设备或模拟器向 Java 后端上报当前环境属性。编码为 UTF-8 JSON 对象；不是 Protobuf。业务命令、命令应答和独立心跳主题尚未实现。

## 主题

```text
home/{location}/{deviceKey}/event
```

| 部分 | 约定 |
|---|---|
| location | 发送方使用小写 indoor 或 balcony，对应 INDOOR / BALCONY |
| deviceKey | 1–64 个英文字母、数字、下划线或横线；必须稳定且唯一 |
| event | 固定文本，当前只处理遥测事件 |

例如 `home/indoor/indoor-node-01/event`。设备标识和位置从主题读取，不在消息体重复传输。后端订阅主题与 QoS 由 `intelli.mqtt` 配置决定；可靠遥测建议发布 QoS 1，设备重试必须保留消息身份。MQTT 确认不等于所有业务操作形成跨存储事务。

## 消息体

```json
{
  "ts": 1791340000000,
  "seq": 1,
  "temperature": 26.5,
  "humidity": 48,
  "smoke": 80
}
```

示例时间仅用于展示；实际发送必须使用真实采样时间。

| 字段 | 发送方类型 | 必填 | 含义 |
|---|---|---|---|
| ts | 非负整数 | 是 | 采样时间，Unix 毫秒；不能用秒，也不能在重试时改成当前时间 |
| messageId | 非空字符串，建议 UUID | 与 seq 至少一个 | 同一设备内稳定的消息标识；不同采样使用不同标识 |
| seq | 非负整数 | 与 messageId 至少一个 | 采样序号；同一采样的重投必须保持 ts 和 seq 不变 |
| temperature | number | 否 | 项目约定为摄氏度；规则支持数值字符串以兼容旧输入，规范发送使用 number |
| humidity | number | 否 | 项目约定为相对湿度百分数；当前后端未强制 0–100 范围 |
| smoke | number | 否 | 当前模拟烟雾读数；真实硬件单位、标定和阈值待确定，不能假定为 ppm |
| 其他属性 | string / number / boolean | 否 | 扩展属性名符合下述格式，不能为 null、对象或数组 |

两种身份字段同时出现时，后端优先使用 messageId。后端事件 ID 由设备键和消息身份生成；messageId 应在设备重启后也避免复用。ts、messageId、seq 是元数据，解析后不会进入设备 properties。

只上报变化属性是允许的；没有随本次上报出现的属性不会自动清空，但其采样时间也不会刷新。当前协议未规定必须每次包含所有传感器字段。

## 格式和业务校验

- 消息体不超过 16 KiB，必须是合法 JSON 对象。
- 属性名匹配 `[a-zA-Z][a-zA-Z0-9_]{0,63}`；字符串最多 255 字符，数值必须有限。
- 源时间不能超过服务端接收时间 5 分钟；旧数据可能被拒绝更新，超过新鲜度的数据不会用于当前环境判断。
- 当前属性新鲜度为 5 分钟，缓存 TTL 为 6 小时；这些是后端策略，不是 MQTT 编码本身的能力。
- 无效报文被丢弃并记录日志。业务处理异常向 Paho 传播，可能引起连接断开与后续重投；未实现持久入站日志。

[telemetry-v1.schema.json](telemetry-v1.schema.json) 定义规范发送格式。JSON Schema 只能验证消息体，主题、字节长度、与当前时间比较等由接收代码验证。当前解析器对 ts 的小数形式、messageId / seq 的类型比本契约宽松；发送方应遵守契约，接收端尚未对这几个约束完全对齐。Schema 当前不在 Java 热路径自动执行。

## 示例与实现位置

- [室内报文](examples/indoor.json)：主题 `home/indoor/indoor-node-01/event`。
- [阳台报文](examples/balcony.json)：主题 `home/balcony/balcony-node-01/event`。
- 接收解析：`intelli-home/src/main/java/com/intelli/home/adapter/in/mqtt/MqttDeviceEventParser.java`。
- 统一事件：`intelli-home/src/main/java/com/intelli/home/domain/event/DeviceEvent.java`。

## 兼容性

新加可选标量属性通常可由现有解析器接受，但不代表已有规则会使用它。修改字段类型、单位、身份算法或主题格式属于破坏性变更，需同步设备端与后端，并设计兼容窗口。现在不支持在原主题中混用 JSON 和 Protobuf。
# 控制协议扩展

遥测 v1 保持兼容。模拟控制主题为 `home/{indoor|balcony}/{deviceKey}/command` 和 `command-reply`；分别见 [command.schema.json](command.schema.json)、[command-reply.schema.json](command-reply.schema.json) 及 examples 下示例。禁止 retained，QoS 1，消息最大 16KiB。commandId 关联回执；发布成功仅代表投递，设备成功必须由匹配回执确认。过期与晚到回执规则、模拟能力开关见 [home-features.md](../home-features.md)。
