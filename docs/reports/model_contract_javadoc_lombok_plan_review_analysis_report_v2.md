# 模型契约注释与 Lombok 治理计划 — 第二轮只读审查报告（v2）

审查时间：2026-05-31
审查人：agentB（只读 pre-commit 审查 Agent）
审查对象：`docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md`（修订版）
审查类型：只读，未修改任何文件

---

## 1. 总体结论

**PASS** — 第一轮审查提出的 3 个必须修改项已全部修正。计划修订版可以作为后续全项目推进的唯一台账。剩余 3 个建议修正项不阻塞执行，可在推进过程中按需微调。

---

## 2. 第一轮问题修正验证

### 2.1 `query/service` 核心 DTO 是否补入 — FIXED

| 第一轮要求 | 修订结果 |
|---|---|
| 补入 `QueryArticleHit`、`RetrievalStrategy`、`RetrievalChannelRun` | 新增批次 **B0.5**（3 个类），审计统计表新增 `query/service: 3` 行 |

B0.5 紧接 B0 之后执行，顺序合理（与 api/query 紧耦合的检索核心对象优先处理）。

### 2.2 B11 是否已拆分 — FIXED

| 第一轮要求 | 修订结果 |
|---|---|
| 29 个内部 DTO 拆为 3 子批 | B11a（LLM 连接/测试，14 类）、B11b（document parse，11 类）、B11c（source controller，4 类） |

实扫验证：8 个 controller 内部 static DTO 数量与计划一致（10+2+2+4+2+2+3+4=29）。

### 2.3 compiler/config + query config/state 是否已补全 — FIXED

| 第一轮指出遗漏 | 修订结果 |
|---|---|
| 6 个 query config（QueryWorkingSetProperties 等） | B12b 明确列出 5 个 query config + LatticeCliConfig |
| 3 个 state 类（QueryRetrievalSettingsState 等） | B12b 和 B18 均列出 |

**⚠️ 微瑕：3 个 state 类在 B12b 和 B18 中重复出现**

`QueryRetrievalSettingsState`、`QueryVectorConfigState`、`CompileReviewConfigState` 同时出现在：
- B12b（作为"运行态缓存、工作集和查询配置投影"）
- B18（作为"检索/编译状态语义"）

这 3 个类兼具 config 投影语义和 graph state 语义，第一轮审查建议归入 B12（"配置缓存的投影，更接近 config 语义"）。修订版将它们放在了两个批次中。由于批次是串行执行的，实际效果是 B12b 处理完毕后 B18 会再次遇到它们——不会导致冲突（可跳过已处理类），但是计划台账的清晰度受影响。

**建议**：从中选择一个批次作为唯一归属，推荐保留在 B12b（第一轮审查的建议），从 B18 中移除。

### 2.4 `infra/persistence/*Record.java` 是否已明确排除 — FIXED

明确排除章节（第 122-129 行）已列出：
- `infra/persistence/*Record.java` 43 个 MyBatis 记录类
- `infra/persistence/mapper/*.java`
- 仓储实现类、服务编排类、异常处理类
- controller 本体（仅治理内部 static DTO）

排除范围清晰、边界明确。

### 2.5 graph/state 范围是否已明确列出 — FIXED

B18 明确列出全部 6 个 state 类：
- `QueryGraphState`、`CompileGraphState`、`DeepResearchState`（已用 `@Data` 的 graph state）
- `QueryRetrievalSettingsState`、`QueryVectorConfigState`、`CompileReviewConfigState`（手写 getter/setter 的配置缓存投影）

实扫验证：`find src/main/java -name "*State.java"` 返回的 6 个 state 类与计划一致。

---

## 3. 仍需修正项（建议，非阻塞）

### 3.1 State 类归属去重（建议修正）

**问题**：`QueryRetrievalSettingsState`、`QueryVectorConfigState`、`CompileReviewConfigState` 同时出现在 B12b 和 B18。

**建议**：从 B18 中移除这 3 个类，仅在 B12b 中保留。理由：
- 它们本质上是"配置的运行态缓存投影"，语义更接近 config 而非 graph state
- B12b 的验证门槛（Spring context / 配置绑定测试）比 B18 的验证门槛（deep research 定向测试）更匹配

### 3.2 Controller 内部 @Data 类的 toString() 安全审计（建议补入）

**问题**：B11a/B11b/B11c 涉及 29 个已有 `@Data` 的内部 DTO，其中 LLM 连接/API key/凭证类 DTO 的字段可能包含敏感值（apiKey、token、password）。`@Data` 生成的 `toString()` 会序列化所有字段，如果被日志输出可能泄露敏感信息。

**建议**：在 B11a/B11b/B11c 的验证栏或 Lombok 规则表中增加一条：
> Controller 内部 @Data DTO 若包含 apiKey/token/password 等敏感字段，应使用 `@ToString.Exclude` 标注或改用 `@Getter @Setter` 替代 `@Data`。

