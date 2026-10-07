# 邮件与 Agent 日报运行说明

Java 收集状态、历史与告警，Python 生成天气上下文与建议，Java 同步投递到固定收件人。
日报通过独立 `dailyAdviceScheduler` 单线程（`daily-advice-*`）运行；原定时任务使用
`taskScheduler`（`home-scheduled-*`）。默认关闭日报和邮件，手动预览仍可使用。

## 配置与启动

Java 配置统一位于 `intelli.notification.*` 和 `intelli.daily-advice.*`，示例为
`intelli-home/src/main/resources/application-local.example.yml`。复制到被忽略的
`application-local.yml` 后需设置 `SPRING_PROFILES_ACTIVE=local`。也可使用环境变量：

```powershell
$env:AGENT_ENABLED='true'
$env:NOTIFY_CHANNEL='email'
$env:MAIL_ENABLED='true'
$env:MAIL_HOST='<SMTP host>'
$env:MAIL_PORT='465'
$env:MAIL_SECURITY='ssl'
$env:MAIL_USERNAME='<sender address>'
$env:MAIL_PASSWORD='<SMTP authorization code>'
$env:MAIL_TO='<fixed recipient addresses, comma separated>'
# 验收通过后再启用定时日报
$env:DAILY_ADVICE_ENABLED='true'
$env:DAILY_ADVICE_ZONE='Asia/Shanghai'
$env:DAILY_ADVICE_CRON='0 0/15 7-9 * * *'
```

Python 沿用原 `LLM_*`、`WEATHER_*` 参数。无 LLM 密钥会生成真实数据模板；无天气配置会
明确说明天气缺失。先启动 Python（8000），再启动 Java（8080）；原启动命令见根 README。
本地 SMTP 自动测试验证投递行为；真实公网邮箱收件需要单独验证。

## 手动验收

```powershell
./scripts/demo-daily-advice.ps1        # dryRun：生成全文，不投递、不写收据
./scripts/demo-daily-advice.ps1 -Send  # 真实投递，使用当天幂等键
```

返回状态：`DRY_RUN`、`SENT`、`SKIPPED_ALREADY_SENT`、`SKIPPED_RUNNING`、
`DELIVERY_NOT_CONFIGURED`、`FAILED`。HTTP 200 代表拿到了执行结果，调用方必须检查 status。
真投递要求 email 通道已启用、且 `DAILY_DIGEST` 未被级别过滤；日志通道不会占用日报收据。
重复当天的真投递跳过；预览不受已投递收据限制。不提供 force 和历史日期补发参数。

可选受控发信入口 `POST /api/notifications/email` 默认不存在，显式设置
`MAIL_API_ENABLED=true` 后可用 `./scripts/demo-email.ps1`。请求字段仅包含
`subject`（1–160 字符、不含换行）、`body`（1–4000 字符）、`idempotencyKey`
（1–51 字符，字母、数字、横线、下划线）。收件人始终来自配置，传入 to 不会改变收件人。
无幂等键时生成 UUID；键命名空间是 `manual-email:`。未配置邮件返回 503，非法输入返回 400。
这是本地调试入口，没有生产鉴权，勿直接暴露公网。

## 数据与故障边界

- 当前状态只取五分钟内新鲜属性，同位置选择源事件时间最新的设备，时间相同按设备键升序取第一个。
- 历史窗口默认 24h，最大 720h；按 `(occurredAt,eventId)` 游标读取，动态数值字段聚合 min/max/avg/last/count。
  默认最多 20 个设备、每设备 20000 条；查询多一条判断是否截断，携带实际采样覆盖时间。
  窗口不是覆盖时间证明；没有采样时不填零。遇到不前进的游标显式失败。
- 告警仓储最多返回最近 100 条，到达上限保守标 truncated；total 是窗口内已查询记录数，不能当完整告警总数。
- Python 固定事实段展示当前状态、历史、告警和天气；LLM 仅追加建议，不能通过省略移除缺失/截断说明。
  提示词约束不是形式化事实校验，不能保证所有 LLM 文本都无幻觉。历史告警不等于当前仍有风险。
- 成功收据键为 `daily-advice:{配置时区日期}`，写入现有 `notification_delivery`，不写 alert_event/Outbox/MQ。
  Agent/SMTP/数据库失败不提交新收据，下个窗口 tick 重试。已有收据持久化，服务对象重建仍跳过。
  单实例锁防止手动与定时重入；数据库主键为最终投递去重守卫，不支持完整多实例调度协调。
- SMTP 在收据事务内同步执行，连接/读/写分别 5s；它们不是整个发送过程统一 5s 截止时间。
  SMTP 已接受但 commit 失败仍可能重发；SMTP 接受也不等于收件箱一定收到。三次 MQ 重试耗尽进死信，需显式重投。
- 日报没有 Outbox 持久化任务，整个窗口停机/失败会漏掉当天；过了窗口不无限追发。
  独立线程隔离调度阻塞，不能隔离共享 MySQL/Redis/SMTP 的资源竞争或全局故障。
- WARN 以下默认过滤，但 `DAILY_DIGEST`、`MANUAL_EMAIL` 默认豁免；过滤处理成功仍提交通知收据。
  关闭邮件通道则投递抛错，不能误提交“已投递”收据。

## 验证

`scripts/verify.ps1` 包含 GreenMail 本地 SMTP、独立无密钥 Python 进程和真实数据库的跨语言测试，
以及仅在隔离 RabbitMQ（5673）执行的 SMTP 三次失败死信/恢复重投、慢日报与真实 Outbox 发布测试。
`mvn test` 运行不依赖中间件的单测，集成测试默认跳过；Python 使用 `pytest -q`。
测试不读取私人授权码、不发送公网邮件、不暂停正常 Broker、不操作真实设备。
