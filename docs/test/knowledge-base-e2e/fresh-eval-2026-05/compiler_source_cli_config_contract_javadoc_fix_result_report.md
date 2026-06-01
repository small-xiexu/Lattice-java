# B12a: Compiler/Source 配置类字段契约 Javadoc 报告

改造时间：2026-06-01
改造人：agentA
批次：B12a

---

## 1. 修改文件清单

| 文件 | 配置前缀 | 字段数 | 变更 |
|---|---|---|---|
| `CompilerProperties.java` | `lattice.compiler` | 45（顶层6 + 嵌套39） | 字段级配置语义 Javadoc |
| `CompileJobProperties.java` | `lattice.compiler.jobs` | 7 | 字段级配置语义 Javadoc |
| `CompileGraphProperties.java` | `lattice.compiler.graph` | 4 | 字段级配置语义 Javadoc |
| `LlmProperties.java` | `lattice.llm` | 25（顶层10 + 嵌套15） | 字段级配置语义 Javadoc |
| `CompileReviewProperties.java` | `lattice.compiler.review` | 4 | 字段级配置语义 Javadoc |
| `CompilationWalProperties.java` | `lattice.compiler.wal` | 2 | 字段级配置语义 Javadoc |
| `SourceAdminProperties.java` | `lattice.source.admin` | 1 | 字段级配置语义 Javadoc |

**未改**：Lombok 注解（0 个文件有）、`@ConfigurationProperties` prefix、字段默认值、getter/setter。

---

## 2. 排除文件

| 文件 | 理由 |
|---|---|
| `CompileReviewConfigState.java` | 运行时状态快照，归 B12b |
| `CompileReviewConfigService.java` | `@Service` 编排类，非配置 |
| `LatticeCliConfig.java` | `@Configuration @Profile("cli")`，零字段 |

---

## 3. 关键安全/风险标注

| 字段 | 所属文件 | 标注 |
|---|---|---|
| `secretEncryptionKey` | LlmProperties | **默认值是开发占位种子，生产环境必须覆盖** |
| `stagingRootDir` | SourceAdminProperties | 路径遍历风险，默认 `/tmp/lattice-source-sync` |
| `uploadRootDir` | CompileJobProperties | 路径规范化校验，默认 `/tmp/lattice-admin-uploads` |
| `budgetUsd` | LlmProperties | 超预算后 fail-closed，停止所有 LLM 调用 |

### 开关语义标注

| 字段 | 默认值 | 语义 |
|---|---|---|
| `DocumentTopics.enabled` | true | fail-closed：false 时不拆分长文档，LLM 上下文可能超限 |
| `CompileJobProperties.workerEnabled` | true | fail-closed：false 时作业不会自动执行 |
| `allowPersistNeedsHumanReview` | false | fail-closed：阻止 needs_human_review 文章落库 |
| `reviewEnabled` | false | 安全默认：false 时不触发 LLM 审查 |
| `bootstrapEnabled` | true | fail-open：数据库配置不可用时以本地配置运行 |
| `allowServiceFallback` | true | fail-open：Graph 异常时自动降级到 service |
| `stepLogFailureMode` | warn | warn=日志失败仅警告继续；fail=日志失败中止编译 |
| `autoFixEnabled` | true | false 时所有问题直接进入人工复核队列 |

---

## 4. 验证

```
mvn compile: BUILD SUCCESS
rg -n "lombok|@Data|@Getter|@Setter" (7 文件): (无结果) ✓
secretEncryptionKey 标注: ✓
workerEnabled 标注: ✓
stagingRootDir 标注: ✓
```

---

## 5. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 7 个目标文件 | 通过 |
| 未添加 Lombok 注解 | 通过 |
| 未修改 @ConfigurationProperties prefix | 通过 |
| 未修改字段类型/名称/默认值 | 通过 |
| 未修改 getter/setter | 通过 |
| LatticeCliConfig 排除 | 通过（零字段） |
| CompileReviewConfigState 归 B12b | 通过 |
| 未 stage/commit/push | 通过 |