当前计划的 Lombok 规则表（第 27-36 行）未覆盖此场景（API Request 行只说"保留框架需要的无参构造/Setter"，未提及 toString 安全）。

### 3.3 审计统计中 LatticeCliConfig 归属（微瑕）

**问题**：审计统计表（第 59 行）将 `LatticeCliConfig` 归类在 `query/state/config` 下，但实际文件位于 `src/main/java/com/xbk/lattice/cli/LatticeCliConfig.java`，不属于 query 包。B12a 将其与 compiler/config + source/config 放在一起是正确的。

**建议**：审计统计中将 `LatticeCliConfig` 从 `query/state/config` 移到单独一行或并入 compiler/config 统计数。

---

## 4. 候选范围补充建议

### 4.1 QuerySemanticRules 是否需纳入

实扫发现 `query/service/QuerySemanticRules.java` 是 `@ConfigurationProperties(prefix = "lattice.query.semantic")` 类，包含中文语义信号列表（countSignals、comparisonSignals、deepResearchSignals），有 getter/setter。该类不在计划的任何批次中。

**分析**：该类已有较完整的类级 Javadoc（"从 config/lattice-query-semantic.yml 加载通用中文语义信号"），字段注释是否需要补强取决于其 List 字段的配置语义是否需要进一步说明。

**建议**：可在 B12b 中纳入，或明确排除（如果认为其字段语义已经足够清晰）。当前遗漏不影响计划主体推进，可在 B12b 执行时现场判断。

---

## 5. 批次拆分是否还需调整

### 5.1 当前批次大小分布

| 批次 | 类数 | 评估 |
|---|---|---|
| B0-B4 | 3-6 | 理想粒度 |
| B5a-B10 | ~10-12 | 合理，admin DTO 内聚性高 |
| B11a | 14 | 略超上限但已是拆分后的最小内聚单元 |
| B11b | 11 | 同上 |
| B11c | 4 | 合理 |
| B12a-B12b | 8-10 | 合理 |
| B13 | 14 | compiler/domain+ast 为强内聚模块，可接受 |
| B14 | 14 | documentparse/domain 为强内聚模块，可接受 |
| B17 | 23 | 最大单批，query/domain+evidence/domain，可接受 |
| B18-B19 | 5-16 | 合理 |

**结论**：批次粒度无需进一步调整。B13/B14/B17 虽然超过 10 个类，但它们属于强内聚的领域模块，拆分会破坏语义完整性。当前拆分合理。

### 5.2 批次顺序验证

B0-B3（api/query 对外 API）→ B0.5（query/service 核心 DTO）→ B4-B11（其余 API 边界）→ B12-B19（domain/config/graph state）→ B20（全局复扫）

顺序合理：先处理对外 API 门面建立样板，再处理紧耦合的检索 DTO，然后扩展到 admin API 和内部 domain 模型。

---

## 6. Lombok / Jackson / Spring Binding / Entity-like 风险边界

### 6.1 计划已覆盖的风险

| 风险类别 | 覆盖位置 | 评价 |
|---|---|---|
| @Data 禁止用于 API Response | Lombok 规则表第 1 行 | 明确 |
| Spring @ConfigurationProperties 保持现有绑定 | Lombok 规则表第 5 行 + 风险禁令第 7 条 | 明确 |
| JPA/Entity-like 不改构造器/equals/hashCode | 风险禁令第 6 条 | 明确 |
| 不全局替换 @Builder | 风险禁令第 2 条 | 明确 |
| 不混改多种风险类别 | 风险禁令第 5 条 | 明确 |
| 不夹带主链行为修复 | 风险禁令第 3 条 | 明确 |
| API Request/Spring binding 保留框架约束 | Lombok 规则表第 2 行 | 明确 |

### 6.2 风险边界总体评价

计划的 Lombok 规则表（第 27-36 行）覆盖了 6 种典型场景，每种场景都有"推荐"和"禁止/谨慎"两列。结合风险禁令（第 112-120 行）和明确排除（第 122-129 行），风险边界描述足够清楚，可以指导 agentA 逐批安全执行。

唯一缺口是 3.2 节提到的 controller 内部 @Data toString() 安全审计，属于 B11 特有问题，不影响其他批次。

---

## 7. 是否可以作为唯一推进台账

### 7.1 台账要素完整性

| 要素 | 状态 | 位置 |
|---|---|---|
| 总目标与范围 | 有 | 第 1-14 行 |
| 注释标准（6 层） | 有 | 第 16-25 行 |
| Lombok 规则（6 场景） | 有 | 第 27-36 行 |
| 候选审计统计 | 有 | 第 38-59 行 |
| 批次推进表（状态/范围/目标/验证） | 有 | 第 61-90 行 |
| 执行模板（7 步） | 有 | 第 92-100 行 |
| 验收门槛（5 类变更） | 有 | 第 102-110 行 |
| 风险与禁令（7 条） | 有 | 第 112-120 行 |
| 明确排除 | 有 | 第 122-129 行 |
| 当前下一步 | 有 | 第 131-135 行 |

