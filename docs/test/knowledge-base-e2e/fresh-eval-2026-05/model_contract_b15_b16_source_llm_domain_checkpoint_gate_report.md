# B15-B16 Source Domain + LLM Domain Checkpoint 门禁核查报告

核查时间：2026-06-01
核查人：agentD（只读门禁审计）
范围：B15 + B16 — source/domain（9）+ llm/domain（4）
状态：**PASS — 所有核查项通过，无阻塞问题**

---

## 1. 当前 Git 变更分类

### 1.1 按文件类别统计

| 类别 | 数量 | 状态 |
|---|---|---|
| 生产代码（B15 source/domain） | 9 | 全部不可变 @Getter + Javadoc |
| 生产代码（B16 llm/domain） | 4 | 全部不可变 @Getter + Javadoc |
| **生产代码合计** | **13** | — |
| 计划文档（治理台账） | 1 | 台账更新（B15/B16→已完成），"当前下一步"已修正 |
| 模型绑定配置参考 | 1 | **已知 out-of-scope dirty（不得纳入）** |
| special_cases_report | 1 | **已知 out-of-scope dirty（不得纳入）** |
| 批次报告（untracked） | 2 | 可纳入 |

### 1.2 生产代码详细清单

#### B15: source/domain（9 个不可变领域对象）

| 文件 | 字段/Getter | diff 规模 | 特殊标注 |
|---|---|---|---|
| `BundleSummary.java` | 13 | +117/-? | bundle 元数据聚合 |
| `KnowledgeSource.java` | 16 | +143/-? | configJson/metadataJson 大 JSON/路径风险 |
| `KnowledgeSourcePage.java` | 4 | +56/-? | 分页容器 |
| `SourceCredential.java` | 10 | +150/-? | **secretCiphertext/secretMask 敏感标注** |
| `SourceDecisionResult.java` | 7 | +66/-? | waitConfirm/skippedNoChange 决策语义 |
| `SourceMaterializationResult.java` | 2 | +26/-? | stagingDir Path 类型 |
| `SourceSyncRun.java` | 17 | +152/-? | evidenceJson/errorMessage 大文本 |
| `SourceSyncRunDetail.java` | 42 | +534/-? | **双构造器保留**，42 字段分组标注 |
| `SourceValidationResult.java` | 6 | +58/-? | 验证结果 |

#### B16: llm/domain（4 个不可变领域对象）

| 文件 | 字段/Getter | diff 规模 | 特殊标注 |
|---|---|---|---|
| `LlmProviderConnection.java` | 12 | +178/-? | **apiKeyCiphertext/apiKeyMask 敏感标注** |
| `LlmModelProfile.java` | 19 | +265/-? | MODEL_KIND_* 常量保留，expectedDimensions 风险 |
| `AgentModelBinding.java` | 12 | +170/-? | scene/agentRole 路由绑定语义 |
| `ExecutionLlmSnapshot.java` | 20 | +277/-? | 快照语义（审计/成本/回放），价格快照 |

---

## 2. 核查项逐项结果

### 2.1 生产代码仅限 B15 9 个 + B16 4 个

| 包 | 预期 | 实际 | 匹配 |
|---|---|---|---|
| `source/domain/` | 9 | 9 | ✅ |
| `llm/domain/` | 4 | 4 | ✅ |
| **合计** | **13** | **13** | ✅ |

### 2.2 越界检查

```
git diff --name-only -- src/main/java/com/xbk/lattice/source/ | grep -v "source/domain/"
→ (无输出)
```
source service/controller/infra/mapper 均未修改。

```
git diff --name-only -- src/main/java/com/xbk/lattice/llm/ | grep -v "llm/domain/"
→ (无输出)
```
llm service/config/controller 均未修改。

```
git diff --name-only -- src/main/java/com/xbk/lattice/query/domain/ src/main/java/com/xbk/lattice/query/evidence/
→ (无输出)
```
B17 范围未修改。

**结果：PASS。**

### 2.3 @Data/@Setter/@Builder 检查

