# B10a: api/admin Overview + Pending DTO 契约治理报告

改造时间：2026-06-01
改造人：agentA
批次：B10a（B10 第 1 子批次，5/13 类）

---

## 1. 修改文件清单

| 文件 | 类型 | 变更 |
|---|---|---|
| `AdminOverviewResponse.java` | Response | 类级 `@Getter`，删除 3 getter，3 字段 Javadoc（status/quality 分层问题标注） |
| `AdminOverviewPendingResponse.java` | Response | 类级 `@Getter`，删除 2 getter，2 字段 Javadoc（截断摘要说明） |
| `AdminOverviewPendingItemResponse.java` | Response | 类级 `@Getter`，删除 3 getter，3 字段 Javadoc（question 禁止 @Data） |
| `AdminPendingResponse.java` | Response | 类级 `@Getter`，删除 2 getter，2 字段 Javadoc |
| `AdminPendingItemResponse.java` | Response | 类级 `@Getter`，删除 8 getter，8 字段 Javadoc（question/answer 禁止 @Data） |

---

## 2. 关键标注

### 分层问题（AdminOverviewResponse）
`status`（`StatusSnapshot`）和 `quality`（`QualityMetricsReport`）直接暴露 governance 层领域对象，未经 DTO 包装。类级和字段级 Javadoc 均已标注。

### 用户数据保护
- `AdminOverviewPendingItemResponse.question` — 禁止 toString()
- `AdminPendingItemResponse.question` + `answer` — 禁止 toString()

### Dashboard vs 完整列表
`AdminOverviewPendingResponse.items` 标注为截断展示（非全量），与 `AdminPendingResponse`（分页完整列表）区分。

---

## 3. Lombok 统计

| 类 | 注解 | 替代 |
|---|---|---|
| `AdminOverviewResponse` | `@Getter` | 3 getter |
| `AdminOverviewPendingResponse` | `@Getter` | 2 getter |
| `AdminOverviewPendingItemResponse` | `@Getter` | 3 getter |
| `AdminPendingResponse` | `@Getter` | 2 getter |
| `AdminPendingItemResponse` | `@Getter` | 8 getter |
| **合计** | | **18 getter** |

---

## 4. 测试与验证

```
mvn compile: BUILD SUCCESS
bash scripts/scan-redline.sh /tmp/b10a_special_cases_report.md: (clean)
```

无 api/admin 层 overview/pending 测试类。未修改 docs/模型绑定配置参考.md、special_cases_report.md。

---

## 5. B10 剩余

| 子批次 | 状态 | 类数 |
|---|---|---|
| **B10a** | **已完成** | **5** |
| B10b | 待开始 | 8 (processing task + knowledge help) |

## 6. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 5 个目标文件 | 通过 |
| domain 类型分层问题标注 | 通过 |
| 禁止 @Data | 通过 |
| 构造器签名/逻辑未改 | 通过 |
| 未修改 docs/模型绑定配置参考.md | 通过 |
| 未修改 special_cases_report.md | 通过 |
| 未 stage/commit/push | 通过 |
