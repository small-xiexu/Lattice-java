# PE1 S2 Section Anchor PARTIAL — 只读归因报告

分析时间：2026-06-06
执行人：agentB（治理/链路分析 Agent）
类型：只读根因归因，无代码修改

---

## 1. 本轮目标

只读归因 PE1 S2 搜索 "下一步计划" 为什么仍为 PARTIAL。判断根因属于 Writer 内容重组、chunk 切分、搜索召回、title 展示还是评测口径问题。

---

## 2. 当前数据状态

| 项 | 值 |
|----|-----|
| 数据库 | `ai-rag-knowledge.lattice` — PE1 runtime gate 数据 |
| 目标 article (id=3) | `Kubernetes 探针与事件响应协同手册`，content_len=5100 |
| review_status / lifecycle | `passed` / `ACTIVE` |
| article_chunks | 8 个（chunk 0-7） |

---

## 3. S2 问题文本与期望

| 字段 | 值 |
|------|-----|
| 搜索词 | `下一步计划` |
| 搜索维度 | anchorTitle |
| 期望 | 搜索命中的条目应显示"下一步计划"作为 section/anchor title |
| 最新 gate 结果 | 2 条结果，rank1="协同手册 / 协同处置流程"，**PARTIAL** |

---

## 4. 历史 S2 修复对照

| 修复 | 时间 | 效果 | 当前状态 |
|------|------|------|:---:|
| chunk identity 修复（`549f0e3`） | 2026-05-27 | chunk 不再被 article 折叠 → S2 FAIL→PARTIAL | ✅ 仍生效 |
| heading boundary chunking（`1d7be23`） | 2026-06-05 | `##` 标题处强制断 chunk | ✅ 仍生效（chunk 边界清晰） |
| 剩余问题 | — | section anchor 不精确 | ❌ 本轮分析对象 |

---

## 5. 源文档检查

源文件：`docs/test/knowledge-base-e2e/sources/01_markdown/probe-and-incident-operations.md`

源文档中存在 `## 下一步计划` 作为独立 Markdown 标题，其下包含落地验证、最小场景、人工演练等内容。

**确认：源文档中 "下一步计划" 是结构标题，不是内联文本。**

---

## 6. Writer 输出检查

### 6.1 Article 标题结构（id=3，article_key=`probe-and-incident-operations--...`）

```
# Kubernetes 探针与事件响应协同手册
## 概述
## 探针如何分工
## 事件响应如何接住探针暴露的风险
## 角色分工与协同节奏
## 落地建议           ← Writer 将 "## 下一步计划" 改写为此
## 相关概念
```

### 6.2 关键发现

**Writer 将源文档的 `## 下一步计划` 改写为 `## 落地建议`。**

"下一步计划" 文本仅以 source ref 形式存在于 `## 落地建议` 节的段落中：
```
[→ probe-and-incident-operations.md, 下一步计划]
```

这是内联引用标记，不是结构标题。"下一步计划" 不再是任何 chunk 的首行标题。

---

## 7. Chunk / SectionAnchor 检查

| chunk | 首行标题 | sectionAnchor | 含 "下一步计划" 文本？ |
|:---:|------|------|:---:|
| 0 | （frontmatter） | — | 否 |
| 1 | # Kubernetes 探针与事件响应协同手册 | Kubernetes 探针与事件响应协同手册 | 否 |
| 2 | ## 概述 | 概述 | 否 |
| 3 | ## 探针如何分工 | 探针如何分工 | 否 |
| 4 | ## 事件响应如何接住探针暴露的风险 | 事件响应如何接住探针暴露的风险 | 否 |
| 5 | ## 角色分工与协同节奏 | 角色分工与协同节奏 | 否 |
| 6 | ## 落地建议 | **落地建议** | **是**（4 处 source ref） |
| 7 | ## 相关概念 | 相关概念 | 否 |

**chunk 6 是唯一包含 "下一步计划" 文本的 chunk，但其 sectionAnchor 为 "落地建议"。**

