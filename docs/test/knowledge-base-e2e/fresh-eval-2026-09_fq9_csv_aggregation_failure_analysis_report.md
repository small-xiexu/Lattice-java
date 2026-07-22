# PE6 FQ9 CSV 聚合失败 — 只读归因报告

分析时间：2026-06-09
执行人：agentB（治理/归因 Agent）
类型：只读 DB 证据分析，不修改任何文件

---

## 1. 复现方式

FQ9 query: `缺陷清单里 P0 和 P1 级别的缺陷各有多少个？分别是什么状态？`

---

## 2. 实际回答 vs Expected 对比

### Expected

| 级别 | 缺陷编号（状态） | 数量 |
|:---:|------|:---:|
| P0 | DEF-002(已验证)、DEF-006(已修复)、DEF-014(待修复) | **3** |
| P1 | DEF-001(已修复)、DEF-004(待修复)、DEF-008(待修复)、DEF-011(待修复) | **4** |

### 实际回答

> "从当前证据可确认：P0 缺陷有 **2** 个；P1 缺陷可确认至少 **4** 个，其中 DEF-004、DEF-008 的缺陷清单原始行状态未在证据片段中展开"

**差异**：P0 少算 1 个（DEF-014 缺失），P1 数量正确但状态描述不完整（"只能确认它们被发布检查表列为 P1 未关闭项"——误混入 release-checklist.xlsx 证据）。

---

## 3. `defect-list.csv` 入库完整性检查

| 检查项 | 结果 |
|------|:---:|
| article 存在 | ✅ id=33, title="defect list", content_len=6173 |
| article 含全部 15 条缺陷 | ✅ chunk 2 含完整 15 行表格 |
| article 含 P0 聚合 | ✅ chunk 6 "## 高严重级别缺陷" 明确列出 P0=3 (DEF-002/DEF-006/DEF-014) + P1=4 |
| article 含按状态汇总 | ✅ chunk 4 "## 按状态汇总" 含已修复/已验证/待修复/已关闭 分组 |
| referential_keywords | ✅ 含全部 DEF-001~DEF-015、P0/P1/P2/P3 |
| review_status | ✅ passed |
| lifecycle | ✅ ACTIVE |

**入库完整性：完全正常。article 不仅包含原始 CSV 的 15 条记录，Writer 还生成了 "按状态汇总"（chunk 4）和 "高严重级别缺陷"（chunk 6）两个聚合 section，恰好是 FQ9 所需的。**

---

## 4. 证据在何处消失

### 4.1 article 内容结构

```
chunk 0 (905)  — frontmatter（title, summary, referential_keywords 含全部 DEF-xxx + P0/P1）
chunk 1 (388)  — # defect list + 概述 + CSV 格式说明
chunk 2 (1767) — ## defect list（完整 15 行表格）
chunk 3 (897)  — ## 字段与列解释
chunk 4 (255)  — ## 按状态汇总（已修复 3/已验证 5/待修复 6/已关闭 1）
chunk 5 (350)  — ## 按模块汇总
chunk 6 (492)  — ## 高严重级别缺陷 ← FQ9 的直接答案所在！
                  P0: DEF-002(已验证)/DEF-006(已修复)/DEF-014(待修复)
                  P1: DEF-001(已修复)/DEF-004(待修复)/DEF-008(待修复)/DEF-011(待修复)
chunk 7 (331)  — ## 数据质量说明
chunk 8 (772)  — ## Related Concepts
```

### 4.2 为什么 LLM 拿不到 chunk 6

| 层级 | 分析 |
|------|------|
| article 级别 content | 6173 chars → `PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT=1200` 截断 → LLM 只看到前 ~1200 chars（chunks 0-1 + chunk 2 的前几行） |
| chunk 级别的 article_chunk_fts | chunk 6 "高严重级别缺陷" (492 chars) 是完美的聚合摘要。但它的 chunk_text 以 "## 高严重级别缺陷" 为首行——搜索词 "缺陷清单" 不直接匹配此标题。chunk 6 的 LIKE/FTS 匹配依赖表格内的 P0/P1 token，得分可能低于 chunks 0-2 |
| 跨 source 污染 | release-checklist article（id=31）的 referential_keywords 包含 P0/P1/DEF-004/DEF-008/DEF-011，与 FQ9 的搜索词重叠。LLM 观察到的 "P1 未关闭项" 很可能来自 release-checklist 的检查项状态（"未完成"）而非 defect-list 的缺陷状态 |

