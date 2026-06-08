# PE5 工作区收口与变更分类计划

分析时间：2026-06-08
执行人：agentC
范围：当前工作区全部已修改（23 个生产/测试/资源文件）和未跟踪报告（~40 个）

## 一、当前工作区全景

### 1.1 已修改文件（23 个）

| 类别 | 数量 | 文件 |
|---|---|---|
| 生产代码 | 14 | StructuredQueryPlanner/Executor/Plan/Service、FtsConfigResolver、LexicalSearchTokenBudget、7 个 JdbcRepository |
| 资源文件 | 7 | 7 个 Mapper XML（ArticleChunk/Contribution/FactCard/FactCardTerminalUnit/SourceFileChunk/SourceFile/StructuredTable） |
| 测试代码 | 2 | SourceFileChunkJdbcRepositoryTests、FtsConfigResolverTests |

### 1.2 暂存区

**空。** 所有变更均在工作区，未暂存。

### 1.3 未跟踪报告

| 主题 | 数量 |
|---|---|
| PE5 structured query planner fix/gate | 9 |
| PE5 evidence packing fix/rollback/gate | 5 |
| PE5 runtime gate/failure/recheck | 3 |
| PE5 structured source recall fix/gate | 4 |
| PE5 YAML retrieval analysis | 2 |
| PE5 资产包（design/build/consistency） | 3 |
| 其他（hidden eval/java codebase/acronym/post-S2 等） | ~12 |

## 二、修复线分类

### 2.1 修复线 A：StructuredQueryPlanner 增强（COUNT/GROUP_BY）

**文件**：
- `StructuredQueryPlanner.java` (+119)
- `StructuredQueryExecutor.java` (+62)
- `StructuredQueryPlan.java` (+35)
- `StructuredQueryService.java` (+4)
- `StructuredTableJdbcRepository.java` (+27) — `findRowShellsByColumnOrValue`
- `StructuredTableMapper.java` (+14) — 对应 Mapper 接口方法
- `StructuredTableMapper.xml` (+35) — 对应 SQL

**变更性质**：PE5 COUNT/GROUP_BY 结构化查询支持。新增查询类型、列名/值查找、证据一致性判定调整。

**影响面**：结构化查询链路。不影响 AnswerGeneration/RRF/citation/fallback。

### 2.2 修复线 B：FTS OR Query（buildFtsQueryText）

**文件**：
- `LexicalSearchTokenBudget.java` (+37) — 新增 `buildFtsQueryText` 静态方法
- 6 个 `*JdbcRepository.java`（各 +3/-1）— `question`参数替换为 `ftsQueryText`
- 6 个 `Mapper XML`（各 +2/-1）— 对应 MyBatis SQL 参数名更新
- `FtsConfigResolver.java` (+7/-15) — 配套简化
- `FtsConfigResolverTests.java` (+6/-4) — 断言更新
- `SourceFileChunkJdbcRepositoryTests.java` (+4/-2) — 断言更新

**变更性质**：**通用 FTS 基础设施变更**。将 LIKE token 转为 OR 连接的 tsquery 文本，确保 tsquery 子 token 与索引精确匹配。影响**所有 6 个 FTS 通道**（ArticleChunk、Contribution、FactCard、FactCardTerminalUnit、SourceFileChunk、SourceFile）。

**影响面**：**全量检索召回**。这是基础设施级变更，不是 PE5 特判，但影响面远超 PE5。

### 2.3 修复线 C：Evidence Packing（已回滚）

**文件**：无工作区残留。仅报告文件记录该线的 fix、runtime gate、rollback 过程。

**状态**：报告显示该线尝试后效果不达预期，已回滚。

## 三、修复线依赖关系

```
修复线 B (FTS OR Query) — 独立基础设施变更
    ↓ 依赖? 否，但所有 FTS 通道共用参数接口
修复线 A (StructuredQueryPlanner) — 独立结构化查询增强
    ↓ 依赖? 是 —— StructuredTableJdbcRepository 的新方法独立于线 B
修复线 C (Evidence Packing) — 已回滚，无残留
```

线 A 与线 B **可以独立验证**：线 B 影响 FTS 通道的 tsquery 参数构建；线 A 影响结构化查询的 Planner/Executor。但当前工作区把它们混在一起，**无法分别验证各自效果**。

## 四、四类分桶

### 4.1 必须保留（建议提交）

| 桶 | 文件 | 理由 |
|---|---|---|
| **线 B — FTS OR Query** | `LexicalSearchTokenBudget.java`、6 个 `*JdbcRepository.java`、6 个 Mapper XML、`FtsConfigResolver.java`、2 个测试 | **通用基础设施修复**。将 FTS tsquery 从原始 question 文本改为 LIKE token 的 OR 连接，对所有 FTS 通道生效。变更量小（每文件 1-3 行），逻辑独立，可回滚。但不确定其对 PE1-PE4 的全量回归影响 |
| **线 A — StructuredQueryPlanner** | `StructuredQueryPlanner.java`、`StructuredQueryExecutor.java`、`StructuredQueryPlan.java`、`StructuredQueryService.java`、`StructuredTableJdbcRepository.java`、`StructuredTableMapper.java`、`StructuredTableMapper.xml` | PE5 结构化查询增强，通用 COUNT/GROUP_BY 支持。无 case 特判风险 |

