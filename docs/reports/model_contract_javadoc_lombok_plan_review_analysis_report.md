# 模型契约注释与 Lombok 治理计划 — 只读审查报告

审查时间：2026-05-31
审查人：agentB（治理/链路分析 Agent）
审查对象：`docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md`
审查类型：只读，未修改任何文件

---

## 1. 总体结论

**NEEDS_REVISION** — 计划整体结构合理，分批顺序正确，Lombok 策略安全。但存在 3 个必须修改项（候选范围遗漏、B11 批次过大、Config 数量不准）和 5 个建议修改项。修正后可批准作为唯一推进台账。

---

## 2. 必须修改项

### 2.1 候选范围遗漏：query/service 内部关键 DTO

**问题**：以下类不在任何批次中，但它们是跨 query 链路传递的核心数据对象，字段级注释缺失严重：

| 类 | 位置 | 字段数 | 手写 getter | Lombok | 影响范围 |
|---|---|---|---|---|---|
| `QueryArticleHit` | `query/service/` | 10 | 10 | 无 | 所有检索→RRF→answer 链路 |
| `RetrievalStrategy` | `query/service/` | ~8 | 手写 | 无 | 检索策略解析与调度 |
| `RetrievalChannelRun` | `query/service/` | ~6 | 手写 | 无 | 检索审计通道运行记录 |

`QueryArticleHit` 尤其关键——它有 `@JsonCreator` 全参构造器 + `@JsonProperty`、10 个 final 字段、全部无字段级 Javadoc。它与 `QueryResponse` 的模式完全一致，应纳入治理。

**建议**：
- 新增批次 **B0.5**（或 B4 之前插入）：`query/service` 检索核心 DTO（`QueryArticleHit`、`RetrievalStrategy`、`RetrievalChannelRun`）
- 或直接并入 B4（`api/compiler` + `admin/service` 之后），但优先在 api/query 批次附近处理

### 2.2 B11 批次过大：29 个 controller 内部 DTO 需拆分

**问题**：计划 B11 写为 "controller 内部 DTO：AdminLlm*Controller、AdminDocumentParse*Controller、AdminSourceController 内部 @Data 类"。实扫发现共 **29 个内部 static DTO 类**分布在 8 个 Controller 文件中：

| Controller | 内部 DTO 数量 |
|---|---|
| `AdminLlmConfigController` | 10 |
| `AdminLlmConnectionTestController` | 2 |
| `AdminLlmModelTestController` | 2 |
| `AdminDocumentParseConnectionController` | 4 |
| `AdminDocumentParseConnectionTestController` | 2 |
| `AdminDocumentParsePolicyController` | 2 |
| `AdminDocumentParseProviderDescriptorController` | 3 |
| `AdminSourceController` | 4 |

29 个类在一批中超出 "5-10 个类" 的批次上限。且这些内部 DTO 已有 `@Data`，注释需求与其他 admin DTO 不同（重点在连接/绑定安全语义，不是通用 API 契约）。

**建议**：将 B11 拆为 3 个子批次：
- **B11a**：LLM config/connection/model test 内部 DTO（14 个类，3 个 Controller）
- **B11b**：Document parse connection/policy/provider 内部 DTO（11 个类，4 个 Controller）
- **B11c**：AdminSourceController 内部 DTO（4 个类）

### 2.3 Config/Properties 候选计数不准

**问题**：计划审计统计写 `compiler/config: 8` + `source/config: 1` = 9 个配置类。实扫发现 **14 个** `@ConfigurationProperties` / 配置类：

| 计划已覆盖 | 计划遗漏 |
|---|---|
| `CompilerProperties`、`CompileGraphProperties`、`CompileJobProperties`、`CompileReviewProperties`、`CompilationWalProperties`、`LlmProperties`、`SourceAdminProperties`、`CompileWorkingSetProperties`（在 compiler/graph 下）| `QueryWorkingSetProperties`、`DeepResearchWorkingSetProperties`、`QueryCacheProperties`、`QueryReviewProperties`、`QuerySearchProperties`、`LatticeCliConfig` |

遗漏的 5-6 个需要补入 B12，或将 B12 细化为 `compiler/config` 和 `query/config` 两个子批。

---

## 3. 建议修改项

### 3.1 缺少 persistence Record 类的明确范围说明

