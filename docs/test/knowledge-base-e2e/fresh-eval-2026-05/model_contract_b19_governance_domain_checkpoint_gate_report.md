# B19 Governance Domain Checkpoint 门禁核查报告

核查时间：2026-06-01
核查人：agentD（只读门禁审计）
范围：B19 — governance/domain（5 个类）
状态：**PASS — 所有核查项通过，无阻塞问题**

---

## 1. Git 变更分类

### 1.1 按文件类别统计

| 类别 | 数量 | 状态 |
|---|---|---|
| B17 历史变更（已独立 gate PASS） | 22 | query/domain(9) + evidence/domain(13) |
| B18 历史变更（已独立 gate PASS） | 14 | deepresearch/domain(10) + EvidenceLedger + graph state(3) |
| B19 生产代码（governance/domain） | 5 | 全部不可变 @Getter + Javadoc |
| **B19 生产代码合计** | **5** | — |
| 计划文档（治理台账） | 1 | B19→已完成，B20 待开始 |
| 模型绑定配置参考 | 1 | **已知 out-of-scope dirty（不得纳入）** |
| special_cases_report | 1 | **已知 out-of-scope dirty（不得纳入）** |
| B17/B18/B19 报告文件（untracked） | 10 | 可纳入 |

### 1.2 B19 生产代码（5 个文件）

| 文件 | 字段 | diff | 变更 |
|---|---|---|---|
| `CrossValidatePayload.java` | 2 | +45/-? | @Getter, 2 getter 删除, @JsonCreator + unsupported() 保留 |
| `PropagationCheckPayload.java` | 2 | +45/-? | @Getter, 2 getter 删除, @JsonCreator + unaffected() 保留 |
| `LifecycleItem.java` | 9 | +143/-? | @Getter, 9 getter 删除, 双构造器保留 |
| `LifecycleTransitionResult.java` | 8 | +123/-? | @Getter, 8 getter 删除, 双构造器保留 |
| `LifecycleReport.java` | 6 | +91/-? | @Getter, 6 getter 删除, items 可变 List 风险不修复 |

---

## 2. 核查项逐项结果

### 2.1 Lombok 核查

| 注解 | 预期 | 实际 | 结果 |
|---|---|---|---|
| @Data | 0 | **0**（无输出） | ✅ |
| @Setter | 0 | **0**（无输出） | ✅ |
| @Builder | 0 | **0**（无输出） | ✅ |
| @Getter | 5/5 | **5/5**（CrossValidatePayload, LifecycleItem, LifecycleReport, LifecycleTransitionResult, PropagationCheckPayload） | ✅ |

**结果：PASS。** B19 是全部批次中最干净的——0 个 @Data，0 个待降级项，仅加 @Getter。

### 2.2 Getter 删除核查

```
rg -n "public .* get|public boolean is" governance/domain/
→ (无输出)
```

**结果：PASS。** 27 个手写 getter 全部删除，无残留简单 getter。

### 2.3 @JsonCreator 和 static factory 保留

#### CrossValidatePayload

| 检查项 | 结果 |
|---|---|
| `@JsonCreator`（第 32 行） | ✅ |
| `@JsonProperty("supported")`（第 34 行） | ✅ |
| `@JsonProperty("evidence")`（第 35 行） | ✅ |
| `Boolean→boolean` null-coalescing | ✅ |
| `evidence.trim-or-empty` | ✅ |
| `unsupported()` static factory（第 42 行） | ✅ |

#### PropagationCheckPayload

| 检查项 | 结果 |
|---|---|
| `@JsonCreator`（第 32 行） | ✅ |
| `@JsonProperty("affected")`（第 34 行） | ✅ |
| `@JsonProperty("reason")`（第 35 行） | ✅ |
| `Boolean→boolean` null-coalescing | ✅ |
| `reason.trim-or-empty` | ✅ |
| `unaffected()` static factory（第 42 行） | ✅ |

**结果：PASS。**

### 2.4 双构造器 + articleKey fallback 保留

#### LifecycleItem

| 检查项 | 结果 |
|---|---|
| 9 参数构造器 | ✅（第 40 行） |
| 7 参数构造器 | ✅（第 54 行） |
| articleKey=conceptId fallback | ✅（第 59 行：`this(null, conceptId, conceptId, ...)`） |

#### LifecycleTransitionResult

| 检查项 | 结果 |
|---|---|
| 8 参数构造器 | ✅（第 34 行） |
| 6 参数构造器 | ✅（第 47 行） |
| articleKey=conceptId fallback | ✅（第 51 行：`this(null, conceptId, conceptId, ...)`） |

**结果：PASS。** 双构造器委托逻辑完整保留。

### 2.5 LifecycleReport.items 未改动

| 检查项 | 结果 |
|---|---|
| items 无 `List.copyOf`/`Collections.unmodifiableList` | ✅ |
| items 无防御性拷贝 | ✅ |
| Javadoc 标注已知风险 | ✅（"可变 List 风险已知，本轮不修复"） |

**结果：PASS。** items 保持原始 List 引用，未添加防御性拷贝（符合计划"已知风险不修复"策略）。

### 2.6 字段 Javadoc

所有 5 个类的全部字段已有字段级 Javadoc：

