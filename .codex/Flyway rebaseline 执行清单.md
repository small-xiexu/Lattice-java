# Flyway rebaseline 执行清单

## 目标

- 仅面向“重建数据库”的开发与测试场景，正式收敛为单一 Flyway 初始化基线
- 删除已无保留价值的占位迁移脚本，不再兼容历史 `flyway_schema_history`
- 同步修正文档与启动说明，避免继续传递“保留占位脚本”的旧认知

## 执行状态

1. `已完成` 建立 rebaseline 执行台账
备注：已确认当前迁移目录为 `V1` 全量基线 + `V2/V3/V4` 空占位，并据此建立本次执行清单。

2. `已完成` 收敛 Flyway 迁移脚本
备注：`src/main/resources/db/migration` 已收敛为唯一 `V1__baseline_schema.sql`，并删除了 `V2/V3/V4` 占位脚本。

3. `已完成` 同步更新项目文档
备注：已把启动清单修正为“正式 rebaseline、只保留单基线、旧库直接重建”的口径。

4. `已完成` 执行构建级校验
备注：已通过 `ArticleJdbcRepositoryTests` 验证单基线迁移，并补充 Maven 构建清理护栏，解决 `target/classes/db/migration` 残留旧脚本问题。