计划覆盖了 API DTO（99）、domain/config（85），但审计统计中 **没有列出 `infra/persistence/*Record.java`（43 个类）**。这些是 MyBatis 映射的 entity-like 类，当前：
- 37/43 手写全参构造器 + 手写 getter（421 个手写 getter）
- 6/43 已用 `@Getter` + `@AllArgsConstructor`（DeepResearch Records）

**建议**：在计划中明确写一条：
> `infra/persistence/*Record.java`（43 个类）暂不纳入本轮治理。Record 的 MyBatis constructor 映射与手写构造器依赖复杂，需先完成 API DTO 和 domain 层治理后再单独评估。若未来纳入，需要逐类验证 MyBatis resultMap 兼容性。

### 3.2 缺少 graph state 类的明确范围说明

当前 graph state 类的分布：
- 已用 `@Data`：`QueryGraphState`、`CompileGraphState`、`DeepResearchState`
- 未用 Lombok、手写 getter/setter：`QueryRetrievalSettingsState`（14 字段/11 getter）、`QueryVectorConfigState`（12 字段/10 getter）、`CompileReviewConfigState`

B18 写了 "graph/state 类" 但描述偏重 deep research 运行态对象。`QueryRetrievalSettingsState` 等是"检索配置的运行态缓存"，语义介于 config 和 state 之间，需单独归类。

**建议**：在 B18 描述中明确列出所有 state 类，或拆出 `QueryRetrievalSettingsState` / `QueryVectorConfigState` / `CompileReviewConfigState` 到 B12 附近（它们是配置缓存的投影，更接近 config 语义）。

### 3.3 B5-B10（api/admin 6 个批次）粒度可进一步细化

当前 B5-B10 覆盖 73 个 admin DTO，每批约 12 个类。这是合理粒度。但有几个批次可能偏大：

| 批次 | 预估类数 | 评估 |
|---|---|---|
| B5 (source/vault/repo/lifecycle) | ~15-20 | 偏大，建议拆为 B5a (source/credential/sync) + B5b (vault/repo/lifecycle) |
| B8 (article/fact card/quality) | ~12-15 | 合理 |
| B10 (overview/pending/task) | ~10-12 | 合理 |

**建议**：B5 可拆为 2 个子批，其他保持不变。

### 3.4 缺少 `@JsonProperty` 字段名与 Java 字段名一致性检查项

部分 API Response DTO 的 `@JsonCreator` 构造器参数上标了 `@JsonProperty("answer")`，但计划中没有提到需要检查**所有 @JsonProperty 名称是否与 Java field 名称一致**。如果后续某轮将字段改名（如 `answer` → `answerMarkdown`）但忘记同步修改 `@JsonProperty`，会导致 Jackson 反序列化断裂。

**建议**：在 B20（全局复扫）中增加一条检查项：
> 扫描所有 `@JsonProperty` 参数名与对应 Java 字段名的一致性，确保无遗漏。

### 3.5 B0 状态描述建议微调

当前写 `QueryResponse 已提交 2888796；QuerySourceResponse / QueryArticleResponse 当前由 agentA 改造中，待验证与提交`。描述准确，但建议增加：

> B0 改造完成后，需由 agentD 做一次真实 API 调用验证 `QueryResponse` JSON 格式不退化（至少验证 `/api/v1/query/ask` 的返回结构不变）。

原因：`QueryResponse` 是最核心的对外 API 响应类，Lombok `@Getter` 引入 + 构造器收敛后的 Jackson 序列化行为必须端到端验证，不能只靠单元测试。

---

## 4. 候选范围修正建议

### 4.1 应补入的类

| 类 | 原因 | 建议批次 |
|---|---|---|
| `QueryArticleHit` | 跨检索→RRF→answer 核心 DTO，10 字段无注释 | B4 附近（或新增 B0.5） |
| `RetrievalStrategy` | 检索策略配置运行时投影 | 同上 |
| `RetrievalChannelRun` | 检索审计通道运行记录 | 同上 |
| `QueryRetrievalSettingsState` | graph state 运行态缓存，14 字段手写 getter | B18 或 B12 |
| `QueryVectorConfigState` | 向量配置运行态缓存 | B18 或 B12 |
| `CompileReviewConfigState` | 审查配置运行态缓存 | B18 或 B12 |
| `QueryWorkingSetProperties` | 遗漏的 config properties | B12 |
| `DeepResearchWorkingSetProperties` | 遗漏的 config properties | B12 |
| `QueryCacheProperties` | 遗漏的 config properties | B12 |
| `QueryReviewProperties` | 遗漏的 config properties | B12 |
| `QuerySearchProperties` | 遗漏的 config properties | B12 |
| `LatticeCliConfig` | CLI 配置（不属于 compiler/query bucket） | B12 |

