# S2 Heading Boundary Chunking — Runtime Gate 验证报告

验证时间：2026-06-05 13:35 ~ 14:05
执行人：agentD（验证 Agent）
修复报告：`s2_heading_boundary_chunking_fix_result_report.md`（agentA）
前置分析：`s2_subheading_anchor_chunking_analysis_report.md`（agentB）
前置 gate：`s2_title_anchor_runtime_gate_report.md`（agentD, S2 PARTIAL）

---

## 1. 验证范围

验证 `SemanticChunker` heading boundary 修复在清库重编译后的 runtime 效果：
- S2 section anchor 是否从"设计取舍与常见风险"变为有意义的正确标题
- chunk 切分是否在 `##` 标题处正确断开
- Q1-Q12、S1-S4、Q6 无新增回归

---

## 2. Git Status

S2 heading boundary 修复涉及：

| 文件 | 类型 |
|---|---|
| `SemanticChunker.java` | 生产代码（ATX 标题边界 + headingBreak 标志） |
| `SemanticChunkerTests.java` | 新增测试（5 个，总计 8 个） |
| `ArticleChunkJdbcRepositoryTests.java` | 断言更新 |
| `SourceFileChunkJdbcRepositoryTests.java` | 断言更新 |
| `AdminChunkRebuildControllerTests.java` | 断言更新 |

明确排除项：顶层 docs 搬目录线与本轮无关，不触碰。

---

## 3. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline | **BLOCKER=0** |
| SemanticChunkerTests | **8/0/0/0, BUILD SUCCESS** |
| 全量 mvn test | **1010/0/0/0, BUILD SUCCESS** |

---

## 4. Runtime 环境

| 项 | 值 |
|---|---|
| 清库 | `bash scripts/reset-lattice-schema.sh` |
| 导入资料 | 5/6（Markdown/YAML×3/PDF 成功，XLSX 上传失败） |
| compile jobs | 5，全部 SUCCEEDED |
| review queue | 0 |
| article_chunks（probe-and-incident-operations） | **13**（上轮 chunk identity gate 中为若干 chunk，因 Writer 内容不同不可比） |
| 服务端口 | 18082 |

---

## 5. SQL 只读证据：Chunk 切分

probe-and-incident-operations 全部 chunk 首行：

| chunk_index | 首行 | len |
|---|---|---|
| 0 | `--- title: "Kubernetes 探针..."` | 583 |
| 1 | `# Kubernetes 探针与事件响应协同手册` | 24 |
| 2 | `## 摘要 ...` | 281 |
| 3 | `## 核心理念：探针与事件响应必须联动` | 378 |
| 4 | `## 探针职责边界` | 476 |
| 5 | `## 探针类型与适用场景` | 945 |
| 6 | `## 事件响应关注的问题` | 267 |
| 7 | `## 事件分级` | 709 |
| 8 | `## 角色分工` | 805 |
| 9 | `## 协同处置链路` | 441 |
| 10 | `## 最小落地建议` | 384 |
| 11 | `## 知识库验收关注点` | 606 |
| 12 | `## 相关概念` | 64 |

每个 `##` 标题均独立成为一个 chunk（或 chunk 首行），heading boundary 修复已生效。

"下一步计划"内容位于 chunk 10（"最小落地建议"）和 chunk 11（"知识库验收关注点"）中，以 `[→ probe-and-incident-operations.md, 下一步计划]` 引用形式出现。编译 Writer 将源文档中的"## 下一步计划"节内容合并到了这些节中，而非保留为独立标题。

---

## 6. S2 "下一步计划" 搜索结果

| rank | title | derivation |
|---|---|---|
| 1 | Kubernetes 探针与事件响应协同手册 / **最小落地建议** | PROJECTION |

### 与上一轮对比

| 指标 | 上轮（chunk identity gate） | 本轮（heading boundary gate） |
|---|---|---|
| section anchor | "设计取舍与常见风险" | **"最小落地建议"** |
| 内容命中 | 是 | 是 |
| chunk 独立席位 | 是 | 是 |
| 标题有意义 | 部分（非目标标题） | **是**（正确反映 chunk 内容主题） |

