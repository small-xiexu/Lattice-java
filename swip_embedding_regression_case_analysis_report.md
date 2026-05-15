# SWIP Embedding 恢复后退化 Case 检索链路分析报告

- **报告时间**：2026-05-14
- **分析目的**：逐个分析 embedding 恢复后 4 个 LLM→FALLBACK 退化 case 的检索链路根因
- **退化 case 清单**：GOAL-001、TERMINATE-STUCK-001、APP-LIST-001、CERT-UPDATE-001

---

## 1. Redline 检查

| 指标 | 值 |
|---|---|
| BLOCKER | **0** |

结论：可以继续。

---

## 2. 退化 Case 概览

| Case ID | Baseline | Embedding (本轮) | fallbackReason |
|---|---|---|---|
| GOAL-001 | LLM/PARTIAL_ANSWER | **FALLBACK/PARTIAL_ANSWER** | CITATION_QUALITY_INSUFFICIENT |
| TERMINATE-STUCK-001 | LLM/SUCCESS | **FALLBACK/PARTIAL_ANSWER** | CITATION_QUALITY_INSUFFICIENT |
| APP-LIST-001 | LLM/PARTIAL_ANSWER | **FALLBACK/SUCCESS** | LLM_OUTPUT_INVALID |
| CERT-UPDATE-001 | LLM/SUCCESS | **FALLBACK/PARTIAL_ANSWER** | CITATION_QUALITY_INSUFFICIENT |

---

## 3. 关键发现：所有 ARTICLE 类型证据均未进入 fused 结果

### 3.1 检索通道命中总览

所有 4 个退化 case 的检索结果显示，**全部 3 个 ARTICLE 通道的命中均为 `included_in_fused = false`**：

| 通道 | 证据类型 | 是否进入 fused |
|---|---|---|
| `article_chunk_fts` (FTS 全文) | ARTICLE | **否** ❌ |
| `article_vector` (向量相似度) | ARTICLE | **否** ❌ |
| `chunk_vector` (向量相似度) | ARTICLE | **否** ❌ |
| `source_chunk_fts` (FTS 全文) | SOURCE | **是** ✅ |
| `fact_card_fts` (FTS 全文) | FACT_CARD | **是** ✅ |

### 3.2 每个 Case 的 Vector 检索正确性