### 4.3 关键证据链

1. chunk 6 存在且内容完全正确 ✅
2. chunk 6 的 heading（"高严重级别缺陷"）与搜索词（"缺陷清单"）的 token 重叠度低于 chunks 0-2（含 "defect list"、"缺陷清单"）→ chunk 6 在 article_chunk_fts 中的排名可能低于 chunks 0-2
3. article 级别的 `buildBoundedPromptEvidenceContent` 将 6173 chars 截断到 1200 chars → chunk 6（位于 article 中后段）被截断
4. LLM 从 1200-char 截断的前段 article 内容 + 可能的 release-checklist 交叉引用中拼凑答案 → 不完整且被污染

---

## 5. 根因判断

### 主因：**证据已召回但回答漏点**

具体机制：**`PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT = 1200` 截断 + chunk 6 在 article_chunk_fts 中因 heading 不匹配搜索词而排序靠后**，导致 LLM 收不到 "高严重级别缺陷" 的完整聚合数据。

### 为什么不是其他类型

| 候选类型 | 判定 | 理由 |
|----------|:---:|------|
| 资料缺失 | **排除** | article id=33 含全部 15 条缺陷 + P0/P1 聚合 + 按状态汇总 |
| 编译抽取缺失 | **排除** | Writer 不仅完整提取了 CSV 数据，还生成了聚合 summary section |
| chunk 切分问题 | **排除** | chunk 6 边界清晰（"## 高严重级别缺陷"），内容完整 |
| 检索未召回 | **排除** | defect-list article 被确认召回（LLM 答案引用了 DEF-001/DEF-002） |
| rerank 排序低 | **排除为主因** | 排序低是 chunk 6 的次级因素，主因是 1200-char 截断 |
| 引用错误 | 部分 | release-checklist 证据混入导致"P1 未关闭项"表述不精确 |
| 多证据冲突未处理 | 部分 | release-checklist 与 defect-list 对同一批 DEF-xxx 有不同视角 |

---

## 6. 是否需要 agentA 修代码

**是**，但修复点不在 FQ9 特有的 CSV 聚合逻辑，而是**通用的证据打包截断问题**。

当前 `PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT = 1200` 导致包含完整聚合数据的 chunk 6（位于 article 中后段）对 LLM 不可见。此问题与 PE5 FQ3（供应商台账 rating 值被 1200-char 截断）同根。

### 最小修复建议

**方向**：在 chunk 级别的 article_chunk_fts 搜索中，当 article 包含 "summary/aggregation" section（如 "按状态汇总"、"高严重级别缺陷"）时，提高这些 summary chunk 在 fused top-K 中的 ranking weight，或确保它们被纳入 LLM 证据。

更简单的方向：将 `PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT` 从 1200 提升到 2400（或按 article chunk 数量动态调整），使中后段的聚合 section 能进入 LLM context window。

---

## 7. agentA 最小修复提示词草案

```text
你现在是 agentA（代码执行 Agent）。

本轮目标：修复长 article 的证据打包截断问题，使 Writer 生成的聚合/摘要
section 对 LLM 可见。

根因：`PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT = 1200` 截断了 article 中后段的
聚合 section（如"高严重级别缺陷"、"按状态汇总"），导致 LLM 无法看到
Writer 已生成的完整聚合数据。

修改范围：
- 仅修改 AnswerGenerationBaseSupport.java 的 PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT
- 从 1200 提升到 2400

通用性：对所有长 article 生效，不绑定具体题号、文件名、字段名。

禁止修改：query/retrieval/rerank/fallback/citation 主链、schema、prompt、scripts

验证：PE6 FQ9 → P0 应为 3 个（DEF-002/DEF-006/DEF-014）
```

---

## 8. 明确声明

- [x] 未修改任何代码
- [x] 未修改题集 expected
- [x] 未修改 prompt/schema/scripts
- [x] 未清库、未重建、未导入
- [x] 未提交 commit
- [x] DB 证据确凿：article id=33 含 chunk 6（"高严重级别缺陷"，P0=3/P1=4 完整）
- [x] 根因统一为 `PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT = 1200` 截断
- [x] Writer 生成的质量正常（含聚合 section），问题不在编译层
- [x] 此问题与 PE5 FQ3 同根：长 article 中后段的聚合数据被 1200-char 截断