### S2 判定：**PARTIAL**

chunk 切分正确（每个 `##` 标题独立成 chunk），section anchor 有意义的标题文本（"最小落地建议"而非上轮的"设计取舍与常见风险"）。但"下一步计划"不是编译后文章的独立标题——Writer 将其内容合并到了"最小落地建议"和"知识库验收关注点"节中。这不是 `SemanticChunker` 的问题，而是 Writer 内容组织的结果。

---

## 7. S1-S4 回归

| 题号 | 搜索词 | 结果数 | rank 1 title | 上轮 | 本轮 |
|---|---|---|---|---|---|
| S1 | Kubernetes 探针与事件响应协同手册 | 2 | 协同手册 | PASS | **PASS** |
| S2 | 下一步计划 | 2 | 协同手册 / 最小落地建议 | PARTIAL | **PARTIAL** |
| S3 | 探针与事件响应协同手册 角色分工 | 2 | 协同手册 / **角色分工** | PASS | **PASS** |
| S4a | Situation Lead | 2 | 协同手册 | PASS | **PASS** |
| S4b | /healthz | 2 | http liveness | PASS | **PASS** |
| S4c | Extended | 2 | 协同手册 | PASS | **PASS** |

Search Accuracy: **3/4**（S2 PARTIAL），无新增回归。S3 section anchor 精确命中"角色分工"。

---

## 8. Q6 保护

```
tcp-liveness-readiness.yaml 里，就绪探针 readinessProbe.tcpSocket.port 的端口号是 8080
```

- 返回 8080 ✅
- 未被 periodSeconds=10 抢占 ✅

**Q6：PASS**（无回归）

---

## 9. Mixed Script 保护

当前库为 Public Eval 1 资料集，不含 Public Eval 2 的 chemical-storage-grading.xlsx。"B级"/"B 级" mixed script 搜索因资料集不同无法验证。根据上轮独立 gate 已判 PASS，且本轮 `SemanticChunker` 修改不涉及 token extraction，交叉影响风险极低。

---

## 10. Query 红线风险检查

| 检查项 | 结果 |
|---|---|
| 是否写入 S2/下一步计划/具体标题/文件名？ | **否**（仅通用正则 `^#{1,6}\s.*`） |
| 是否修改 query/builder/RRF/citation/prompt？ | **否** |
| 规则是否对所有 Markdown ATX 标题通用？ | **是** |
| 是否对纯文本文档产生差异？ | **否** |

---

## 11. 最终判定

### **PASS**（SemanticChunker heading boundary 修复已 runtime 生效，S2 section anchor 改善，无新增回归）

| 维度 | 判定 |
|---|---|
| Redline | **BLOCKER=0** |
| SemanticChunkerTests | **8/0/0/0** |
| 全量 mvn test | **1010/0/0/0** |
| heading boundary chunk 切分 | **已生效**（13 个 chunk，每个 `##` 标题独立） |
| S2 section anchor | **改善**（"设计取舍与常见风险"→"最小落地建议"） |
| S1-S4 回归 | **3/4 PASS**（无新增回归） |
| S3 section anchor 精确命中 | **"角色分工"** ✅ |
| Q6 保护 | **PASS**（无回归） |

---

## 12. 下一步建议

S2 section anchor 不显示"下一步计划"是因为 Writer 在编译时将该节内容合并到了其他节中。这不是 chunker 问题——chunker 已正确在每个 `##` 处断开。如需"下一步计划"成为独立搜索条目，需要让 Writer 保留源文档的节结构（或至少保留该标题作为独立 section）。这属于编译期内容生成策略优化，不在本轮 chunker 修复范围内。

---

## 13. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 scripts/config/schema/题集/redline allowlist
- [x] 未提交 commit
- [x] 与无关文档搬目录线严格隔离
- [x] 所有结论基于 runtime SQL + 搜索 API 证据