```
rg -l "@Data|@Setter|@Builder" <13个文件>
→ SourceCredential.java, LlmProviderConnection.java
```

两个匹配均为 Javadoc 文本"含敏感字段（xxx），禁止引入 `@Data`"——安全标注文档，非实际注解。

**结果：PASS。** 13 个文件 0 个 @Data，0 个 @Setter，0 个 @Builder。

### 2.4 @Getter 检查

| 包 | 文件数 | @Getter | 结果 |
|---|---|---|---|
| source/domain | 9 | 9（全部类级） | ✅ |
| llm/domain | 4 | 4（全部类级） | ✅ |
| **合计** | **13** | **13** | ✅ |

### 2.5 B15 敏感字段安全标注

#### SourceCredential

| 字段 | 行号 | 标注 |
|---|---|---|
| `secretCiphertext` | 29 | 加密存储，非明文但仍敏感，禁止 toString()/日志 |
| `secretMask` | 35 | 仅脱敏展示，非完整凭证 |

**结果：PASS。** 两个字段均有安全 Javadoc。

#### 其他大文本/路径风险字段

| 字段 | 类 | 标注 |
|---|---|---|
| `configJson` | KnowledgeSource | 可能含 repo 路径/Vault 引用 |
| `metadataJson` | KnowledgeSource | 可能较大 |
| `evidenceJson` | SourceSyncRun | 可能较大 |
| `errorMessage` | SourceSyncRun | 可能含异常信息 |

### 2.6 B15 SourceSyncRunDetail 双构造器保留

```
rg -n "public SourceSyncRunDetail" SourceSyncRunDetail.java
→ 第 111 行, 第 138 行（双构造器）
```

**结果：PASS。** 2 个构造器全部保留。

### 2.7 B16 敏感字段安全标注

#### LlmProviderConnection

| 字段 | 行号 | 标注 |
|---|---|---|
| `apiKeyCiphertext` | 31 | 加密存储，非明文但仍敏感，禁止 toString()/日志 |
| `apiKeyMask` | 37 | 脱敏展示（sk-\*\*\*\*xxxx），非完整密钥 |

**结果：PASS。**

### 2.8 B16 MODEL_KIND 常量保留

```
LlmModelProfile.java
→ MODEL_KIND_CHAT = "CHAT"（第 19 行）
→ MODEL_KIND_EMBEDDING = "EMBEDDING"（第 20 行）
```

**结果：PASS。**

### 2.9 计划台账状态

| 批次 | 台账状态 | 验证 |
|---|---|---|
| B15 | 已完成 | mvn compile PASS |
| B16 | 已完成 | mvn compile PASS |
| B17 | 待开始 | — |

**"当前下一步"已修正**：`"B0-B12b 已完成，进入 B16"` → `"B0-B16 已完成（共 180 类），进入 B17"`

**结果：PASS。**

### 2.10 Out-of-scope 文件与报告敏感信息

| 检查项 | 结果 |
|---|---|
| `docs/模型绑定配置参考.md` | 仍为 dirty（不纳入） |
| `special_cases_report.md` | 仍为 dirty（不纳入） |
| B15/B16 报告 API key | 无（rg 无输出） |

---

## 3. 可纳入本次 Checkpoint 的文件清单

### 3.1 计划台账（1 个）

- `docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md`

### 3.2 B15 生产代码（9 个）

| # | 文件 |
|---|---|
| 1-9 | `src/main/java/com/xbk/lattice/source/domain/BundleSummary.java` 等全部 9 个 |

### 3.3 B16 生产代码（4 个）

| # | 文件 |
|---|---|
| 10-13 | `src/main/java/com/xbk/lattice/llm/domain/AgentModelBinding.java` 等全部 4 个 |

### 3.4 批次报告（2 个 untracked）

| 文件名 | 类型 |
|---|---|
| `source_domain_contract_javadoc_lombok_fix_result_report.md` | B15 修复报告 |
| `llm_domain_contract_javadoc_lombok_fix_result_report.md` | B16 修复报告 |

---

## 4. 必须排除的文件

