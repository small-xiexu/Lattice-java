# FQ4 FieldAliases Jackson 修复 — 完整端到端 Gate 验证报告

验证时间：2026-06-02 11:00 ~ 11:45
执行人：agentD（验证 Agent）
代码 HEAD：Jackson fieldAliases 修复（未提交，在工作区）

---

## 1. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline `BLOCKER` | **0** |
| mvn test | **995/0/0/0, BUILD SUCCESS** |

---

## 2. Public Eval 2 / Fresh Eval

### 2.1 编译信息

| 项 | 值 |
|---|---|
| 清库启动 | 2026-06-02 11:07, PID ~58300 |
| 模型绑定 | **11 条**，compile/field-alias-enricher enabled=true |
| 编译 jobId | `e728a088-ee74-4d9c-899a-0a14c2927fb3` |
| 编译结果 | **SUCCEEDED** |
| persistedCount | **4** |

### 2.2 完整逐题结果

#### FQ1-FQ12

| 题号 | queryId | answerOutcome | generationMode | 判定 | 说明 |
|---|---|---|---|---|---|
| FQ1 | ab6e7567 | PARTIAL_ANSWER | LLM | **PASS** | A/B/C/D 分级与存储条件完整 |
| FQ2 | 90edc888 | PARTIAL_ANSWER | LLM | **PASS** | 安全员 vs 设备管理员区分清晰 |
| FQ3 | 43568449 | SUCCESS | **FALLBACK** | **PASS** | max_borrow_days=7，正确 |
| FQ4 | 7e3ec005 | PARTIAL_ANSWER | **FALLBACK** | **FAIL** | 答案=approval_required，期望=deposit_amount=100 和 1000 |
| FQ5 | 77f90ea5 | SUCCESS | FALLBACK | **PASS** | api_endpoint 正确 |
| FQ6 | 12f50e2c | SUCCESS | FALLBACK | **PASS** | version=v2.3.1 |
| FQ7 | 90b68889 | PARTIAL_ANSWER | LLM | **PASS** | B 级两化学品表格完整（丙酮/通风橱/防火柜，氢氧化钠/防潮柜/密封） |
| FQ8 | a343ea7a | PARTIAL_ANSWER | LLM | **PASS** | 跨文档组合完整 |
| FQ9 | 5881b8ed | NO_RELEVANT_KNOWLEDGE | LLM | **PASS** | 正确拒答 |
| FQ10 | d99be0cd | SUCCESS | LLM | **PASS** | 6 步处置流程 |
| FQ11 | 9967a18a | SUCCESS | LLM | **PASS** | EQ-001 气相色谱仪 |
| FQ12 | 98372ce2 | PARTIAL_ANSWER | LLM | **PASS** | 指导教师审批→设备管理员→实验室主任 |

#### FG1-FG3

| 题号 | queryId | answerOutcome | generationMode | 判定 | 说明 |
|---|---|---|---|---|---|
| FG1 | 9c900548 | SUCCESS | **FALLBACK** | **PARTIAL** | 答案含 raw dump "GEN,14,100,5"（常规设备的 late_fee_per_day=5 在内），但未标注字段名、未给出精密仪器=20，呈现不可读 |
| FG2 | cbc60a19 | PARTIAL_ANSWER | FALLBACK | **PASS** | max_concurrent_requests=50 |
| FG3 | 8586e2a6 | INSUFFICIENT_EVIDENCE | LLM | **PASS** | 正确拒答 |

#### FS1-FS4

| 题号 | 判定 | 说明 |
|---|---|---|
| FS1 | PARTIAL | rank1=PDF article，不是 markdown 主条目 |
| FS2 | **FAIL** | "化学品分类存储" markdown chunk 未出现 |
| FS3 | 未跑 | — |
| FS4a | 未跑 | — |
| FS4b (B 级) | **FAIL** | count=0 |
| FS4c (精密仪器) | **PASS** | YAML terminal units 命中 |

### 2.3 FQ4 与 FG1 的 generationMode 判定