### 4.2 应明确排除的类

| 类 | 原因 |
|---|---|
| `infra/persistence/*Record.java`（43 个） | MyBatis constructor 映射依赖复杂，需先完成 DTO 治理后单独评估 |
| `infra/persistence/*JdbcRepository.java` | 仓储类，非数据模型 |
| `infra/persistence/mapper/*.java` | MyBatis 接口，非数据模型 |
| `*Controller.java` | 控制器类，非数据模型（但内部 static DTO 仍需处理） |
| `*ExceptionHandler.java` | 异常处理类 |
| `*Support.java` / `*Orchestrator.java` | 服务编排类（除非包含内部 DTO） |

---

## 5. 批次拆分建议

### 5.1 修正后的完整批次表

| 批次 | 范围 | 预估类数 | 变更 |
|---|---|---|---|
| B0 | `api/query` 核心：`QueryResponse`、`QuerySourceResponse`、`QueryArticleResponse` | 3 | 不变（进行中） |
| B0.5 | `query/service` 检索核心 DTO：`QueryArticleHit`、`RetrievalStrategy`、`RetrievalChannelRun` | 3 | **新增** |
| B1 | `api/query` 引用 DTO | 4 | 不变 |
| B2 | `api/query` 结构化证据 DTO | 5 | 不变 |
| B3 | `api/query` 搜索/pending/request/error DTO | 7 | 不变 |
| B4 | `api/compiler` + `admin/service` | 6 | 不变 |
| B5a | `api/admin` source/credential/sync DTO | ~10 | 从 B5 拆分 |
| B5b | `api/admin` vault/repo/lifecycle DTO | ~10 | 从 B5 拆分 |
| B6 | `api/admin` vector/retrieval config DTO | ~10 | 不变 |
| B7 | `api/admin` compile job/review DTO | ~12 | 不变 |
| B8 | `api/admin` article/fact card/quality DTO | ~12 | 不变 |
| B9 | `api/admin` query feedback/retrieval audit DTO | ~10 | 不变 |
| B10 | `api/admin` overview/pending/task DTO | ~10 | 不变 |
| B11a | controller 内部 DTO：LLM config/connection/model test | 14 | B11 拆分 |
| B11b | controller 内部 DTO：document parse connection/policy/provider | 11 | B11 拆分 |
| B11c | controller 内部 DTO：AdminSourceController | 4 | B11 拆分 |
| B12a | `compiler/config` + `source/config` + `LatticeCliConfig` | 10 | 拆分为 config |
| B12b | `query/*` config: QuerySearch/Cache/Review + WorkingSet + DeepResearchWorkingSet | 6 | 拆分为 config |
| B13 | `compiler/domain` + `compiler/ast` | 14 | 不变 |
| B14 | `documentparse/domain` | 14 | 不变（含 2 个 API-like + 10 domain） |
| B15 | `source/domain` | 9 | 不变 |
| B16 | `llm/domain` | 4 | 不变 |
| B17 | `query/domain` + `query/evidence/domain` | 23 | 不变 |
| B18 | `query/deepresearch/domain` + graph state 类（明确列出所有 state） | ~16 | 扩大范围 |
| B19 | `governance/domain` | 5 | 不变 |
| B20 | 全局复扫 | — | 增加 @JsonProperty 一致性检查 |

**总计：约 210 个候选类（修正后），24 个批次。**

### 5.2 批次顺序理由

1. **B0-B3** 先完成 `api/query`（对外 API 门面），建立样板
2. **B0.5** 处理 `query/service` 核心 DTO（与 api/query 紧耦合的检索对象）
3. **B4-B11** 处理其余 API 边界（compiler + admin + 内部 DTO）
4. **B12-B19** 处理 domain/config/graph state（内部模型，依赖更少）
5. **B20** 全局复扫收口

---

## 6. Lombok / Jackson / Spring Binding / Entity-like 风险

### 6.1 计划已正确识别的风险

| 风险 | 计划覆盖 | 评价 |
|---|---|---|
| @JsonCreator 类不能用 @Data | Lombok 规则表明确禁止 | 正确 |
| @ConfigurationProperties 保持现有绑定 | 风险禁令第 7 条 | 正确 |
| Entity-like 不改构造器 | 风险禁令第 6 条 | 正确 |
| 不全局替换 @Builder | 风险禁令第 2 条 | 正确 |