| 文件 | 原因 |
|---|---|
| `docs/模型绑定配置参考.md` | API key 变更 + 计划禁令 |
| `special_cases_report.md` | 机械重扫 + 计划禁令 |

---

## 5. B15 + B16 汇总

### 5.1 B15（9 个 source/domain 对象）

| 指标 | 数量 |
|---|---|
| 不可变对象 | 9 |
| @Getter | 9 |
| 删除 getter | 117 |
| @Data/@Setter/@Builder | **0** |
| 敏感字段标注 | 2（secretCiphertext + secretMask） |
| 大文本/路径标注 | 5（configJson/metadataJson/evidenceJson ×4 处） |
| 双构造器保留 | 1（SourceSyncRunDetail） |
| 最大 DTO | SourceSyncRunDetail（42 字段） |

### 5.2 B16（4 个 llm/domain 对象）

| 指标 | 数量 |
|---|---|
| 不可变对象 | 4 |
| @Getter | 4 |
| 删除 getter | 63 |
| @Data/@Setter/@Builder | **0** |
| 敏感字段标注 | 2（apiKeyCiphertext + apiKeyMask） |
| 常量保留 | 2（MODEL_KIND_CHAT + MODEL_KIND_EMBEDDING） |
| 快照语义标注 | ExecutionLlmSnapshot（审计/成本/回放） |

### 5.3 B15 + B16 合计

| 指标 | 数量 |
|---|---|
| 类数 | 13 |
| @Getter | 13 |
| 删除 getter | **180**（117 + 63） |
| @Data | **0** |
| 敏感字段安全标注 | 4（2 ×2） |

### 5.4 编译验证

| 批次 | mvn compile |
|---|---|
| B15 | BUILD SUCCESS |
| B16 | BUILD SUCCESS |

---

## 6. 是否可以进入 B17

**可以。** 所有核查项通过：

- [x] 13 个文件与指定清单完全匹配
- [x] 0 个 @Data/@Setter/@Builder
- [x] 13 个 @Getter，180 个 getter 删除
- [x] SourceCredential secretCiphertext/secretMask 安全标注
- [x] LlmProviderConnection apiKeyCiphertext/apiKeyMask 安全标注
- [x] SourceSyncRunDetail 双构造器保留
- [x] MODEL_KIND_CHAT/EMBEDDING 常量保留
- [x] source service/controller + llm service/config + B17 均未修改
- [x] 台账 B15/B16→已完成，下一步 B17，"当前下一步"已修正
- [x] 2 个 out-of-scope 文件已排除
- [x] mvn compile PASS

### 提交建议

当前累积 B0-B16 共 **180 个类**未提交。B17 涉及 query/domain 的 `@Data` 谨慎治理（是剩余批次中风险较高的），建议在进入 B17 之前先提交固化 B0-B16 进度。

---

## 附录：全量治理进度（截至 B16）

| 阶段 | 批次 | 类数 | 处置方式 | 状态 |
|---|---|---|---|---|
| 试点 | B0 | 3 | @Getter + Javadoc | 已提交 |
| API 边界 DTO | B0.5-B10 | 95 | @Getter/@Setter + Javadoc | 已完成 |
| Controller 内部 DTO | B11 | 29 | @Data 降级 + @ToString.Exclude | 已完成 |
| Config + State | B12 | 16 | Javadoc / @Getter | 已完成 |
| Compiler Domain + AST | B13 | 14 | @Getter + @Data 降级 | 已完成 |
| DocumentParse Domain | B14 | 10 | @Getter | 已完成 |
| Source Domain | B15 | 9 | @Getter + 安全标注 | 已完成 |
| LLM Domain | B16 | 4 | @Getter + 安全标注 | 已完成 |
| **累计** | | **180** | | **177 未提交** |
| Query Domain/Evidence | B17 | ~23 | @Data 谨慎治理 | 待开始 |
| DeepResearch/Graph | B18 | ~17 | 待定 | 待开始 |
| Governance Domain | B19 | ~5 | 待定 | 待开始 |
| 全局复扫 | B20 | — | — | 待开始 |
