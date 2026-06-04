# FQ4 FieldAliases JSON 数组消费修复 — 端到端验证报告

验证时间：2026-06-02
执行人：agentD（验证 Agent）
修复报告：`fq4_field_alias_json_array_consumption_fix_result_report.md`
代码 HEAD：`741647f test(eval): record post-cleanup public eval gate`

---

## 1. 前置门禁

| 门禁 | 结果 |
|---|---|
| 无残留测试进程 | 通过 |
| PostgreSQL (vector_db) | healthy |
| Redis | healthy |
| Redline `BLOCKER` | **0** |
| mvn test | **995/0/0/0, BUILD SUCCESS** |

---

## 2. 修复代码确认

`AnswerFallbackConclusionBuilder.buildFieldLevelHaystack()` 已从字符串切片 `appendJsonField` 改为 Jackson `JsonNode` 结构化解析。确认如下代码已存在于工作区：

```java
// 新实现 (line 397-417)
JsonNode node = JsonMappers.defaultMapper().readTree(metadataJson);
JsonNode aliases = node.path("fieldAliases");
if (aliases.isArray()) {
    for (JsonNode alias : aliases) {
        sb.append(' ').append(alias.asText(""));
    }
}
```

旧方法 `extractJsonStringValue` 仍保留但仅用于 `displayText` 提取（单字符串值，不受影响）。

**代码修改已确认存在，但未提交。**

---

## 3. 验证步骤 A：Public Eval 2 / Fresh Eval

### 3.1 清库与编译

| 项 | 值 |
|---|---|
| 清库时间 | 2026-06-02 08:10 |
| 编译 jobId | `f7625be8-8fd2-465b-8bd3-593135059d8f` |
| 编译结果 | **SUCCEEDED** |
| persistedCount | **5** |

### 3.2 模型绑定

11 条绑定全部 enabled=true, compile/field-alias-enricher 存在 (id=4).

### 3.3 Terminal Unit 核验

deposit_amount terminal units 存在且含中文别名：

| id | parent_path | 值 | 中文别名关键项 |
|---|---|---|---|
| 11 | equipment_types[0] (常规设备) | 100 | 押金金额, 保证金金额, 设备押金 |
| 18 | equipment_types[1] (精密仪器) | 500 | 押金金额, 保证金金额, 押金 |
| 25 | equipment_types[2] (大型设备) | 1000 | 押金金额, 保证金金额, 借用押金, 押金数额 |

late_fee_per_day terminal units 存在且含中文别名（每日逾期费、逾期日费等）。

### 3.4 FQ4 核心验证

| 字段 | 值 |
|---|---|
| queryId | `526e4c65` |
| answerOutcome | PARTIAL_ANSWER |
| generationMode | **FALLBACK** |
| modelExecutionStatus | DEGRADED |
| 答案 | `equipment_types[0].approval_required = 设备管理员` |
| 期望 | `equipment_types[0].deposit_amount = 100`, `equipment_types[2].deposit_amount = 1000` |
| 判定 | **FAIL** |

**FQ4 FALLBACK 路径仍选中 approval_required 而非 deposit_amount。修复未生效。**

### 3.5 FG1 回归

| 字段 | 上轮（LLM路径） | 本轮（FALLBACK路径） |
|---|---|---|
| answerOutcome | SUCCESS | SUCCESS |
| generationMode | **LLM** | **FALLBACK** |
| 答案 | 精密仪器=20, 常规设备=5 | API endpoint = https://lab-equip.campus.edu/api/v2/borrow |
| 判定 | PASS | **FAIL（回归）** |

FG1 从上轮 LLM 路径的 PASS 倒退为 FALLBACK 路径的 FAIL。上轮"FG1 PASS"不能证明 fallback conclusion builder 修复生效——它只是 LLM 语义匹配的结果。

### 3.6 Public Eval 2 完整结果

#### FQ1-FQ12

| 题号 | 判定 | generationMode | 说明 |
|---|---|---|---|
| FQ1 | **PASS** | LLM | A/B/C/D分级覆盖 |
| FQ2 | **PASS** | LLM | 安全员vs设备管理员区分 |
| FQ3 | **PASS** | FALLBACK | max_borrow_days=7 |
| FQ4 | **FAIL** | FALLBACK | approval_required取代deposit_amount |
| FQ5 | **PASS** | FALLBACK | api_endpoint正确 |
| FQ6 | **PASS** | FALLBACK | version=v2.3.1 |
| FQ7 | **PASS** | LLM | 丙酮通风橱/防火柜, 氢氧化钠防潮柜/密封, 均设备管理员 |
| FQ8 | **PASS** | LLM | 跨文档组合完整 |
| FQ9 | **PASS** | LLM | 正确拒答 |
| FQ10 | **PASS** | LLM | 6步处置流程 |
| FQ11 | **PASS** | LLM | EQ-001气相色谱仪 |
| FQ12 | **PASS** | LLM | 3审批阶段正确 |

#### FG1-FG3

