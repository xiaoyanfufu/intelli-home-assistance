# Agent API v1

Java 是安全告警的唯一判断来源，Python 提供天气上下文和生活建议。Java 高危建议直接返回规则提示；Python 同样提供 safetyAlerts 短路，供直接调用方使用。

`POST /agent/recommend`，JSON 请求：

```json
{
  "version": 1,
  "requestId": "request-identifier",
  "deviceKey": "indoor-node-01",
  "location": "INDOOR",
  "properties": {"temperature": 30, "humidity": 50},
  "states": {
    "INDOOR": {"temperature": 30, "humidity": 50},
    "BALCONY": {"temperature": 20, "humidity": 50}
  },
  "safetyAlerts": []
}
```

`states` 是 Java 筛选后的新鲜属性，允许字符串数值。多个设备同位置时选最近接收数据的设备，时间相同按设备键排序取最后一个；Java 对所有设备分别检查安全规则，不因位置聚合隐藏高危设备。

响应字段：`recommendation`、`scenario`、`analysis`、`degraded`、`degradationReason`。

```json
{
  "recommendation": "开窗建议：室内比阳台高 10.0℃，开窗可快速降温",
  "scenario": "NORMAL",
  "analysis": {"window_advice": "OPEN"},
  "degraded": true,
  "degradationReason": "LLM_NOT_CONFIGURED"
}
```

Python 原因包括 LLM_NOT_CONFIGURED、LLM_UNAVAILABLE。Java 额外使用 AGENT_DISABLED、AGENT_UNAVAILABLE、STALE_DATA、BUSY。

收到非空 safetyAlerts 时返回固定安全提示，不查天气、不调用 LLM。没有环境数据时场景为 UNKNOWN，不声称环境正常。天气不可用时不判断适合晾晒。

`version` 仅支持 1；非法请求返回 422。Java HTTP 超时默认 10 秒、并发建议请求最多 4 个；建议故障不进入设备告警链路。

## 每日生活建议

`POST /agent/daily-advice`，示例见 [daily-advice.json](examples/daily-advice.json)，运行与故障边界见 [日报说明](daily-advice.md)。

请求：version=1、requestId、date（ISO 日期）、windowHours（1–720）、states（位置到新鲜属性）、
history（最多 100 个设备摘要）、alertSummary、coverage。history 每项包含 deviceKey/location/from/to/sampleCount/truncated、
coveredFrom/coveredTo（可空）及 properties；各数值属性为 min/max/avg/last/count，非有限数拒绝。
alertSummary 为 total/byLevel/items/truncated，最多 100 条 items；total 是已查询记录数，可能不完整。
coverage.devicesTruncated 表示部分设备未纳入历史。无历史或告警时允许空值并明确说明缺失。

响应：advice（多段文本）、analysis、degraded、degradationReason。LLM_NOT_CONFIGURED / LLM_UNAVAILABLE
是有效模板结果；Java 无法访问 Agent 或收到空/过长正文则失败，不提交投递收据。
当前状态/历史/告警/天气事实和缺失/截断说明由 Python 确定性渲染，LLM 仅追加建议。
历史统计不证明连续趋势；历史告警不代表当前仍有同一风险；Java 保持安全与控制判断权。

Java 日报按位置选择源采样时间最新的新鲜设备，时间相同按设备键升序取第一个，
不把不同设备属性静默拼接。Java → Python 单向调用；Python 没有反向发邮件或调度能力。