`extractSectionAnchor()` 正确地从 chunk 6 的 chunk_text 中提取了第一个 Markdown 标题（`## 落地建议`）作为 sectionAnchor。**这不是 extractSectionAnchor 的 bug**——在当前 chunk 文本中，"落地建议" 确实是第一个结构性标题。

---

## 8. 搜索行为分析

搜索 "下一步计划" 时：

1. **FTS**：chunk 6 的 `search_tsv` 应包含 "下一步计划" token（来自 source ref 文本）→ FTS 匹配 ✓
2. **LIKE**：chunk 6 的 `chunk_text` 包含 "下一步计划" 子串 → LIKE 匹配 ✓
3. **RRF 融合**：chunk 6 和 chunk 4（含 "协同" 相关文本）均被召回并融合
4. **搜索结果**：
   - Rank 1 显示 sectionAnchor = "协同处置流程"（或类似）——来自其他更匹配的 chunk
   - chunk 6 的 sectionAnchor = "落地建议" ——与搜索词 "下一步计划" 无直接语义关联
   - 用户看到的结果标题中没有 "下一步计划"

**核心矛盾**：chunk 6 被成功检索（内容匹配），但展示标题（sectionAnchor="落地建议"）与搜索词（"下一步计划"）不一致。用户无法从搜索结果中判断该 chunk 就是 "下一步计划" 的内容。

---

## 9. 主失败类型归类

### **编译抽取缺失**（Writer 内容重组导致源文档标题丢失）

源文档有 `## 下一步计划` 作为结构标题，但 Writer LLM 在生成 article 时将其改写为 `## 落地建议`。这导致：
1. 搜索结果中不存在 sectionAnchor="下一步计划" 的条目
2. 包含目标内容的 chunk 以 "落地建议" 作为展示标题
3. 搜索精度不足——用户无法直观确认搜索结果对应 "下一步计划"

**排除的类型**：

| 候选类型 | 判定 | 理由 |
|----------|:---:|------|
| 资料缺失 | **排除** | 源文档存在 `## 下一步计划` |
| chunk 切分问题 | **排除** | heading boundary 修复已生效，chunk 边界清晰 |
| 检索未召回 | **排除** | chunk 6 被检索到（内容含 "下一步计划" source ref） |
| rerank/RRF 排序低 | **排除为主因** | chunk 6 可能不在 rank1，但根因是 Writer 标题丢失，不是排序 |
| search 展示 anchor 错 | **排除** | sectionAnchor 正确提取了 chunk 首行标题 "落地建议" |
| RRF 身份折叠 | **排除** | chunk identity 修复已生效 |
| 评测口径问题 | **排除** | 搜索 "下一步计划" 期望看到对应标题是合理预期 |

---

## 10. 根因判断

### Writer LLM 在生成 article 时对源文档标题做了语义改写，导致关键搜索锚点（"下一步计划"）从结构标题降级为内联引用文本

具体链条：

```
源 Markdown: "## 下一步计划" (结构标题)
  → Writer LLM: 语义改写为 "## 落地建议"
    → article content: "下一步计划" 仅作为 [→ ..., 下一步计划] source ref 存在
      → chunk 6 sectionAnchor = "落地建议" (chunk 首行标题)
        → 搜索 "下一步计划": chunk 6 被检索到，但展示标题为 "落地建议"
          → 用户看到 "协同手册 / 落地建议" 无法关联到 "下一步计划"
            → S2 PARTIAL
```

### 为什么历史修复没有解决这个问题

- **chunk identity 修复**：解决了 chunk 被 article 折叠的问题（S2 FAIL→PARTIAL）
- **heading boundary chunking**：确保 `##` 标题 start new chunk（chunk 边界正确）
- **两轮修复都没有触及 Writer 内容重组问题**——chunk 边界和身份正确，但 chunk 内的标题文本已经被 Writer 改变

---

## 11. 为什么不能写 S2 / 具体标题 / 文件名特判

