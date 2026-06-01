# B12a Compiler/Source/CLI Config 只读边界审查报告

生成时间：2026-06-01
审查人：agentB（只读边界审查）
状态：完成
目标批次：B12a — `compiler/config` + `source/config` + `LatticeCliConfig`

---

## 一、最终 B12a 纳入清单（7 个类，无需拆分）

| # | 类名 | 配置前缀 | 字段数 | 嵌套类 | Lombok | 处置 |
|---|---|---|---|---|---|---|
| 1 | `CompilerProperties` | `lattice.compiler` | 6 顶层 + 5 嵌套类 | DocumentTopics(28)、HeadingPatternRule(6)、GroupingRule(2)、FileRanking(1)、FileRankingRule(2) | 无 | 字段 Javadoc 升级（含嵌套类） |
| 2 | `CompileJobProperties` | `lattice.compiler.jobs` | 7 | — | 无 | 字段 Javadoc 升级 |
| 3 | `CompileGraphProperties` | `lattice.compiler.graph` | 4 | — | 无 | 字段 Javadoc 升级 |
| 4 | `LlmProperties` | `lattice.llm` | 10 顶层 + 4 嵌套类 | Admin(2)、ChatClient(6)、CompileTimeout(3)、Pricing(4) | 无 | 字段 Javadoc 升级，`secretEncryptionKey` 安全标注 |
| 5 | `CompileReviewProperties` | `lattice.compiler.review` | 4 | — | 无 | 字段 Javadoc 升级 |
| 6 | `CompilationWalProperties` | `lattice.compiler.wal` | 2 | — | 无 | 字段 Javadoc 升级 |
| 7 | `SourceAdminProperties` | `lattice.source.admin` | 1 | — | 无 | 字段 Javadoc 升级，`stagingRootDir` 路径风险标注 |

**无需拆分**：7 个文件在 10 个上限以内。但注意 `CompilerProperties`（969 行）和 `LlmProperties`（600 行）体量较大，嵌套类多，agentA 需合理安排时间。

---

## 二、明确排除清单及理由

| 排除文件 | 理由 | 去向 |
|---|---|---|
| `CompileReviewConfigState` | 不是 @ConfigurationProperties 类，是运行时状态快照。计划 B12b 已明确列出 `CompileReviewConfigState` 归入 state 类批次 | **B12b** |
| `CompileReviewConfigService` | `@Service` 注解，是 Spring service 编排类（持久化覆盖→运行时属性同步），不属于配置契约治理。含 `@PostConstruct`、`save()`、`apply()` 等编排逻辑 | **排除**（非 config） |
| `LatticeCliConfig` | `@Configuration @Profile("cli")`，是 Spring 标记/激活类，**零字段**。无配置属性可注释，无 Lombok 可治理 | **排除**（零字段） |

### CompileReviewConfigState 归属说明

虽然该类位于 `compiler/config` 包，但：
- 它不是 `@ConfigurationProperties` 类（无 Spring 属性绑定）
- 它是不可变 final-field 类，9 个手写 getter，可用 `@Getter` 替代
- 计划 B12b 已明确将其与 `QueryRetrievalSettingsState`、`QueryVectorConfigState` 归为同一批次（state 类）
- 处置建议：移入 B12b，按其 state 类规则处理（类级 @Getter + 字段语义标注）

---

## 三、各纳入类的详细分析

### 关键全局发现

- **0 个 @Data**：所有 7 个配置类均使用手写 getter/setter（标准 JavaBean 模式），无 Lombok 注解。这与 B0-B11 的 API DTO 完全不同。
- **绑定方式均为 `@ConfigurationProperties`**：Spring Boot 通过 getter/setter 进行 relaxed binding。**不应引入 Lombok @Getter/@Setter**，保持当前绑定方式不变。
- **字段 Javadoc 缺口**：所有字段的 Javadoc 均在 getter 上（"获取xxx"/"返回xxx"），属于字段名翻译，不符合计划的 Config Properties 注释标准。需要在字段声明处补充**配置前缀、默认值、开关影响、fail-open/fail-closed 语义、变更后影响的链路**。

### 3.1 CompilerProperties（`lattice.compiler`）⚠️ 大文件

- 969 行，含 5 个嵌套 static class
- 无 Lombok，标准 JavaBean getter/setter
- 顶层 6 字段 + DocumentTopics(28) + HeadingPatternRule(6) + GroupingRule(2) + FileRanking(1) + FileRankingRule(2) = **45 个配置属性**

