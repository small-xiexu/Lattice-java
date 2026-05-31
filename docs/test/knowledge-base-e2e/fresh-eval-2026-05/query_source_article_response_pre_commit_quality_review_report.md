# QuerySourceResponse / QueryArticleResponse B0 改造 — Pre-Commit 质量审查报告

审查时间：2026-05-31
审查人：agentB（只读 pre-commit 审查 Agent）
审查范围：仅 QuerySourceResponse / QueryArticleResponse B0 改造，不修改任何文件

---

## 1. 审查结论

**PASS** — 本次 B0 改造质量合格，建议独立提交。

---

## 2. 逐项审查结果

### 2.1 字段 Javadoc 是否是工程解释型

**PASS**

所有 11 个字段（QuerySourceResponse 6 个 + QueryArticleResponse 5 个）的 Javadoc 均为工程解释型注释，解释了：
- 字段在系统中的业务含义（非字段名翻译）
- 调用方的典型使用场景
- 何时可能为空及其原因
- 与其他概念的关联关系

典型示例：
- `sourceId`: 说明了“非持久化资料时可能为空”的边界条件
- `derivation`: 明确列举了三种推导来源（检索命中/projection/top-K 兜底）及置信度判断指引
- `title`: 说明了标题的三种可能来源（资料库/编译产物/系统自动生成）

风格与已完成的 `QueryResponse.java`、`QueryRetrievalSettingsState.java` 一致。

### 2.2 `@Getter` / `@Builder` / `@JsonCreator` 共存是否安全

**PASS**

| 注解 | 位置 | 安全性分析 |
|---|---|---|
| `@Getter` | 类级 | 字段为 `final`，Lombok 仅生成 getter，不生成 setter。与手写 getter 签名完全一致 |
| `@Builder` | `@JsonCreator` 构造器上 | Lombok 生成静态 builder 类，其 `build()` 方法调用全参构造器。不干扰 Jackson 反序列化路径 |
| `@JsonCreator` | 构造器上 | Jackson 通过 `@JsonProperty` 注解的参数名进行反序列化，与 builder 路径独立 |

这是 Lombok + Jackson 的成熟共存模式。全量 995 测试全部通过，验证了 JSON 序列化/反序列化行为不变。

未使用的危险注解：无 `@Data`、`@Setter`、`@AllArgsConstructor`、`@NoArgsConstructor`。

### 2.3 删除短构造器后，测试迁移是否完整

**PASS**

逐文件核实：

| 测试文件 | 原短构造器调用 | 迁移状态 |
|---|---|---|
| `LatticeMcpToolsTest.java:62-73` | `new QuerySourceResponse("concept-1", "Payment Timeout", List.of(...))` / `new QueryArticleResponse("concept-1", "Payment Timeout")` | 已迁移为 builder |
| `RedisQueryCacheStoreTests.java:45-53` | 同上模式 | 已迁移为 builder |
| `RedisQueryCacheStoreIntegrationTests.java:68-76` | 同上模式 | 已迁移为 builder |

全项目 `grep` 搜索确认：测试代码中已零残留 `new QuerySourceResponse(...)` / `new QueryArticleResponse(...)` 短构造器调用。

### 2.4 生产代码残留的 7 处全参构造是否可以接受

**ACCEPTABLE — 不建议迁移为 builder**

7 处生产调用点及其参数匹配情况：

| # | 文件:行号 | 类型 | 参数数量 | 匹配度 |
|---|---|---|---|---|
| 1 | `QueryResponseCitationProjectionSupport.java:218` | QuerySourceResponse | 6/6 | 全参 |
| 2 | `QueryResponseCitationProjectionSupport.java:234` | QuerySourceResponse | 6/6 | 全参 |
| 3 | `QueryResponseCitationBaseSupport.java:264` | QuerySourceResponse | 6/6 | 全参 |
| 4 | `StructuredQueryAnswerRenderer.java:306` | QuerySourceResponse | 6/6 | 全参 |
| 5 | `QueryResponseCitationBaseSupport.java:154` | QueryArticleResponse | 5/5 | 全参 |
| 6 | `QueryResponseCitationBaseSupport.java:172` | QueryArticleResponse | 5/5 | 全参 |
| 7 | `QueryResponseCitationBaseSupport.java:294` | QueryArticleResponse | 5/5 | 全参 |

**不建议迁移的理由：**

1. 全参构造器是保留的 `@JsonCreator` 入口，本身就是稳定的公共 API。删除的是短便利构造器，不是全参构造器。
2. 这些调用点参数均为复杂表达式（三元判断、方法调用返回值），使用位置参数构造器比 builder 模式更紧凑、可读性更好。强行改为 builder 会引入不必要的 `.build()` 和 `.xxx()` 链式调用噪音。
3. `@Builder` 是**新增**的便利能力，不是**替代**全参构造器。两种构造方式可以共存——测试偏好 builder（链式语义清晰），生产偏好全参构造器（参数位置对应明确）。

结论：7 处生产调用点无需迁移，现状可接受。

