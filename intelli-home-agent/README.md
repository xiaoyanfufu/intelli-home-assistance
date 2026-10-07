# intelli-home-agent

Python 3.12 + FastAPI + LangGraph 生活建议服务。Java 执行安全规则、定时调度、持久化和通知；Python 获取天气并生成建议。

许可证：[MIT](LICENSE)。

## 安装与运行

从本目录执行：

```powershell
python -m venv .venv
.venv/Scripts/python.exe -m pip install -r requirements-lock.txt
.venv/Scripts/python.exe -m pip install --no-deps --no-build-isolation -e .
.venv/Scripts/python.exe -m uvicorn intelli_agent.main:app --host 127.0.0.1 --port 8000
```

锁文件包含运行和测试依赖；editable 安装让 `src/` 包可被服务和测试导入。可将 `.env.example` 复制为 `.env` 配置外部服务，真实密钥不提交到 Git。无模型密钥时返回模板建议并标记 `degraded=true`；天气不可用时明确报告缺失。

## 接口与工作流

| 接口 | 用途 |
|---|---|
| GET /health | 健康及配置状态，不返回密钥 |
| POST /agent/recommend | 设备状态、天气和策略驱动的按需建议 |
| GET /agent/weather | 结构化天气，供 Java 收衣规则使用，不调用模型 |
| POST /agent/daily-advice | 接收 Java 聚合的状态、历史与告警摘要，生成生活建议日报 |

`graph.py` 与 `daily.py` 分别维护按需建议和日报工作流。日报固定事实段保留数据缺失、截断和历史告警说明，模型只追加生活建议。模型异常或空响应降级为真实数据模板；日报协议版本非 1 返回 422。

协议见 [Agent API](../contracts/agent-api.md) 和 [日报说明](../contracts/daily-advice.md)。本服务与 Java、契约和脚本位于同一仓库。

## 验证

```powershell
.venv/Scripts/python.exe -m pytest -q
```

测试覆盖策略、工作流、天气契约和日报。Python 不负责设备控制、数据库收据或邮件投递。
