# ADR-003: 统一逻辑删除

## 背景
项目需要保留历史数据用于审计和分析，不能物理删除记录。

## 决策
所有数据表统一使用 `deleted` 字段做**逻辑删除**，`deleted=0` 表示有效记录，`deleted=1` 表示已删除。

## 影响
- `lending_records`、`credit_records`、`fine_records` 三张表均包含 `deleted` 字段
- 所有查询语句必须包含 `WHERE deleted=0` 条件
- MyBatis-Plus 的 `@TableLogic` 注解自动处理
- 使用 `LendingRecordMapper.xml` 中自定义 SQL 的 `selectById` 方法，SQL 包含 `AND deleted=0`