### 6.2 计划未充分强调的风险

| 风险 | 严重程度 | 建议 |
|---|---|---|
| **Spring @ConfigurationProperties + @Data 组合** | 中 | `@Data` 生成 getter/setter，JavaBeans 绑定依赖 getter/setter 命名。如果字段名是 `maxRetryCount`，getter 是 `getMaxRetryCount()`，绑定 key 是 `max-retry-count`。使用 `@Data` 对此无影响——**但前提是现有 getter/setter 命名已正确**。建议 B12 中明确写：先审计现有 getter/setter 命名是否与配置 key 的 relaxed binding 一致，再决定是否引入 Lombok |
| **@Builder + @JsonCreator 冲突** | 高 | `QueryResponse` 的 B0 改造中已引入 `@Builder`。如果同时有 `@Builder` 和 `@JsonCreator`，Jackson 反序列化会使用 `@JsonCreator` 构造器（不受 `@Builder` 影响）。但如果某类只有 `@Builder` 没有 `@JsonCreator`，Jackson 无法反序列化。建议在注释标准中明确写：API Response 类必须有 `@JsonCreator` 或无参构造器，`@Builder` 只能作为补充 |
| **Controller 内部 @Data 类的安全审计** | 中 | B11 中的 29 个内部 DTO 已用 `@Data`。它们被用作 LLM 连接/API key/凭证的请求体——包含敏感字段。`@Data` 生成的 `toString()` 会将所有字段（包括 key/token）序列化为字符串。如果日志中打印了这些对象，可能泄露敏感信息。建议 B11 中增加检查：确认这些 @Data 类不会被 toString 输出到日志 |

### 6.3 尚未出现的风险（但计划应提及）

| 场景 | 风险 | 建议 |
|---|---|---|
| `infra/persistence/*Record.java` 未来治理 | MyBatis `<constructor>` 映射 + `@AllArgsConstructor` 参数顺序不一致 | 当前排除，未来评估时逐类验证 |
| `@JsonProperty` 与字段改名 | Jackson 反序列化断裂 | 已建议在 B20 增加一致性检查 |

---

## 7. 注释标准评价

### 7.1 分层标准质量

计划的"注释标准"表按 API/Domain/Entity/Config/Graph/Enum 六层定义了每层字段注释应回答的问题和不应写的内容。这是计划中最强的部分——层次清晰、边界明确、有正例和反例。

**评价**：PASS。无需修改。

### 7.2 缺少的模板

建议在注释标准中增加**一个具体正例 + 反例对照**（以 `QueryResponse.answer` 为样例），帮助 agentA 理解期望的注释深度：

**正例**（已在之前 DTO 分析报告中给出）：
```java
/**
 * 最终答案的 Markdown 文本。
 *
 * <p>写入方：AnswerGenerationPayloadOrchestrator。
 * 当 generationMode=NO_ANSWER 时为空字符串，不为 null。
 * 与 citationMarkers 中的引用点索引对应。
 */
```

**反例**（应避免）：
```java
/** 答案 */                  // 仅翻译字段名
/** The answer string */    // 不提供运行语义
/** 由后端生成 */            // 模糊，不说谁生成
```

---

## 8. 验证门槛评价

### 8.1 当前门槛设计

| 变更类型 | 计划要求 | 评价 |
|---|---|---|
| 仅字段 Javadoc | 可选测试，自查 | PASS — 注释不改行为 |
| Lombok 注解 | 定向测试 + 编译 | PASS |
| 构造器收敛 | 搜索残留 + 定向测试 | PASS — 但缺少 API 响应 JSON 格式回归 |
| Config properties | Spring context 测试 | PASS |
| Entity-like | 持久化测试 | PASS |

### 8.2 建议补强

- **B0/B1/B2（api/query 对外 API）**：每批完成后，agentD 做一次真实 API 调用（`/api/v1/query/ask`）验证 JSON 格式不退化
- **B12（Config properties）**：启动时检查 Spring 无 `Cannot resolve configuration property` 警告
- **B11（Controller 内部 DTO）**：检查 toString 输出不包含敏感字段值（apiKey/token/password）

---

## 9. 是否需要拆分为两个计划

**结论：不需要。保留一个总台账。**

理由：
- API DTO 和 domain/config 的注释标准不同，但 Lombok 策略有重叠——统一管理可避免一个类同时被两个计划交叉修改
- 分批顺序确保了 "API DTO 优先、domain/config 在后"，不会交叉执行
- 拆成两个计划会增加台账同步成本（质量打磨台账、API DTO 台账、Domain 台账三个文件需同时维护）