### 4.2 建议回滚

无。当前两条修复线均为通用能力改动，无 case 特判或实验性代码。

### 4.3 仅文档（可单独提交，不阻塞代码线）

| 桶 | 文件 | 建议 |
|---|---|---|
| PE5 资产包 | `fresh-eval-2026-08/` 目录（5 source + eval + README） | 已就绪，可单独提交 |
| PE5 设计/构建/一致性报告 | `fresh-eval-2026-08_design_report.md`、`_build_report.md`、`_question_set_consistency_fix_result_report.md` | 随资产包提交 |
| PE5 runtime gate 报告 | `_runtime_gate_report.md`、`_runtime_gate_failure_analysis_report.md` 等 | 记录当前 baseline 的 gate 结果，但不随修复线提交——等修复线通过后再补新 gate |

### 4.4 需要验证后再定

| 桶 | 文件 | 风险 |
|---|---|---|
| 线 B 对 PE1-PE4 的全量回归 | 6 个 FTS 通道的召回行为是否退化 | 线 B 改变了所有 FTS 通道的 tsquery 构建，必须跑 PE1-PE4 全量搜索回归 |
| 线 A 对 PE1-PE4 的结构化查询回归 | `StructuredQueryService` 的证据一致性判定变更 | `isComparison`/`isValueProbe` 跳过了 rowMatchesFilters，需确认旧题无回归 |

## 五、推荐最小收口方案

### 方案：分两轮提交 + 干净验证

**第一轮：先提交线 B（FTS OR Query）+ 全量回归**

1. 暂存并提交线 B 全部文件（`LexicalSearchTokenBudget.java`、6 个 JdbcRepository、6 个 Mapper XML、`FtsConfigResolver.java`、2 个测试）
2. 立即跑 PE1-PE4 全量搜索回归（FS1-FS4、FQ search dependent cases）
3. 如搜索回归无退化 → 线 B 通过
4. 如搜索回归退化 → 回滚线 B，单独分析根因

**第二轮：提交线 A（StructuredQueryPlanner）+ PE5 gate**

1. 在线 B 通过后，暂存并提交线 A 全部文件
2. 跑 PE5 全量 FQ1-FQ12 + FS1-FS4 + FG1-FG3
3. 跑 PE1-PE4 结构化查询保护回归

### 如果找不到干净的验证窗口

**建议切到新 worktree**，分别在以下状态跑 gate：
- Worktree A：仅线 B 变更（其他文件回滚）
- Worktree B：仅线 A 变更（线 B 已提交后再加）
- Worktree C：干净 HEAD（回滚全部，做 PE5 baseline）

```bash
# 创建 worktree
git worktree add /tmp/pe5-line-b HEAD
cd /tmp/pe5-line-b
git checkout main -- <线B文件>  # 只应用线B变更
```

## 六、特殊注意事项

### 6.1 `LexicalSearchTokenBudget.buildFtsQueryText` 的通用性

此方法是通用基础设施：输入 LIKE token 列表，输出 OR 连接的 tsquery 文本。不绑定 PE5、不绑定特定领域、不绑定特定 JdbcRepository。**但需要确认其对 MySQL `to_tsvector('simple')` 的 OR 语法兼容性**——当前用 ` | ` 连接，这取决于 PostgreSQL FTS 的 tsquery OR 语法是否正确（MySQL 的 FULLTEXT 不支持 OR 语法，但此项目使用 PostgreSQL）。

### 6.2 `StructuredQueryService` 的 evidence 一致性变更

```java
boolean isComparison = plan.getComparisonOperator() != null;
boolean isValueProbe = StructuredQueryPlanner.isValueProbeFilter(plan.getFilters());
if (!isComparison && !isValueProbe && !rowMatchesFilters(rowEvidence, plan.getFilters())) {
```

此变更对 COMPARISON 和 VALUE_PROBE 类型的查询跳过了 `rowMatchesFilters` 检查。需确认这不会导致 PE1-PE4 中已有的结构化查询返回错误证据。

### 6.3 报告清理

24 个 PE5 相关未跟踪报告**不要混入代码提交**。建议：
- 资产包相关的 3 个（design/build/consistency）随资产包单独提交
- Runtime gate 报告等修复线通过后，与新的 gate 报告一起提交
- 中间实验报告（evidence packing rollback 等）可删除或被最终 gate 覆盖

## 七、当前工作区质量判断

| 维度 | 判定 |
|---|---|
| 能否提供干净 PE5 结果 | **否**——两条修复线混合，且线 B 影响面覆盖全部 FTS 通道 |
| 线 B 是否有独立价值 | **是**——通用 FTS tsquery 优化 |
| 线 A 是否有独立价值 | **是**——通用 COUNT/GROUP_BY 支持 |
| 两条线是否可以同时提交 | **不建议**——需要分别验证回归影响 |
| 是否需要切 worktree | **建议**——如果不想回滚/恢复反复，worktree 是最快验证方式 |

## 八、明确声明

- [x] 本轮未修改任何文件
- [x] 仅输出收口计划，不执行回滚或提交
- [x] 未读取 hidden eval