| 类 | 字段数 | Javadoc |
|---|---|---|
| `CrossValidatePayload` | 2 | supported/evidence ✅ |
| `PropagationCheckPayload` | 2 | affected/reason ✅ |
| `LifecycleItem` | 9 | articleKey/conceptId/lifecycle 等 ✅ |
| `LifecycleTransitionResult` | 8 | articleKey/conceptId/lifecycle/reason 等 ✅ |
| `LifecycleReport` | 6 | totalArticles/activeCount/deprecatedCount/archivedCount/otherCount/items ✅ |

### 2.7 编译验证

```
mvn compile → BUILD SUCCESS (6.287s)
```

**结果：PASS。**

### 2.8 越界检查

```
git diff --name-only -- governance/ | grep -v "governance/domain/"
→ (无输出)
```
governance service/controller/caller 均未修改。

```
git diff --name-only -- query/retrieval/ query/answer/
→ (无输出)
```
query 主链行为未修改。

**结果：PASS。** B19 实现严格限制在 governance/domain 5 个文件范围内。

### 2.9 计划台账核查

| 检查项 | 预期 | 实际 | 结果 |
|---|---|---|---|
| B19 状态 | 已完成 | "已完成" | ✅ |
| B19 汇总 | 5 类，@Getter, 27 getter 删除 | 台账第 91 行 | ✅ |
| 验证 | mvn compile PASS | 已确认 | ✅ |
| 下一步 | B20: 全局复扫 | "待开始" | ✅ |

**结果：PASS。**

### 2.10 Out-of-scope 文件确认

| 文件 | 状态 |
|---|---|
| `docs/模型绑定配置参考.md` | 仍为 dirty（不纳入） |
| `special_cases_report.md` | 仍为 dirty（不纳入） |
| B17 历史变更（22 个文件） | 已有独立 gate PASS，不阻塞 |
| B18 历史变更（14 个文件） | 已有独立 gate PASS，不阻塞 |

---

## 3. 可纳入本次 Checkpoint 的文件清单

### 3.1 B19 生产代码（5 个）

| # | 文件 |
|---|---|
| 1 | `src/main/java/com/xbk/lattice/governance/domain/CrossValidatePayload.java` |
| 2 | `src/main/java/com/xbk/lattice/governance/domain/PropagationCheckPayload.java` |
| 3 | `src/main/java/com/xbk/lattice/governance/domain/LifecycleItem.java` |
| 4 | `src/main/java/com/xbk/lattice/governance/domain/LifecycleTransitionResult.java` |
| 5 | `src/main/java/com/xbk/lattice/governance/domain/LifecycleReport.java` |

### 3.2 B19 批次报告（2 个 untracked）

| 文件名 | 类型 |
|---|---|
| `governance_domain_contract_analysis_report.md` | 边界审查 |
| `governance_domain_b19_contract_javadoc_lombok_fix_result_report.md` | B19 修复报告 |

---

## 4. 必须排除的文件

| 文件 | 原因 |
|---|---|
| `docs/模型绑定配置参考.md` | API key 变更 + 计划禁令 |
| `special_cases_report.md` | 机械重扫 + 计划禁令 |
| B17 生产代码（22 个文件） | 已在 B17 gate 独立 PASS |
| B18 生产代码（14 个文件） | 已在 B18 gate 独立 PASS |

---

## 5. B19 汇总

| 指标 | 数量 |
|---|---|
| 类数 | 5 |
| @Getter | 5 |
| 删除 getter | 27 |
| @Data/@Setter/@Builder | **0**（最干净批次） |
| @JsonCreator 保留 | 2（CrossValidatePayload + PropagationCheckPayload） |
| static factory 保留 | 2（unsupported + unaffected） |
| 双构造器保留 | 2（LifecycleItem + LifecycleTransitionResult） |
| articleKey=conceptId fallback 保留 | 2 处 |
| Boolean→boolean null-coalescing 保留 | 2 处 |
| 已知风险（不修复） | 1（LifecycleReport.items 可变 List） |
| mvn compile | BUILD SUCCESS |

---

## 6. 是否可以进入 B20

**可以。** 所有核查项通过：

- [x] 5 个文件与指定清单完全匹配
- [x] 0 个 @Data/@Setter/@Builder
- [x] 5 个 @Getter，27 个 getter 删除，0 残留
- [x] 2 个 @JsonCreator + @JsonProperty 保留
- [x] 2 个 static factory 保留（unsupported/unaffected）
- [x] 2 个双构造器 + articleKey fallback 保留
- [x] LifecycleReport.items 可变 List 未添加防御性拷贝（符合计划）
- [x] governance service/controller + query 主链均未修改
- [x] 台账 B19→已完成，下一步 B20
- [x] mvn compile BUILD SUCCESS
- [x] B17/B18 历史变更不阻塞

---

## 附录：全量治理进度（截至 B19）

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
| Query + Evidence Domain | B17 | 23 | @Getter + @Data 降级 | 已完成 |
| DeepResearch + Graph State | B18 | 14 | @Data 降级（含 EvidenceLedger 无 @Setter） | 已完成 |
| Governance Domain | B19 | 5 | @Getter（零 @Data 污染） | 已完成 |
| **累计** | | **222** | | **219 未提交** |
| 全局复扫 | B20 | — | — | 待开始 |