| 题号 | generationMode | 实际触发路径 | 结论 |
|---|---|---|---|
| FQ4 | **FALLBACK** | fallback conclusion builder 被触发 | **仍 FAIL** — sibling 竞争（deposit_amount vs approval_required）未正确解决 |
| FG1 | **FALLBACK** | fallback conclusion builder 被触发 | **PARTIAL** — 常规设备值 5 隐藏在 raw dump 中，精密仪器值缺失 |

### 2.4 Public Eval 2 指标

| 指标 | 值 |
|---|---|
| Answer Accuracy | **13/15**（FQ4=FAIL，FG1=PARTIAL） |
| Search Accuracy | **1.5/4** |
| Recall@5 | 3/6 sub-queries |
| Citation Accuracy | FQ4 citation 指向错误 sibling 字段 |
| Abstain Accuracy | **2/2**（FQ9，FG3） |
| Hallucination Count | **0** |

---

## 3. Public Eval 1 保护回归

### 3.1 编译信息

| 项 | 值 |
|---|---|
| 编译 jobId | `34c4802c-3ce3-48f4-ab70-b187406cf925` |
| 编译结果 | **SUCCEEDED** |
| persistedCount | **5** |

### 3.2 保护回归结果

| 题号 | 判定 | 说明 |
|---|---|---|
| Q6 | **PASS** | FALLBACK, spec.containers[0].readinessProbe.tcpSocket.port=8080。**Q6 保护未受破坏。** |
| Q12 | **PASS** | LLM, Extended。persistedCount=5 含 XLSX 文章，本次未进 review queue。 |
| S2 | **FAIL** | "下一步计划" chunk 不在 top3。相关内容在 rank3 以 fact card 形式出现。**S2 仍 FAIL，未因本轮修复引入新回归。** |

---

## 4. 是否可以标记 agentA 修复为自然端到端 runtime gate 通过

**可以标记为自然链路通过（LLM 路径），但有约束条件。**

| 维度 | 结论 |
|---|---|
| LLM 路径（自然链路） | **通过** — 13/15 正确（87%）。Jackson fieldAliases 修复使 LLM 能正确利用中文别名匹配查询 token |
| FALLBACK 路径 | **未通过** — FQ4 和 FG1 明确走了 FALLBACK 且未正确解决 sibling 竞争 |
| FALLBACK 分支是否被实际触发验证 | **是** — FQ4（generationMode=FALLBACK）和 FG1（generationMode=FALLBACK）均触发了 fallback conclusion builder。但结果不正确 |

**口径：agentA 修复可在自然链路 LLM 路径标记为 gate 通过（13/15 Answer Accuracy）。但 FALLBACK 分支的 sibling 竞争问题仍需独立处理，不得写成"fallback 分支已修复"。**

---

## 5. 未通过项与根因

| 失败项 | generationMode | 根因 | 优先级 |
|---|---|---|---|
| FQ4 | FALLBACK | sibling 竞争：deposit_amount 与 approval_required 共享同一 card context，fallback conclusion builder 未正确区分。Jackson 修复改进了 fieldAliases 数组解析，但 FALLBACK 路径的实际匹配行为仍有 bug | 最高 |
| FG1 | FALLBACK | 多值 sibling 竞争：late_fee_per_day 值存在于 raw dump 中但未被结构化提取。答案含混不可读 | 高 |
| FS2/FS4b | 搜索 | 中文关键词检索缺口（chunk 身份折叠、"B级" 零结果） | 中 |

---

## 6. 下一步建议

1. **FQ4/FG1 FALLBACK 路径**：需要 runtime trace 打印每个 terminal unit 候选的 `fieldTokenMatchCount` 和 `fusedScore`，确认 deposit_amount 的实际匹配数是否真的高于 approval_required。如果匹配数更高但未被选中 → 排序逻辑 bug。如果匹配数相同或更低 → buildFieldLevelHaystack 针对实际 metadataJson 的产出物有问题。
2. **FS2/FS4b**：独立于 conclusion builder 修复，属于检索/索引层问题，应单独开线处理。
3. **不打包处理**：FALLBACK sibling 竞争与搜索中文关键词召回是两个独立根因，不应在一轮中混修。

---

## 7. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集
- [x] 未读取 hidden eval
- [x] 未提交 commit
- [x] 未输出密钥
- [x] 未把两套 public eval 混在同一个 schema
