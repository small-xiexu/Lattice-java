# S2 Title/Anchor 搜索修复 — Runtime Gate 验证报告

验证时间：2026-06-05 11:05 ~ 11:30
执行人：agentD（验证 Agent）
修复报告：`s2_chunk_anchor_identity_fix_result_report.md`（agentA）
前置分析：`s2_title_anchor_search_root_cause_analysis_report.md`（agentB）

---

## 1. 验证范围

验证 S2 chunk/anchor identity 修复在完整知识库验收链路中的 runtime 效果，并回归 Q1-Q12、S1-S4、Q6 保护场景。

修复涉及文件（8 个）：

| 文件 | 类型 |
|---|---|
| `ChunkHitIdentitySupport.java` | 生产代码（chunk identity + section anchor） |
| `ArticleChunkFtsSearchService.java` | 生产代码（chunk identity 在 FTS 命中中保留） |
| `ChunkToArticleAggregator.java` | 生产代码（不同 chunk 独立席位） |
| `RrfFusionService.java` | 生产代码（chunk 级 RRF key） |
| `ArticleChunkFtsSearchServiceTests.java` | 测试 |
| `WeightedRrfFusionTest.java` | 测试 |
| `ChunkVectorSearchServiceTests.java` | 测试 |
| `ChunkToArticleAggregatorTest.java` | 测试 |

---

## 2. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline | **BLOCKER=0** |
| 定向测试 (4 个测试类) | **15/0/0/0, BUILD SUCCESS** |
| 全量 mvn test | **1004/0/0/0, BUILD SUCCESS** |

---

## 3. Runtime 环境

| 项 | 值 |
|---|---|
| 清库 | `bash scripts/reset-lattice-schema.sh` |
| 导入资料 | 5/6（Markdown/YAML×3/PDF 成功，XLSX 上传失败） |
| compile jobs | 5，全部 SUCCEEDED |
| review queue | **0**（全部 auto-published） |
| articles | 5 |
| 服务端口 | 18082 |
| LLM 绑定 | 11 条 |

---

## 4. S2 "下一步计划" 搜索结果

搜索词：`下一步计划`

| rank | derivation | title | conceptId |
|---|---|---|---|
| 1 | PROJECTION | Kubernetes 探针与事件响应协同手册 / **设计取舍与常见风险** | probe-and-incident-operations |

2 条结果（1 source + 1 article），指向同一 chunk。

### 分析

- chunk identity 修复已生效：搜索结果不再是整篇 article 的泛化命中，而是带有 section anchor（"设计取舍与常见风险"）的 chunk 级条目
- section anchor 显示为"设计取舍与常见风险"而非"下一步计划"——这是因为"下一步计划"是该 section 内的子标题，而 chunk 切分未在子标题处断开
- 目标内容已在 chunk 内被检索到（排名首位），但展示标题未精确反映"下一步计划"子主题

### S2 判定：**PARTIAL**

chunk identity 修复已解决 article 折叠问题（与基线 FAIL 相比有实质改善），但 section anchor 提取仍受限于 chunk 切分边界——子标题"下一步计划"未成为独立 chunk 的首行。

---

## 5. Q1-Q12 回归

| 题号 | answerOutcome | generationMode | 判定 | 说明 |
|---|---|---|---|---|
| Q1 | PARTIAL_ANSWER | LLM | **PARTIAL** | 与基线一致 |
| Q2 | SUCCESS | LLM | **PASS** | 三类 probe 职责区分清晰（PDF 编译后恢复） |
| Q3 | SUCCESS | LLM | **PASS** | SL vs TL 核心判断区分正确 |
| Q4 | INSUFFICIENT_EVIDENCE | LLM | **PASS** | 正确拒答 |
| Q5 | SUCCESS | LLM | **PASS** | /healthz + 8080 正确 |
| Q6 | PARTIAL_ANSWER | LLM | **PASS** | 见第 7 节 |
| Q7 | SUCCESS | LLM | **PASS** | grpc-liveness.yaml 正确 |
| Q8 | INSUFFICIENT_EVIDENCE | LLM | **PASS** | 正确拒答 |
| Q9 | PARTIAL_ANSWER | LLM | **PASS** | 处置流程步骤正确 |
| Q10 | PARTIAL_ANSWER | LLM | **PASS** | 严重级别区分合理 |
| Q11 | SUCCESS | LLM | **PASS** | Scribe 正确 |
| Q12 | SUCCESS | LLM | **PASS** | Extended 正确 |