**顶层字段风险**：
| 字段 | 默认值 | 运行影响 |
|---|---|---|
| `ingestMaxChars` | 65536 | 单文件最大采集字符数；超过截断，影响大文档的内容完整性 |
| `batchMaxChars` | 40000 | LLM 批处理最大字符数；超过分批，影响编译并发度和 LLM 调用次数 |
| `defaultGroup` | `"defaultGroup"` | 默认分组名；影响未匹配分组规则的文件的归属 |

**DocumentTopics 关键字段**：
| 字段 | 默认值 | 开关/阈值语义 |
|---|---|---|
| `enabled` | true | 长文档专题拆分总开关；false 时整份文档作为单个概念处理，LLM 上下文可能超限 |
| `longDocumentMinChars` | 12000 | 触发拆分的文档最小字符数；低于此值的文档不拆分 |
| `mediumDocumentMinChars` | 6000 | 中等结构化文档最小字符数；需同时满足 minHeadingsForMediumDocument |
| `minTopicChars` / `maxTopicChars` | 700 / 22000 | 拆出专题的字符数边界；过小产生碎片，过大 LLM 上下文超限 |
| `enabled` (fail-closed) | true | false 时**所有文档不再拆分**，可能导致 LLM 输入超限→编译失败 |
| `pageMarkerPattern` | null | 页码正则；null 时不识别页码，页眉页脚可能混入正文 |

**FileRanking / GroupingRule**：影响文件编译优先级和分组逻辑，修改后影响编译顺序和批处理分组。

### 3.2 CompileJobProperties（`lattice.compiler.jobs`）

- 7 字段，无嵌套类，无 Lombok

| 字段 | 默认值 | 运行影响 |
|---|---|---|
| `workerEnabled` | true | 后台编译 worker 开关；false 时编译作业提交后不被自动执行（fail-closed：作业永久 PENDING） |
| `pollDelayMs` | 1000 | worker 轮询间隔；影响作业响应延迟 |
| `workerId` | `worker-{pid}@{host}` | 当前实例标识；由 `ManagementFactory.getRuntimeMXBean()` 自动生成 |
| `heartbeatIntervalSeconds` | 15 | 心跳间隔；短于此值频繁续租，增加 DB 压力 |
| `leaseDurationSeconds` | 300 | 运行租约时长；worker 崩溃后此时间过后其他 worker 才可抢占 |
| `stalledThresholdSeconds` | 600 | 疑似卡住阈值；超过此时间无心跳标记 STALLED |
| `uploadRootDir` | `java.io.tmpdir/lattice-admin-uploads` | 上传暂存目录；路径遍历风险（用户可控文件名），需标注 |

### 3.3 CompileGraphProperties（`lattice.compiler.graph`）

- 4 字段，无嵌套类，无 Lombok

| 字段 | 默认值 | 开关/语义 |
|---|---|---|
| `enabled` | true | Graph 编排开关；false 时回退到 service 直接调用（fail-open：自动回退） |
| `allowServiceFallback` | true | 是否允许回退到 service；false 时 Graph 失败直接抛异常 |
| `persistStepLog` | true | 是否持久化步骤日志；false 时 Graph 执行过程不可审计 |
| `stepLogFailureMode` | `"warn"` | 步骤日志失败时的处理模式：`warn` 仅日志告警，`fail` 抛出异常阻断编译 |

### 3.4 LlmProperties（`lattice.llm`）⚠️ 含默认加密种子

- 600 行，含 4 个嵌套 static class，无 Lombok
- 顶层 10 字段 + Admin(2) + ChatClient(6) + CompileTimeout(3) + Pricing(4) = **25 个配置属性**

**关键风险字段**：

| 字段 | 默认值 | 风险 |
|---|---|---|
| `secretEncryptionKey` | `"lattice-phase8-bootstrap-key-change-me"` | **默认加密种子**。这个默认值是开发/测试占位，生产环境必须通过配置文件覆盖。需要在 Javadoc 中明确标注"仅用于开发环境，生产环境必须替换"。若生产环境未覆盖，所有加密密钥可被此种子解密 |
| `bootstrapEnabled` | true | Bootstrap 配置回退开关；true 时数据库配置不可用时使用本地 application.yml 兜底 |
| `configSource` | `"hybrid"` | 配置源模式：`hybrid` 优先数据库→回退本地；`database` 仅数据库；`properties` 仅本地 |
| `budgetUsd` | 10.0 | LLM 调用预算上限（美元）；超出后停止调用（fail-closed：预算耗尽阻塞编译） |
| `reviewEnabled` | false | 真实审查开关；**默认关闭**（安全默认），开启后每次编译触发 LLM 审查 |
| `cacheTtlSeconds` | 86400 | 缓存 TTL；影响 LLM 响应复用率和成本 |