- 生产代码中写入 `"下一步计划" → 必须保留` 硬编码 → 违反红线
- 为 `probe-and-incident-operations.md` 写文件名特判 → 红线禁止
- 为 "下一步计划" 写 Writer prompt 特判 → 红线禁止
- 为 S2 写题号特判 → 红线禁止

任何通用修复必须对所有文档、所有标题一视同仁。

---

## 12. 下一步唯一最小动作建议

### 方向：Writer prompt 增强——更忠实保留源文档的标题文本

当前 Writer prompt 允许 LLM 对源文档标题做语义改写（如 "下一步计划"→"落地建议"），这有利于文章可读性但伤害了搜索精度。

**最小修改**：在 Writer system prompt 中增加一条通用规则：

> 当源文档中存在明确的 Markdown 标题（`##`、`###`）时，文章应优先使用源文档的原始标题文本，或至少将原始标题作为同义词/别名保留在节内容中。不得将源文档的节标题改写为语义相近但措辞不同的标题。

**为什么这是通用修复**：
- 对所有文档的所有节标题统一生效
- 不绑定 "下一步计划" 或任何具体标题文本
- 不绑定文件名或题号
- 保留 Writer 的组织自由度（节顺序、合并策略），只约束标题措辞

**风险**：
- 可能降低文章可读性（源文档标题可能不够"文章化"）——但搜索精度 > 可读性对于知识库场景
- 对已 PASS 的 PE2 题目无负面影响（更忠实的标题不会让答案变错）

**不建议**：
- ❌ 在 search 层识别 "下一步计划" 并做 display title 映射 → case 特判
- ❌ 在 chunk 层做 title/anchor 重映射 → 查询期复杂化
- ❌ 降低 S2 的评测预期 → 回避真实问题

---

## 13. agentA 修复提示词草案

```text
你现在是 agentA（代码执行 Agent）。

本轮目标：
修改 Writer system prompt，使其更忠实保留源文档的 Markdown 标题文本，
修复 PE1 S2 "下一步计划" 被改写为 "落地建议" 导致的搜索精度问题。

根因：
Writer LLM 在生成 article 时对源文档节标题做了语义改写
（"## 下一步计划" → "## 落地建议"），导致搜索结果中无法直观定位目标节。

修改范围：
- 只修改 src/main/resources/prompts/compiler/writer-text.md
- 在 prompt 中增加一条通用规则（非 "下一步计划" 特判）
- 不改 Java 代码、不改其他 prompt 文件

Prompt 规则草案：
当源文档的 structured sections 中包含 Markdown 标题行（## / ###），
文章对应节的标题应优先使用源文档的原始标题文本。
如需调整标题措辞以提升可读性，应将原始标题作为首句或别名保留在节内容中，
确保关键词搜索仍能定位到该节。

通用性要求：
- 不写入 "下一步计划"、"落地建议"、"probe-and-incident-operations" 等具体文本
- 对所有文档、所有标题一视同仁
- 规则为通用标题保真要求

禁止事项：
- 禁止修改 Java 代码
- 禁止修改 query/retrieval/search 主链
- 禁止修改其他 prompt 文件
- 禁止提交 commit

验证计划（交给 agentD）：
1. PE1 清库重编译
2. 搜索 "下一步计划" → rank1 sectionAnchor 应为 "下一步计划"（或含 "下一步计划"）
3. S2 应变为 PASS（或至少 rank1 标题能直观关联到搜索词）
4. PE2 无回归（全名标题不受影响）
```

---

## 14. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt / config / schema / scripts
- [x] 未修改题集 / redline allowlist
- [x] 未提交 commit
- [x] 未清库 / 重建 / 导入资料
- [x] 未读取 hidden eval
- [x] DB 为 PE1 runtime gate 数据，article/chunk 内容可直接查证
- [x] Writer 已将 "## 下一步计划" 改写为 "## 落地建议" — DB 证据确凿
- [x] 推荐修复为通用 Writer prompt 标题保真规则，不包含 case 特判