但建议在计划中加一条明确约束：
> B0-B11 为"API 边界治理阶段"，B12-B19 为"内部模型治理阶段"。两个阶段串行执行，B0-B11 全部完成后（含 B20 中间检查）再进入 B12。

---

## 10. 给下一轮 agent 的建议

### 10.1 给 agentC（计划修订）

1. 按本报告第 2 节修正 3 个必须修改项
2. 按本报告第 4.1 节补入遗漏的 12 个候选类
3. 按本报告第 4.2 节明确排除 persistence Record
4. 按本报告第 5.1 节更新批次表和类数统计
5. 在注释标准中增加正例/反例对照
6. 修订后状态从 "草案，待 agentB 只读审查" 改为 "已审查，待推进 B0"

### 10.2 给 agentA（B0 完成后）

B0 当前状态：`QueryResponse` 已提交，`QuerySourceResponse` / `QueryArticleResponse` 由 agentA 改造中。完成后：
1. 验证：定向测试 + agentD 真实 API 调用（`/api/v1/query/ask` JSON 格式不退化）
2. 提交 B0 三个类
3. 回写计划 B0 状态为 "已完成"

### 10.3 给 agentB（后续审查）

- B0-B3 全部完成后可再做一次中间审查，确认样板是否正确推广
- B11 执行前单独审查 controller 内部 DTO 的 toString 安全性

---

## 附录 A：扫描命令摘要

```bash
# Lombok 使用统计
grep -rh "@Data\|@Getter\|@Setter\|@Builder\|@Slf4j" src/main/java --include="*.java" | sort | uniq -c

# Controller 内部 @Data 类
grep -rl "@Data" src/main/java/com/xbk/lattice/api --include="*.java"

# Graph State 类
find src/main/java -name "*State.java" -o -name "*GraphState.java" | grep -v test

# Config/Properties 类
find src/main/java -name "*Properties.java" -o -name "*Config.java" | grep -v test

# @ConfigurationProperties 类
grep -rl "@ConfigurationProperties" src/main/java --include="*.java"

# @JsonCreator 类
grep -rl "@JsonCreator" src/main/java --include="*.java"

# Controller 内部 static DTO 数量
grep -c "static class" src/main/java/com/xbk/lattice/api/admin/AdminLlmConfigController.java
```

---

## 附录 B：完整候选类清单修正对照

| 计划原计数 | 修正计数 | 变化原因 |
|---|---|---|
| api/admin: 73 | 73 | 不变 |
| api/query: 18 | 18 | 不变 |
| api/compiler: 4 | 4 | 不变 |
| admin/service: 2 | 2 | 不变 |
| documentparse: 12 | 12 | 不变（含 domain/model + domain） |
| query/evidence: 14 | 14 | 不变 |
| query/deepresearch: 11 | 11 | 不变 |
| documentparse/domain: 10 | 10 | 不变 |
| source/domain: 9 | 9 | 不变 |
| query/domain: 9 | 9 | 不变 |
| compiler/config: 8 | **10** | +2（CompileWorkingSetProperties 已在内，补 LatticeCliConfig 需单独归类） |
| compiler/domain: 7 | 7 | 不变 |
| compiler/ast: 7 | 7 | 不变 |
| governance/domain: 5 | 5 | 不变 |
| llm/domain: 4 | 4 | 不变 |
| source/config: 1 | 1 | 不变 |
| — | **+6** | query/config: QuerySearch/Cache/Review/WorkingSet/DeepResearchWorkingSet（~5）+ LatticeCliConfig |
| — | **+3** | query/service: QueryArticleHit, RetrievalStrategy, RetrievalChannelRun |
| — | **+3** | graph state: QueryRetrievalSettingsState, QueryVectorConfigState, CompileReviewConfigState |
| — | **+29** | controller 内部 DTO（B11，已覆盖但未独立计数） |
| **总计 ~182** | **总计 ~210** | 净增 ~28 |

---

## 合规声明

- 本轮未修改 `src/main/java`、`src/test/java`、`src/main/resources`、`scripts`
- 本轮未修改 `docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md`
- 本轮未修改 `docs/quality-progress-and-lessons.md`
- 本轮未读取 hidden eval
- 本轮未 stage、未 commit、未 push
- 本轮未清库、未重建、未重导
- 本轮新增报告：`model_contract_javadoc_lombok_plan_review_analysis_report.md`