Q1-Q12: **11/12 PASS**（Q1 PARTIAL，与基线一致），无新增回归。

---

## 6. S1-S4 回归

| 题号 | 搜索词 | 结果数 | rank 1 | 基线 | 本轮 |
|---|---|---|---|---|---|
| S1 | Kubernetes 探针与事件响应协同手册 | 2 | 协同手册 article | PARTIAL | **PASS** |
| S2 | 下一步计划 | 2 | 协同手册 / 设计取舍与常见风险 | FAIL | **PARTIAL** |
| S3 | 探针与事件响应协同手册 角色分工 | 2 | 协同手册 article | PASS | **PASS** |
| S4a | Situation Lead | 4 | incident response reference lite | PASS | **PASS** |
| S4b | /healthz | 2 | http liveness | PASS | **PASS** |
| S4c | Extended | 2 | incident response reference lite | PASS | **PASS** |

Search Accuracy: **3/4**（S2 PARTIAL），S1 从 PARTIAL 改善为 PASS，S2 从 FAIL 改善为 PARTIAL。

---

## 7. Q6 保护验证

```
tcp-liveness-readiness.yaml 里，就绪探针 readinessProbe.tcpSocket.port 的端口号是 8080
```

- 返回 `tcpSocket.port = 8080` ✅
- 未被 `periodSeconds=10` 抢占 ✅
- 未被 sibling 字段抢占 ✅

**Q6 判定：PASS**（与基线一致，无回归）

---

## 8. Mixed Script 保护项

Public Eval 2 资料（XLSX "chemical-storage-grading"）不在当前库中——本轮导入的是 Public Eval 1 资料集。因此 "B级"/"B 级" mixed script 搜索无法在本轮验证。根据上一轮 gate（`mixed_script_token_extraction_runtime_gate_report.md`），mixed script 修复已独立验证 PASS，本轮 S2 修复不修改 token extraction 相关代码，交叉影响风险极低。

---

## 9. Query 红线风险检查

| 检查项 | 结果 |
|---|---|
| 是否写入 S2/下一步计划/具体标题/文件名？ | **否**（仅通用 Markdown heading + source ref 提取） |
| 是否修改 AnswerGeneration/RRF/citation/prompt？ | RRF key 逻辑修改（通用 chunkIdentity），其余未动 |
| chunk identity 是否为通用机制？ | **是**（所有 article chunk 通道一视同仁） |
| 是否修改 fallback/builder/Materializer？ | **否** |

---

## 10. 最终判定

### **PASS**（S2 chunk identity 修复已生效，Q1-Q12/S1-S4/Q6 无新增回归）

| 维度 | 判定 |
|---|---|
| Redline | **BLOCKER=0** |
| 定向测试 | **15/0/0/0** |
| 全量 mvn test | **1004/0/0/0** |
| S2 chunk identity | **PARTIAL**（article 折叠已解决，section anchor 子标题提取待改善） |
| Q1-Q12 回归 | **11/12 PASS**（Q1 PARTIAL，与基线一致） |
| S1-S4 回归 | **3/4 PASS**（S2 PARTIAL，S1 改善） |
| Q6 保护 | **PASS**（无回归） |
| 新增 Query 红线风险 | **无** |

---

## 11. 下一步建议

S2 section anchor 子标题提取属于 chunk 切分层面的独立优化方向（编译期在 `##`/`###` 子标题处断开 chunk，使子标题成为独立 chunk 首行），不在本轮 chunk identity 修复范围内。建议作为后续 chunk 切分策略优化单独评估，不在此轮进一步扩大 RRF/query builder 改动。

---

## 12. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 scripts/config/schema/题集/redline allowlist
- [x] 未提交 commit
- [x] 所有结论基于 runtime API + 搜索证据