**ChatClient 嵌套类**：6 个按 purpose 的灰度开关（enabled/queryAnswerEnabled/queryRewriteEnabled/queryReviewEnabled/compileReviewEnabled/governanceJsonEnabled），控制新旧执行栈的切换。全部默认 true（fail-open：失败回退到旧栈）。

**Admin 嵌套类**：`encryptSecrets`（默认 true）、`maskSecrets`（默认 true）— 密钥加密与脱敏开关，false 时密钥明文存储。

**CompileTimeout 嵌套类**：writer/reviewer/fixer 三个角色的默认超时秒数，影响 LLM 调用超时行为。

**Pricing 嵌套类**：bootstrap 回退定价，不依赖 provider 名称的估算费率。

### 3.5 CompileReviewProperties（`lattice.compiler.review`）

- 4 字段，无嵌套类，无 Lombok

| 字段 | 默认值 | 开关语义 |
|---|---|---|
| `autoFixEnabled` | true | 自动修复总开关 |
| `maxFixRounds` | 1 | 最大修复轮次；0 表示不修复，仅审查 |
| `allowPersistNeedsHumanReview` | false | 是否允许需人工复核文章落库（fail-closed：默认阻止） |
| `humanReviewSeverityThreshold` | `"HIGH"` | 触发人工复核的最低严重度；可选 HIGH/MEDIUM/LOW |

### 3.6 CompilationWalProperties（`lattice.compiler.wal`）

- 2 字段，无嵌套类，无 Lombok
- `keyPrefix`：Redis WAL key 前缀（默认 `"lattice:wal:"`）
- `ttlSeconds`：WAL 条目 TTL（默认 86400）；过期后 WAL 记录自动清理

### 3.7 SourceAdminProperties（`lattice.source.admin`）

- 1 字段，无嵌套类，无 Lombok
- `stagingRootDir`：资料源物化 staging 根目录（默认 `java.io.tmpdir + "/lattice-source-sync"`）
- 路径风险：类似 `CompileJobProperties.uploadRootDir`，用户可控路径写入，需标注

---

## 四、配置绑定风险总结

| 风险类别 | 涉及字段 | 说明 |
|---|---|---|
| **密钥/安全** | `LlmProperties.secretEncryptionKey` | 默认值 `"lattice-phase8-bootstrap-key-change-me"` 是占位种子，生产必须覆盖 |
| **fail-closed（阻塞）** | `workerEnabled`、`DocumentTopics.enabled`、`budgetUsd`、`allowPersistNeedsHumanReview`（默认 false 阻止落库） | 配置错误可导致编译停滞或文章不落库 |
| **fail-open（回退）** | `bootstrapEnabled`、`allowServiceFallback`、`ChatClient.*Enabled` | 全部默认 true，提供自动降级路径 |
| **路径遍历** | `uploadRootDir`、`stagingRootDir` | 用户可控路径/文件名写入，需标注 |
| **成本** | `budgetUsd`、`Pricing.*`、`CompileTimeout.*`、`maxInputChars` | 直接影响 LLM 调用费用和超时 |
| **灰度切换** | `ChatClient.*Enabled` | 6 个独立开关，控制 query/compile/governance 路径的新旧栈切换 |

---

## 五、Lombok 使用分析

**全部 7 个类均无 Lombok**。当前使用手写 getter/setter 满足 Spring Boot `@ConfigurationProperties` 的 JavaBean 绑定要求。

**处置建议**：**不引入 Lombok**。理由：
1. `@ConfigurationProperties` 的 relaxed binding 依赖标准 getter/setter 命名，手写 getter 提供明确的文档入口
2. 所有 getter 已有基本 Javadoc，引入 `@Getter/@Setter` 会丢失这些文档锚点
3. 计划规则明确："Config Properties: 保持当前 Spring Boot 绑定方式；注释写配置语义"
4. 配置类变更风险远高于 API DTO，改绑定方式可能导致 Spring Boot 属性注入失败

**本轮仅做字段级 Javadoc 升级**（从 getter 迁移到字段），不删除任何手写 getter/setter。

---

## 六、给 agentA 的下一轮提示词草案

