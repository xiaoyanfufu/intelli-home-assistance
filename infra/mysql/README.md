# 数据库初始化

新建 MySQL 数据卷只创建 `intelli_home` 数据库，不执行 `init/` 下的历史 SQL。Java 启动时运行 `src/main/resources/db/migration/V1–V4`，Flyway 是当前表结构的唯一迁移入口。

`init/01-schema.sql` 和 `init/02-mvp.sql` 保留供旧版本结构对照，不用于新库安装。已有旧版八表数据库需按 Java `FEATURES.md` 显式启用 `ADOPT_EXISTING_SCHEMA=true` 接管；已有 Flyway 历史表无需再次接管。未知结构默认拒绝。

不要修改已应用的 Flyway 文件，也不要通过删除数据卷升级。