| Case ID | Vector 排名第 1 的 Article | 该 Article 是否包含答案 |
|---|---|---|
| GOAL-001 | 系统架构 5 (article_vector #1) / Swip智能键盘系统使用手册 20250702 (chunk_vector #1) | **是**。使用手册 article 首段完整包含 4 个 expectedPoints |
| TERMINATE-STUCK-001 | Swip智能键盘系统使用手册 20250702 (article_vector #1 + chunk_vector #1) | **是**。使用手册 article 含"长按 LOGO 10秒" |
| APP-LIST-001 | Swip智能键盘系统使用手册 20250702 (article_vector #1) / 系统架构 5 (chunk_vector #1) | **是**。系统架构 article 含 APP 安装列表 |
| CERT-UPDATE-001 | HTTPS证书安装 (article_vector #1 + chunk_vector #1) | **是**。HTTPS 证书文章含"证书有效期为一年，系统会在到期前自动续约更新" |

> **结论：Vector 检索完全正确** — 所有 4 个 case 的向量搜索均命中了包含正确答案的 article。问题出在 **RRF 融合阶段**。

---

## 4. 根因分析：`applyStructuredEvidenceGuardrail`

### 4.1 代码路径

```
RrfFusionService.fuse()
  │
  ├─ mergeHits() — RRF 加权融合，article 与 fact_card 各自计分
  │
  ├─ isStructuredAnswerShape(answerShape)  → true (POLICY / SEQUENCE / ENUM)
  │   │
  │   └─ applyStructuredEvidenceGuardrail()
  │         │
  │         ├─ isPrimaryStructuredEvidence() → 仅 FACT_CARD + SOURCE 通道为 "主证据"
  │         │     主证据通道：fact_card_fts, fact_card_vector, source_chunk_fts
  │         │     非主证据通道：article_chunk_fts, article_vector, chunk_vector
  │         │
  │         └─ 排序策略：
  │               Tier 0: FACT_CARD + SOURCE_CHUNK → 优先占用 top-K
  │               Tier 2: ARTICLE → 仅当 top-K 不满时才补入（作为"背景证据"）
  │
  └─ fusedHits 中仅含 fact_card + source_chunk → LLM 收到碎片化证据 → FALLBACK
```

关键代码在 `RrfFusionService.java:417-431`：
- `isPrimaryStructuredEvidence()` 只认 `fact_card_fts`、`fact_card_vector`、`source_chunk_fts` 为主证据
- `article_vector`、`chunk_vector`、`article_chunk_fts` 均归类为"背景证据"(Tier 2)
- 在结构化答案形态（POLICY/SEQUENCE/ENUM/COMPARE/STATUS）下，主证据优先填充 top-8

### 4.2 为什么 baseline 没有这个问题

Baseline 无 embedding，检索通道少 2 路（无 `article_vector`、`chunk_vector`）。相同 guardrail 逻辑下：
- Baseline 的 `article_chunk_fts` 同样被标记为非主证据
- 但 baseline 的 `fact_card_fts` 和 `source_chunk_fts` 命中较少（文本抽取质量差 → FTS 命中少）
- top-8 有剩余空间，article 可被补入 → LLM 能拿到完整 article 生成答案

Embedding 恢复后：
- `article_vector` 和 `chunk_vector` 新增 8 路命中
- `fact_card_fts` 和 `source_chunk_fts` 仍然占优（权重 1.40 vs 1.35/1.00）
- top-8 被 fact_card + source_chunk 占满 → **article 全部被挤出**
- LLM 收到的 fused 证据全是结构化片段 → 引用质量不足 → FALLBACK

### 4.3 权重差异量化

| 通道 | 默认权重 | 被 guardrail 视为主证据 |
|---|---|---|
| `fact_card_fts` | 1.40 | **是** |
| `source_chunk_fts` | 1.30 | **是** |
| `chunk_vector` | 1.35 | 否（ARTICLE 类型） |
| `article_chunk_fts` | 1.25 | 否（ARTICLE 类型） |
| `article_vector` | 1.00 | 否（ARTICLE 类型） |

RRF 公式：`score = weight / (rrfK + rank)` 其中 rrfK=60。

在 RRF 层面，chunk_vector (1.35) 的分数接近 fact_card_fts (1.40)。但由于 guardrail 按证据类型而非 RRF 分数决定优先级，vector 文章即使分数更高也被排除。

---

## 5. 逐 Case 详细分析

### 5.1 GOAL-001（系统目标）

| 维度 | 详情 |
|---|---|
| expectedEvidence | 使用手册 §1 系统目标 + 安装手册 §系统目标 |
| 正确 article | `Swip智能键盘系统使用手册 20250702`（内容首段完整覆盖 4 个 expectedPoints） |
| vector 排名 | article_vector #2 (score 8.60), chunk_vector #2 (score 8.62) |
| fused 结果 | fact_card `结构化规则约束 - 使用手册#0` + fact_card `结构化规则约束 - 安装手册#1,#2` + 安装手册 source_chunks |
| FALLBACK 输出 | 返回了安装手册的设备初始化步骤（错误文档） |
| 根因 | **文章正确检索但被 guardrail 排除**。FALLBACK 拿到的 fact_card 是安装手册的证书/初始化规则，与"系统目标"无关 |

### 5.2 TERMINATE-STUCK-001（终止卡死交易）

| 维度 | 详情 |
|---|---|
| expectedEvidence | 使用手册 FAQ "长按 LOGO 10秒"，安装手册 FAQ |
| 正确 article | `Swip智能键盘系统使用手册 20250702`（含 FAQ 章节，包含正确操作步骤） |
| vector 排名 | article_vector #1 (score 8.55), chunk_vector #1 (score 8.61) |
| fused 结果 | source_chunk `使用手册#1`（前一段落）+ fact_card `结构化顺序步骤 - 安装手册#0` + fact_card `结构化规则约束 - 使用手册#0` |
| FALLBACK 输出 | 证据中**实际包含了正确答案**（"长按 LOGO 10秒"），但因为没有 article 级综合，answer 质量不足以通过 citation 检查 |
| 根因 | **文章正确检索但被 guardrail 排除**。source_chunk 片段碰巧包含了答案，但缺少 article 上下文导致 citation 评分不足 |

### 5.3 APP-LIST-001（APP 列表）

| 维度 | 详情 |
|---|---|
| expectedEvidence | 安装手册 §3.2.2 "包含 SWIP APP Store 在内共有 9 个 APP" |
| 正确 article | `系统架构 5` 或 `FAQ 33`（均涉及 APP 安装章节） |
| vector 排名 | chunk_vector #1 → `系统架构 5` (score 8.67) |
| fused 结果 | source_chunk `安装手册#0` + fact_card `结构化规则约束 - 安装手册#0,#1,#2` + fact_card `结构化键值条目 - 使用手册#0` |
| FALLBACK 输出 | 返回了随机文本片段（如"金辉"、"刘"、"金"、"（"等），完全无意义。fallbackReason = LLM_OUTPUT_INVALID |
| 根因 | **文章正确检索但被 guardrail 排除**。fact_card + source_chunk 的碎片内容远不足以回答"9 个 APP 列表"这种枚举题。LLM 尝试从碎片生成但输出无效 |

### 5.4 CERT-UPDATE-001（证书自动更新）

| 维度 | 详情 |
|---|---|
| expectedEvidence | 安装手册 §4.8 "提前 51/31 天晚上 11 点"，"键盘晚上处于开机状态" |
| 正确 article | `HTTPS证书安装（门店内网）`（summary 明确提到"证书有效期为一年，系统会在到期前自动续约更新"） |
| vector 排名 | article_vector #1 (score 8.64), chunk_vector #1 (score 8.72) |
| fused 结果 | source_chunk `安装手册#0,#2` + fact_card `结构化顺序步骤 - 安装手册#0` + fact_card `结构化规则约束 - 安装手册#0,#1` + fact_card `结构化状态分组 - 安装手册#0,#1` |
| FALLBACK 输出 | 返回了设备初始化的通用步骤（证书申请流程），而非"自动证书更新时间窗口" |
| 根因 | **文章正确检索但被 guardrail 排除**。fact_card 的通用初始化步骤不包含证书更新窗口的具体细节（51/31 天，晚上 11 点）。但 vector search 排名第 1 的 HTTPS证书安装 article 的 content 中是否包含这些细节，取决于 poi_xwpf 的文本提取覆盖 |

---

## 6. 根因分类总结

| 根因 | 影响 case 数 | 严重度 | 说明 |
|---|---|---|---|
| **`applyStructuredEvidenceGuardrail` 排除 ARTICLE 类型证据** | 4/4 | **CRITICAL** | Vector 正确检索，但 guardrail 将 ARTICLE 归类为 Tier 2 "背景证据"，top-8 被 fact_card + source_chunk 填满 |
| **fact_card 内容碎片化** | 4/4 | HIGH | poi_xwpf 提取的 fact_card 是结构化片段（规则约束、键值条目），不含完整上下文 |
| **poi_xwpf 文本覆盖不足** | 1/4 | MEDIUM | CERT-UPDATE-001 的"51/31 天"细节可能未被 poi_xwpf 提取到 article content 中 |

**单一根因**：`applyStructuredEvidenceGuardrail` 将 ARTICLE 类型证据（含 `article_vector`、`chunk_vector`、`article_chunk_fts`）系统性降级为"背景证据"，导致即使向量检索完全正确，LLM 也拿不到 article 级综合内容，只能基于碎片化的 fact_card + source_chunk 生成答案，触发 citation 质量不足而进入 FALLBACK。

---

## 7. 是否建议改代码

**否**。`applyStructuredEvidenceGuardrail` 的设计意图合理——对于结构化答案（枚举/步骤/状态/策略），优先事实性片段而非概述性 article。问题在于当前 SWIP 的 fact_card + source_chunk 碎片质量不足以支撑 LLM 独立生成高质量答案。

---

## 8. 下一轮最小修复建议

**唯一推荐：调整 `lattice.query_retrieval_settings` 的 RRF 通道权重。**

当前默认权重：
| 通道 | 默认权重 |
|---|---|
| `chunk_vector_weight` | 1.35 |
| `article_vector_weight` | 1.00 |
| `fact_card_weight` | 1.40 |
| `source_chunk_weight` | 1.30 |

建议在 `ai-rag-swip-eval` 库中插入一行 `query_retrieval_settings`，将 vector 文章权重提升至与结构化证据同级或更高，让 ARTICLE 在 RRF 层面先于 guardrail 获得更高分数：

```sql
-- 示例：提升 chunk_vector 到 1.60，article_vector 到 1.30，RRF K 降到 30
INSERT INTO lattice.query_retrieval_settings (
    parallel_enabled, fts_weight, source_weight, contribution_weight,
    graph_weight, article_vector_weight, chunk_vector_weight, rrf_k
) VALUES (
    true, 1.0, 1.0, 1.0, 1.20, 1.30, 1.60, 30
);
```

**理由**：
- 不修改源代码（仅 DB 配置）
- 不修改 LLM/embedding 配置
- 不修改题集/regression suite
- 最小改动范围：1 行 INSERT
- 可逆：DELETE 即可回退
- 风险可控：仅影响 SWIP 独立库 `ai-rag-swip-eval`

**备选方案**：直接调高 `fused_hit_count` 上限（`TOP_K` 常量，当前为 8），但这需要修改代码（`QueryGraphRetrievalSupport.java:158`），不符合本轮约束。

**不做**：
- 修改 `applyStructuredEvidenceGuardrail` 逻辑（代码改动，且 guardrail 设计有合理性）
- 调整 embedding 模型或 API key（不在根因路径上）
- 更换 docx 解析器（不解决 vector→guardrail 的链路问题）

---

## 9. 合规声明

| 项 | 状态 |
|---|---|
| 是否修改源代码（src/main/java/**） | **否** |
| 是否修改测试代码（src/test/java/**） | **否** |
| 是否修改配置（src/main/resources/**） | **否** |
| 是否修改题集 | **否** |
| 是否修改 regression suite | **否** |
| 是否修改 redline allowlist | **否** |
| 是否修改 embedding / LLM 配置 | **否** |
| 是否污染主 baseline（ai-rag-knowledge） | **否** |
| 是否提交代码 | **否** |
| 是否启用 OCR | **否** |
| 分析过程是否为只读 | **是**（SQL 只读查询 + 代码只读 + 文件只读） |