### 7.2 与 quality-progress-and-lessons.md 的一致性

`docs/quality-progress-and-lessons.md` 第 319 行明确约定：
> "本文件是质量打磨阶段的进度台账，不替代用户指定的计划文件；如果用户指定 `docs/**/plans/*.md`，仍以计划文件为唯一进度台账并随做随回写"

计划文件第 6 行也声明：
> "后续按批次推进时以本文档作为进度台账，每完成一批必须回写状态、验证结果和残留风险"

两份文档的层级关系明确：计划文件是唯一进度台账，quality ledger 是全局上下文索引。不冲突。

### 7.3 回写机制

执行模板第 5 步（第 98 行）：
> "回写本文档：状态、实际修改文件、验证结果、残留风险"

验收门槛表（第 102-110 行）按 5 种变更类型定义了最低验证和升级验证标准。

回写机制可操作、可审计。

**结论**：可以作为唯一推进台账。

---

## 8. B0 代码实现引用验证

计划对 B0 的描述（第 14、65 行）：
- `QueryResponse` 已提交 `2888796`
- `QuerySourceResponse` / `QueryArticleResponse` 已由 agentA 改造并完成审查

经核实：
- `git log --oneline` 确认 `2888796` 存在且内容为 QueryResponse 构造器收敛
- B0 pre-commit 审查报告（`query_source_article_response_pre_commit_quality_review_report.md`）结论为 PASS
- 当前工作区中 QuerySourceResponse.java / QueryArticleResponse.java 的 diff 与计划描述一致

计划对 B0 的引用准确，无失真。

---

## 9. 合规声明

- 本轮未修改 `src/main/java`、`src/test/java`、`src/main/resources`、`scripts`
- 本轮未修改 `docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md`
- 本轮未修改 `docs/quality-progress-and-lessons.md`
- 本轮未修改 `docs/模型绑定配置参考.md`、`special_cases_report.md`
- 本轮未读取 hidden eval
- 本轮未 stage、未 commit、未 push
- 本轮未清库、未重建、未重导
- 本轮新增报告：`model_contract_javadoc_lombok_plan_review_analysis_report_v2.md`

---

## 附录：实扫验证数据

```
# State 类（6 个，与计划 B18 一致）
src/main/java/com/xbk/lattice/compiler/config/CompileReviewConfigState.java
src/main/java/com/xbk/lattice/compiler/graph/CompileGraphState.java
src/main/java/com/xbk/lattice/query/deepresearch/graph/DeepResearchState.java
src/main/java/com/xbk/lattice/query/graph/QueryGraphState.java
src/main/java/com/xbk/lattice/query/service/QueryRetrievalSettingsState.java
src/main/java/com/xbk/lattice/query/service/QueryVectorConfigState.java

# @ConfigurationProperties 类（14 个，排除 LatticeApplication）
src/main/java/com/xbk/lattice/compiler/config/CompilationWalProperties.java
src/main/java/com/xbk/lattice/compiler/config/CompileGraphProperties.java
src/main/java/com/xbk/lattice/compiler/config/CompileJobProperties.java
src/main/java/com/xbk/lattice/compiler/config/CompileReviewProperties.java
src/main/java/com/xbk/lattice/compiler/config/CompilerProperties.java
src/main/java/com/xbk/lattice/compiler/config/LlmProperties.java
src/main/java/com/xbk/lattice/compiler/graph/CompileWorkingSetProperties.java
src/main/java/com/xbk/lattice/query/deepresearch/store/DeepResearchWorkingSetProperties.java
src/main/java/com/xbk/lattice/query/graph/QueryWorkingSetProperties.java
src/main/java/com/xbk/lattice/query/service/QueryCacheProperties.java
src/main/java/com/xbk/lattice/query/service/QueryReviewProperties.java
src/main/java/com/xbk/lattice/query/service/QuerySearchProperties.java
src/main/java/com/xbk/lattice/query/service/QuerySemanticRules.java
src/main/java/com/xbk/lattice/source/config/SourceAdminProperties.java

# Controller 内部 static DTO（29 个，与计划 B11a/B11b/B11c 一致）
AdminLlmConfigController: 10
AdminLlmConnectionTestController: 2
AdminLlmModelTestController: 2
AdminDocumentParseConnectionController: 4
AdminDocumentParseConnectionTestController: 2
AdminDocumentParsePolicyController: 2
AdminDocumentParseProviderDescriptorController: 3
AdminSourceController: 4
```