```
交给 agentA。

本轮任务：对 B12a 的 7 个配置类做 **字段契约 Javadoc 升级**（仅补注释，不改 Lombok/绑定）。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/compiler_source_cli_config_contract_analysis_report.md

## 修改范围（7 个文件，仅补字段 Javadoc）

### 核心原则

- **不引入任何 Lombok 注解**（@Data/@Getter/@Setter）
- **不删除任何手写 getter/setter**
- **不修改任何字段类型、默认值、访问修饰符**
- **不修改 @ConfigurationProperties 前缀**
- 字段 Javadoc 必须回答：配置前缀、默认值、开关语义（fail-open/fail-closed）、变更后影响的编译/LLM/检索链路

### 修改文件列表

1. CompilerProperties.java（大文件，969 行）
   - 顶层 6 字段 + DocumentTopics 28 字段 + HeadingPatternRule 6 字段 + GroupingRule 2 字段 + FileRanking(FileRankingRule) 3 字段
   - 共 45 字段补 Javadoc（审查报告 3.1 节）
   - DocumentTopics.enabled 标注 fail-closed：false 时所有文档不拆分，LLM 上下文可能超限
   - pageMarkerPattern 标注 null 时不识别页码

2. CompileJobProperties.java
   - 7 字段补 Javadoc（审查报告 3.2 节）
   - workerEnabled 标注 fail-closed：false 时作业永久 PENDING
   - uploadRootDir 标注路径遍历风险

3. CompileGraphProperties.java
   - 4 字段补 Javadoc（审查报告 3.3 节）
   - allowServiceFallback 标注 fail-open
   - stepLogFailureMode 标注 warn vs fail 行为差异

4. LlmProperties.java（大文件，600 行）
   - 顶层 10 字段 + Admin 2 字段 + ChatClient 6 字段 + CompileTimeout 3 字段 + Pricing 4 字段
   - 共 25 字段补 Javadoc（审查报告 3.4 节）
   - **secretEncryptionKey 必须标注：默认值为开发占位种子，生产环境必须覆盖**
   - **bootstrapEnabled 标注 fail-open语义**
   - budgetUsd 标注 fail-closed：超出后停止 LLM 调用
   - reviewEnabled 标注默认 false（安全默认）
   - ChatClient 6 个开关标注灰度语义和 fail-open 回退

5. CompileReviewProperties.java
   - 4 字段补 Javadoc（审查报告 3.5 节）
   - allowPersistNeedsHumanReview 标注 fail-closed

6. CompilationWalProperties.java
   - 2 字段补 Javadoc（审查报告 3.6 节）

7. SourceAdminProperties.java
   - 1 字段补 Javadoc（审查报告 3.7 节）
   - stagingRootDir 标注路径风险

## 禁止事项

- 禁止添加任何 Lombok 注解
- 禁止删除/修改任何 getter/setter 方法
- 禁止修改 @ConfigurationProperties 注解或前缀
- 禁止修改字段默认值
- 禁止修改嵌套类的结构和字段
- 禁止修改 CompileReviewConfigState / CompileReviewConfigService / LatticeCliConfig（排除文件）
- 禁止修改 domain/entity/service/controller

## 验收门槛

- mvn compile -pl . -q 通过（验证 Spring @ConfigurationProperties 绑定未破坏）
- Spring context 启动正常（如项目支持）
- 自查：每个字段注释包含“默认值+开关语义+影响链路”

## 完成后：回写 B12a → "已完成"，输出 B12a_fix_result_report.md
```

---

## 七、审查结论

- B12a 最终纳入 **7 个配置类**，排除 CompileReviewConfigState（→B12b）、CompileReviewConfigService（service 类）、LatticeCliConfig（零字段标记类）。
- **无需拆分**，7 个文件在 10 个上限以内。但 CompilerProperties（45 字段）和 LlmProperties（25 字段）体量大，是 B12a 的主要工作量。
- **全部 7 个类无 Lombok**，本轮不引入 Lombok，仅做字段 Javadoc 升级。
- **关键安全发现**：`LlmProperties.secretEncryptionKey` 有硬编码默认值 `"lattice-phase8-bootstrap-key-change-me"`，必须在 Javadoc 中标注生产环境替换要求。
- **fail-closed 高风险字段**：`workerEnabled`（false→作业停滞）、`DocumentTopics.enabled`（false→不拆分→LLM 超限）、`budgetUsd`（超预算→停止调用）、`allowPersistNeedsHumanReview`（默认 false 阻止落库）。
- **路径风险字段**：`uploadRootDir`、`stagingRootDir` 使用 `java.io.tmpdir` 默认值，需标注路径遍历风险。
- 本轮不改变任何 Spring Boot 配置绑定方式。
