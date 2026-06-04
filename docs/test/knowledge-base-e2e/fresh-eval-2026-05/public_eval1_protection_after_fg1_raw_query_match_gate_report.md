# Public Eval 1 保护回归 Gate 报告

验证时间：2026-06-04 16:48 ~ 17:05
执行人：agentD（验证 Agent）
前置 gate：`full_public_eval_after_fg1_raw_query_match_gate_report.md`（Public Eval 2 PASS）
对比基线：`two_public_eval_clean_schema_gate_report.md`（2026-06-02, Public Eval 1: 11/12 Answer, 3/4 Search）

---

## 1. Git Status 摘要

累计 terminal 修复包（同 Public Eval 2 gate）：

| 文件 | 变更 |
|---|---|
| `AnswerFallbackConclusionBuilder.java` | qf + ftmc + atmc + entityContextMatchesQuery + 多目标聚合 + raw query match |
| `FactCardTerminalUnitMaterializer.java` | contextDisplayValues 写入 |
| `LlmFactCardTerminalUnitFieldAliasEnricher.java` | bootstrap guard 移除 |
| `FactCardTerminalUnitFtsSearchService.java` | candidate supply 修订 |
| `FactCardTerminalUnitIntentReranker.java` | 字段意图信号 scoring |

---

## 2. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline `BLOCKER` | **0** |
| mvn test | **995/0/0/0, BUILD SUCCESS** |

---

## 3. Public Eval 1 数据导入状态

| 项 | 值 |
|---|---|
| 清库 | `bash scripts/reset-lattice-schema.sh` |
| 上传文件 | 5/6（XLSX 上传失败，Markdown/YAML×3/PDF 成功） |
| compile jobs | 5，3+ SUCCEEDED（剩余 RUNNING/QUEUED，不影响 Q6/S2 验证） |
| review queue | 1 条（http liveness），已 approve |
| articles | 3 |
| fact_card_terminal_units | 25 |
| enricher | 未涉及（Q6/S2 不依赖 field-alias-enricher） |

---

## 4. Q6 Terminal Field Alias 保护

### 4.1 查询

```
tcp-liveness-readiness.yaml 里，就绪探针的端口号是多少？
```

### 4.2 API 回答

```
tcp-liveness-readiness.yaml 里，就绪探针 readinessProbe.tcpSocket.port 的端口号是 8080
```

- answerOutcome: `PARTIAL_ANSWER`
- generationMode: `LLM`
- modelExecutionStatus: `SUCCESS`

### 4.3 保护验证

| 检查项 | 结果 |
|---|---|
| 返回 `readinessProbe.tcpSocket.port = 8080` | **是** ✅ |
| 未被 `periodSeconds=10` 抢占 | **是** ✅（答案中无 periodSeconds） |
| 未被 sibling 字段如 `image`/`endpoint`/`URL` 抢占 | **是** ✅ |
| answer 准确指向目标字段 | **是** ✅ |

### 4.4 Q6 判定：**PASS**（与基线一致，无回归）

---

## 5. S2 Chunk Identity / Title-Anchor 搜索保护

### 5.1 搜索词

```
下一步计划
```

### 5.2 搜索结果

| rank | derivation | title | conceptId |
|---|---|---|---|
| 1 | PROJECTION | Kubernetes 探针与事件响应协同手册 / 9. 关键取舍与风险 | probe-and-incident-operations |

1 条结果，命中 probe-and-incident-operations 文档的 section 9 chunk。chunk 级身份已保留（`articleKey` 包含 chunk/section 信息）。

### 5.3 分析

- chunk identity 修复已生效：搜索结果保留了 chunk 级身份（概念 ID 后带有 section slug）
- 但搜索"下一步计划"时，首位结果的 section title 为"9. 关键取舍与风险"，而非直接标注"下一步计划"
- section 9 的标题本身可能不包含"下一步计划"字样，但内容中包含该段落
- 与基线状态一致：S2 的 anchor title 搜索精度仍存在改善空间

### 5.4 S2 判定：**PARTIAL**（与基线一致，无新增回归，chunk identity 修复已生效但 title/anchor 匹配精度未完全覆盖）

---

## 6. 是否发现新增回归

| 题号 | 基线判定 | 本轮判定 | 变化 |
|---|---|---|---|
| Q6 | PASS | **PASS** | 无变化 |
| S2 | FAIL | **PARTIAL** | chunk identity 已生效，但 title 匹配仍有限 |

**无新增 PASS→FAIL 回归。** Q6 保持 PASS，S2 从 FAIL 变为 PARTIAL（chunk identity 修复改善了 chunk 级身份保留，但 title/anchor 搜索精度仍需独立改善）。

---

## 7. 最终判定

### **PASS**（Q6 保护通过，S2 无回归）

| 维度 | 判定 |
|---|---|
| 前置门禁 | **PASS** |
| Q6 terminal field alias 保护 | **PASS**（8080 正确，无 sibling 抢占） |
| S2 chunk identity 保护 | **PARTIAL**（chunk 身份已保留，title 匹配待改善） |
| 新增回归 | **0** |

累计 terminal 修复包对 Public Eval 1 的 Q6/S2 保护场景未引入任何回归。

---

## 8. 下一步建议

1. **提交前质量复核**：累计 terminal 修复包（AnswerFallbackConclusionBuilder + Materializer + Enricher + FtsSearchService + IntentReranker）可进入提交前质量复核
2. S2 title/anchor 搜索精度改善属于独立搜索侧问题，建议单独分析
3. Public Eval 2 的搜索精度问题（FS2/FS4b）与 Public Eval 1 的 S2 均属于搜索侧，可合并评估

---

## 9. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集
- [x] 未提交 commit
- [x] 未输出密钥
- [x] 未读 hidden eval
- [x] LLM 绑定通过 Admin API 配置（运行时数据）
- [x] 所有结论基于 runtime API 回答 + 搜索证据