| 题号 | 判定 | generationMode | 说明 |
|---|---|---|---|
| FG1 | **FAIL（回归）** | FALLBACK | api_endpoint取代late_fee_per_day |
| FG2 | **PASS** | FALLBACK | max_concurrent_requests=50 |
| FG3 | **PASS** | LLM | 正确拒答 |

#### FS1-FS4

| 题号 | 判定 | 说明 |
|---|---|---|
| FS1 | **PASS** | "校园实验室安全管理手册" rank1 |
| FS2 | **FAIL** | 无"化学品分类存储"chunk条目 |
| FS4b | **FAIL** | "B级" count=0 |
| FS4c | **PASS** | "精密仪器"命中YAML |

### 3.7 Public Eval 2 指标

| 指标 | 本轮 | 上轮（修复前） | 变化 |
|---|---|---|---|
| Answer Accuracy | **13/15** | 11/15 | +2 (但FG1从上轮PASS变本轮FAIL，FQ4仍FAIL) |
| FG1 | **FAIL** | 上上轮PASS | 回归（LLM→FALLBACK路径切换导致） |
| FQ4 | **FAIL** | FAIL | 持平（修复未生效） |
| Search Accuracy | **2/4** | 1.5/4 | +0.5 (FS1改善) |
| Abstain Accuracy | **2/2** | 2/2 | 持平 |
| Hallucination Count | **0** | 0 | 持平 |

---

## 4. 验证步骤 B：Public Eval 1 保护回归

### 4.1 编译

| 项 | 值 |
|---|---|
| jobId | `32b288cc-dc2b-42da-94d8-9d7d0ec1c349` |
| 结果 | SUCCEEDED |
| persistedCount | **5** |

### 4.2 保护回归结果

| 题号 | 判定 | 说明 |
|---|---|---|
| Q6 | **PASS** | spec.containers[0].readinessProbe.tcpSocket.port=8080, FALLBACK路径, citation正确 |
| Q12 | **PASS** | Extended, 本轮XLSX文章已persist（articles=5含incident checklist） |
| S2 | **FAIL** | "下一步计划"chunk仍不在top5中以anchor身份展示，相关内容仅在rank4的fact card中出现 |

Q6 保护未受破坏。Q12 恢复（上轮因XLSX文章在review queue中失败）。S2 仍 FAIL。

---

## 5. 是否可以标记 agentA 修复为 runtime 验证通过

**不可以。**

证据：
1. FQ4 FALLBACK 路径仍选中 `approval_required` 而非 `deposit_amount`——修复的直接目标未达成。
2. FG1 从上轮 LLM 路径的 PASS 倒退为 FALLBACK 路径的 FAIL——暴露上轮 FG1 的"通过"并非来自 fallback 修复，而是 LLM 语义匹配。
3. 修复代码（Jackson JsonNode 数组遍历）在源码中已存在且逻辑正确，但运行时行为未改变。

---

## 6. 唯一根因

**FALLBACK 路径下 `buildFieldLevelHaystack` 中的 Jackson JSON 解析未被有效执行或 `metadataJson` 在 QueryArticleHit 上不可用。**

具体体现：
- 源码中 `buildFieldLevelHaystack` 已使用 Jackson `readTree` + `isArray` + `for` 遍历（逻辑正确）
- 但 FQ4 和 FG1 的 FALLBACK 路径仍产出与修复前完全相同的结果
- 如果 `metadataJson` 返回 null 或空字符串，`buildFieldLevelHaystack` 在第 399-401 行返回 `""`，导致 `countFieldLevelTokenMatches` 对所有候选返回 0，最终回退到 fused order tiebreaker（与旧行为完全一致）
- 如果 `metadataJson` 包含完整 JSON，Jackson 解析应能使 deposit_amount 的 `fieldTokenMatchCount > 0` 而 approval_required 的 = 0

**需要 agentA 确认：FALLBACK 路径中 QueryArticleHit.getMetadataJson() 是否实际被填充。** 如果未被填充，修复应在数据填充层（terminal unit hit 构建时回填 metadataJson），而非调整解析逻辑。

---

## 7. 下一步建议

1. **最高优先级**：agentA 验证 FALLBACK 路径中 QueryArticleHit 的 `metadataJson` 字段是否被填充。如果未填充，需在 terminal unit hit 构建层（检索→QueryArticleHit 转换）回填 metadataJson。
2. **验证方式**：可加临时的 log 在 `buildFieldLevelHaystack` 入口打印 metadataJson 内容（或长度），确认是否为空。
3. **不改排序算法**：当前的 Jackson + fieldTokenMatchCount + fused order tiebreaker 逻辑已正确，只是输入数据（metadataJson）可能为空。
4. **不入 query fallback 主链**：如果 metadataJson 为空是根本问题，修复应在数据填充层，不向 fallback conclusion builder 追加新逻辑。

---

## 8. 明确声明

- [x] 未修改生产代码（src/main/java）
- [x] 未修改测试代码（src/test/java）
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集、expected answer、eval runner
- [x] 未读取 hidden eval
- [x] 未提交 commit
- [x] 未输出 API key/token/password/baseUrl
- [x] 未把两套 public eval 资料混在同一个 schema 状态下验证