### 2.5 是否误改 query/retrieval/fallback/answer 主链逻辑

**PASS**

变更范围严格限定在 DTO 类结构：
- 新增 `@Getter`、`@Builder`、字段 Javadoc
- 删除短便利构造器（3-param / 2-param）
- 删除手写 getter 方法（由 `@Getter` 替代）

未触及：
- `QueryFacadeService`、`QueryController` 等主链入口
- `retrieval/` 包下的检索逻辑
- `fallback/` 包下的兜底逻辑
- `answer/` 包下的答案渲染逻辑
- `QueryResponseCitationBaseSupport`、`QueryResponseCitationProjectionSupport`、`StructuredQueryAnswerRenderer` 中的构造逻辑（仅使用全参构造器，未改变调用方式）

DTO 的公开 API 语义完全不变：构造器参数顺序和类型不变，getter 方法名和返回类型不变，JSON 字段名不变。

### 2.6 报告中测试与 redline 结论是否足够支持提交

**PASS**

| 验证项 | 结果 | 评价 |
|---|---|---|
| 定向测试（4 个测试类） | 25 run, 0 failures, 0 errors | 覆盖 DTO 直接使用场景 |
| 全量测试（全项目） | 995 run, 0 failures, 0 errors | 覆盖 JSON 序列化/反序列化、生产链路集成 |
| Redline 扫描 | clean（无输出） | 无新增硬编码/魔法字符串/兜底逻辑违规 |

测试结果可信，覆盖充分。

### 2.7 本次提交范围应只包含 B0 改造，不得混入全项目治理计划文档

**PASS**

Git diff 确认：本次 B0 改造仅涉及 5 个文件：

| 文件 | 类型 |
|---|---|
| `src/main/java/com/xbk/lattice/api/query/QuerySourceResponse.java` | DTO 改造 |
| `src/main/java/com/xbk/lattice/api/query/QueryArticleResponse.java` | DTO 改造 |
| `src/test/java/com/xbk/lattice/mcp/LatticeMcpToolsTest.java` | 测试迁移 |
| `src/test/java/com/xbk/lattice/query/service/RedisQueryCacheStoreTests.java` | 测试迁移 |
| `src/test/java/com/xbk/lattice/query/service/RedisQueryCacheStoreIntegrationTests.java` | 测试迁移 |

以下文件虽在 working tree 中有修改，但属于**已有修改，非本次 B0 引入**，不应纳入提交范围：

| 排除文件 | 判断依据 |
|---|---|
| `docs/quality-progress-and-lessons.md` | 与 DTO 改造无关 |
| `docs/模型绑定配置参考.md` | 与 DTO 改造无关 |
| `special_cases_report.md` | 仅时间戳更新和例行重扫结果，与 B0 无关 |
| `docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md` | 全项目治理计划，不应混入 |
| `docs/reports/model_contract_javadoc_lombok_plan_review_analysis_report.md` | 治理计划审查报告，不应混入 |
| `docs/test/knowledge-base-e2e/fresh-eval-2026-05/query_source_article_response_field_javadoc_lombok_fix_result_report.md` | agentA 执行报告，可选纳入 docs |

---

## 3. 建议提交范围

```
src/main/java/com/xbk/lattice/api/query/QuerySourceResponse.java
src/main/java/com/xbk/lattice/api/query/QueryArticleResponse.java
src/test/java/com/xbk/lattice/mcp/LatticeMcpToolsTest.java
src/test/java/com/xbk/lattice/query/service/RedisQueryCacheStoreTests.java
src/test/java/com/xbk/lattice/query/service/RedisQueryCacheStoreIntegrationTests.java
```

可选随提交纳入（作为本次改造的记录）：
```
docs/test/knowledge-base-e2e/fresh-eval-2026-05/query_source_article_response_field_javadoc_lombok_fix_result_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-05/query_source_article_response_pre_commit_quality_review_report.md
```

## 4. 明确排除范围

以下文件**不得**纳入本次 B0 提交：

1. `docs/quality-progress-and-lessons.md` — 已有修改，非本次 B0 引入
2. `docs/模型绑定配置参考.md` — 已有修改，非本次 B0 引入
3. `special_cases_report.md` — 例行重扫产物，非本次 B0 引入
4. `docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md` — 全项目治理计划
5. `docs/reports/model_contract_javadoc_lombok_plan_review_analysis_report.md` — 治理计划审查报告
6. 任何 `scripts/**`、`src/main/java/com/xbk/lattice/query/**`（DTO 文件除外）、`src/main/java/com/xbk/lattice/compiler/**` 等非 B0 范围文件

---

## 5. 补充说明

- agentA 报告中 `QueryResponseCitationProjectionSupport` 的文件路径缺少 `service/` 层级（报告写 `.../QueryResponseCitationProjectionSupport.java`，实际为 `.../query/service/QueryResponseCitationProjectionSupport.java`），不影响实质审查结论。
- `special_cases_report.md` 的修改确认为例行重扫（时间戳从 2026-05-22 更新至 2026-05-31，新增若干扫描条目），与 B0 改造无因果关系。
