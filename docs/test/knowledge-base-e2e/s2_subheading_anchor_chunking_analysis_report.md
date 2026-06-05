# S2 子标题 Anchor 展示不精确 — 只读根因分析报告

分析时间：2026-06-05
执行人：agentB（治理/链路分析 Agent）
类型：只读根因分析与最小通用修复设计，无代码修改

---

## 1. 当前 S2 Runtime 现象复述

**来源**：`s2_title_anchor_runtime_gate_report.md`（2026-06-05 agentD runtime gate）

搜索词：`下一步计划`

| rank | title | conceptId |
|------|-------|-----------|
| 1 | Kubernetes 探针与事件响应协同手册 / **设计取舍与常见风险** | probe-and-incident-operations |

- chunk identity 修复已生效（不再是整篇 article 的泛化命中）
- 搜索结果排名首位，目标内容已被检索到
- 但 section anchor 显示为"设计取舍与常见风险"而非"下一步计划"
- S2 判定：**PARTIAL**（从 FAIL 改善，但 anchor 展示仍不精确）

---

## 2. 相关源码链路

### 2.1 Chunk 切分：`SemanticChunker`

**文件**：`src/main/java/com/xbk/lattice/infra/chunking/SemanticChunker.java`

两阶段算法：

| 阶段 | 方法 | 行为 |
|------|------|------|
| Phase 1 | `parseUnits(content)` | 按空白行、Markdown 标题行（`#`）、水平线（`---`）、列表项（`- `、`* `）、代码围栏（` ``` `）将内容拆分为语义单元（TextUnit） |
| Phase 2 | `chunk(units, maxChars, overlapRatio)` | 逐个累积语义单元，直到添加下一个单元会超出 `maxChars=3600` 时断开为一个 chunk。断开后通过 `rewindForOverlap` 回退约 540 字符（15%）产生重叠 |

**关键事实**：标题行（`## 下一步计划`）在 Phase 1 中是单元边界（当前单元在标题前 flush），但**标题本身不强制产生 chunk 边界**。标题单元和后续内容单元在 Phase 2 中被累积到同一个 chunk，直到总字符数超过 3600 才断开。

### 2.2 Section Anchor 提取：`ChunkHitIdentitySupport.extractSectionAnchor()`

**文件**：`src/main/java/com/xbk/lattice/query/service/ChunkHitIdentitySupport.java`，第 171-183 行

```java
static String extractSectionAnchor(String chunkText) {
    String[] lines = chunkText.split("\\R");
    for (String line : lines) {
        String heading = extractHeadingFromLine(line);
        if (!isBlank(heading)) {
            return heading;  // ← 返回第一个匹配的标题
        }
    }
    return "";
}
```

**逻辑**：逐行扫描 chunk 文本，返回第一个匹配 Markdown 标题（`# ` 开头）或 source-ref 格式（`[→ file, anchor]` 且该行以 `[` 开头）。

### 2.3 展示标题构造：`ChunkHitIdentitySupport.displayTitle()`

**文件**：同上，第 152-163 行

```java
static String displayTitle(String articleTitle, String sectionAnchor) {
    if (isBlank(sectionAnchor)) return articleTitle;
    if (isBlank(articleTitle)) return sectionAnchor;
    if (articleTitle.equals(sectionAnchor)) return articleTitle;
    return articleTitle + " / " + sectionAnchor;
}
```

### 2.4 调用入口：`ArticleChunkFtsSearchService.toQueryArticleHit()`

在将 chunk 命中转为 `QueryArticleHit` 时，调用 `extractSectionAnchor(chunkText)` 和 `displayTitle(articleTitle, sectionAnchor)` 设置搜索结果标题。

---

## 3. 当前数据库只读证据

### 3.1 目标文章

| article_key | title | review_status | lifecycle |
|---|---|---|---|
| `default-source--01-markdown-probe-and-incident-operations` | Kubernetes 探针与事件响应协同手册 | passed | ACTIVE |

### 3.2 目标 chunk（chunk_index=2）

chunk_text 结构（简化）：

```
一个最小落地场景可以...并同步准备一份简化事件响应清单。
[→ probe-and-incident-operations.md, 下一步计划]
该简化事件响应清单至少覆盖 initiate、assess、contain、remediate、retrospect 五个阶段。
[→ probe-and-incident-operations.md, 下一步计划]

通过人工演练，需要确认 probe 失败时谁负责判定...
[→ probe-and-incident-operations.md, 下一步计划]

## 设计取舍与常见风险          ← 第一个 Markdown 标题

### 探针过宽松与过激进的取舍

探针设计得太宽松会让异常暴露太慢...
```

### 3.3 `extractSectionAnchor` 对 chunk 2 的执行过程

| 行号 | 内容 | extractHeadingFromLine 结果 |
|:---:|------|:---:|
| 1 | `一个最小落地场景可以...` | 空（非标题、非 source-ref 行首） |
| 2 | `[→ probe-and-incident-operations.md, 下一步计划]` | 空（该行不是以 `[` 开头，source-ref 在行内） |
| ... | ... | ... |
| N | `## 设计取舍与常见风险` | **"设计取舍与常见风险"** ← 返回 |

### 3.4 为什么 `[→ ..., 下一步计划]` 没被提取

`extractHeadingFromLine()` 第 199-202 行支持 source-ref 格式提取：

```java
if (trimmedLine.startsWith("[") && trimmedLine.endsWith("]") && markerIndex >= 0) {
    return normalizeHeading(trimmedLine.substring(markerIndex + 2, trimmedLine.length() - 1));
}
```

但该逻辑要求 source-ref **独占一行**（`trimmedLine.startsWith("[")`）。在 chunk 2 中，`[→ ..., 下一步计划]` 是**内联在段落文本中**的引用标记，不是独立行。

### 3.5 `## 下一步计划` 标题在哪里

标题 `## 下一步计划` 在 chunk 1 末尾（chunk 1 的文本预览被截断，但 chunk 1 包含该标题行及其开头内容）。chunk 边界在"下一步计划"节的正文中间断开——标题留在 chunk 1，后续正文溢入 chunk 2。

---

## 4. 根因判断

### 根因：**chunk 切分策略未在 `##` 标题处强制断开 chunk**

`SemanticChunker` 将标题行视为语义单元边界（unit boundary），但不视为 chunk 边界（chunk boundary）。当"## 下一步计划"节的内容超过 3600 字符限制时，chunk 在正文中间断开，导致：

1. 标题 `## 下一步计划` 留在 chunk 1
2. 后续正文（含 source-ref `下一步计划`）溢入 chunk 2
3. Chunk 2 的第一个结构性标题是 `## 设计取舍与常见风险`
4. `extractSectionAnchor()` 正确返回了 chunk 2 中第一个可识别的标题："设计取舍与常见风险"

**这不是 `extractSectionAnchor` 的 bug**——它在给定 chunk 文本的前提下，正确地找到了第一个 Markdown 标题。问题在于 chunk 2 的文本中根本不含 `## 下一步计划` 标题行。

### 排除项

| 候选根因 | 判定 | 理由 |
|----------|:---:|------|
| sectionAnchor 提取逻辑有 bug | **排除** | 在当前 chunk 文本中正确找到了第一个 Markdown 标题 |
| source-ref 提取规则有遗漏 | **排除为独立根因** | 即使修复内联 source-ref 提取，它也只是引用标记而非结构标题 |
| chunk identity 修复未生效 | **排除** | 已在 runtime gate 确认生效（chunk 不再被 article 折叠） |
| 检索未召回 | **排除** | 目标 chunk 排在搜索结果首位 |
| 展示标题拼接逻辑 | **排除** | `displayTitle()` 正确拼接了 articleTitle + sectionAnchor |

---

## 5. 候选修复方案对比

| # | 方案 | 层面 | 是否需要重编译 | 改动面 | 对单标题 chunk 的影响 | 对长文档的影响 |
|---|------|:---:|:---:|:---:|---|:---|
| **A** | **`SemanticChunker.chunk()`：标题单元强制开始新 chunk** | 编译期 | **是** | 小（1 个方法，约 5 行） | 无变化（标题本就在 chunk 首行） | 产生更多、更短的 chunk |
| B | `extractSectionAnchor()`：收集全部标题，选最深或最近 | 查询期 | 否 | 中（需改提取逻辑 + 测试） | 无变化 | 可能选中子标题而非节标题 |
| C | `extractSectionAnchor()`：额外提取内联 source-ref 锚点 | 查询期 | 否 | 小（约 10 行） | 无变化 | 依赖 source-ref 格式存在 |
| D | 为 chunk 增加 `parent_section` metadata 列 | 编译 + schema | 是 | 大（schema + record + mapper + 查询） | 无变化 | 最完整但改动面最大 |

### 方案 A 详解（推荐）

**修改位置**：`SemanticChunker.chunk()` 方法

**逻辑变更**：在累积单元到 chunk 时，如果遇到标题单元（文本以 `#` 开头），且当前 builder 已有内容，则先将当前 chunk 封存，再开始新 chunk。

**伪代码**：
```
for each unit in units:
    if unit.isHeading() and builder.hasContent():
        finalize current chunk
        start new chunk
    if builder would exceed maxChars:
        finalize current chunk
        start new chunk with overlap
    append unit to builder
```

**为什么这是通用修复**：
- 对所有 Markdown 文档生效（不限于特定文件名、标题、题号）
- `isHeading` 基于通用文本结构（行首 `#`），与 `extractHeadingFromLine` 使用相同的通用规则
- 不写入任何业务词、文档名、eval case id

**为什么不是 case 特判**：

| 检查项 | 判定 |
|--------|:---:|
| 是否检查文件名？ | 否 |
| 是否检查 article title？ | 否 |
| 是否检查具体标题文本（如"下一步计划"）？ | 否 |
| 是否检查 eval 题号？ | 否 |
| 是否依赖 source-ref 格式？ | 否 |
| 规则是否对所有 `#` 标题通用？ | **是** |

### 方案 B 不推荐的原因

即使收集所有标题，chunk 2 中也不含 `## 下一步计划` 标题行。chunk 2 中的标题是"设计取舍与常见风险"和"探针过宽松与过激进的取舍"。无论选择算法如何优化，都无法从 chunk 2 中提取出不在其中的标题。

### 方案 C 不推荐的原因

内联 source-ref 格式 `[→ ..., 下一步计划]` 是编译器生成的特有引用标记格式。将其作为 section anchor 的来源，语义上不正确——这是引用锚点而非结构标题。且格式变化（如编译器不再生成此格式）会导致静默失效。

### 方案 D 不推荐的原因

Schema 变更 + Record 字段 + Mapper XML + 编译期写入 + 查询期消费，改动面是方案 A 的 5-10 倍。当前 `article_chunks` 表只有 `chunk_text` 和 `search_tsv`，没有 metadata 列。增加需要 migration。

---

## 6. 推荐的唯一最小修复方案

### **方案 A：`SemanticChunker.chunk()` 标题单元强制开始新 chunk**

这是唯一能从根本上解决问题的方案。标题 `## 下一步计划` 在 chunk 1 中，其正文溢入 chunk 2——无论查询期如何优化 anchor 提取，都无法从 chunk 2 中提取出不在其中的标题。必须从源头确保标题与其所属内容在同一 chunk 中（或标题至少成为独立 chunk 的首行）。

### 副作用评估

| 维度 | 影响 | 缓解 |
|------|------|------|
| Chunk 数量 | 会增加（每个 `##` 标题产生新 chunk） | 当前 3 个 chunk 可能变成 8-10 个；仍在可接受范围 |
| Chunk 大小 | 部分 chunk 变小（短节 < 500 字符） | 小 chunk 对搜索精度有利（更精确的定位）；重叠机制保留上下文 |
| 向量索引 | 需重建（chunk 文本变化） | `article_chunk_vector_index` 在 compile 时自动重建 |
| FTS 检索 | chunk 变小 → 匹配精度提升，但单 chunk 上下文减少 | 重叠机制（15%）保证相邻 chunk 提供上下文 |
| 非 Markdown 文档 | **无影响**（纯文本/PDF/CSV 没有 `##` 标题） | — |
| 已有 eval | 需重新编译资料才能验证 S2 改善 | 见验证计划 |

### 需要重新编译

是。chunk 切分发生在编译期的 `ArticlePersistSupport.rebuildArticleChunks()` → `SemanticChunker.chunk()` 调用中。修改后必须重新编译（清库 + 上传 + compile）才能产生新 chunk。

---

## 7. 影响范围

| 组件 | 是否受影响 | 说明 |
|------|:---:|------|
| `SemanticChunker.java` | **是**（唯一修改点） | `chunk()` 方法 |
| `ArticleChunkJdbcRepository` | 否 | 调用 `semanticChunker.chunk()` 的接口不变 |
| `SourceFileChunkJdbcRepository` | 否（但同样受益） | 使用同一个 `SemanticChunker`，source chunk 也会在标题处断开 |
| `ChunkHitIdentitySupport` | 否 | `extractSectionAnchor()` 逻辑不变，但输入更精确 |
| `RrfFusionService` | 否 | chunk identity 不变 |
| `AnswerGenerationService` | 否 | 不涉及 query/answer 主链 |
| Citation binding | 否 | chunk 级 citation 引用 `chunk_text` 内容，不受标题位置影响 |
| `article_chunks` schema | 否 | 不需要新列 |
| Q6 保护 | 否 | Q6 依赖 terminal field alias，不依赖 chunk 切分 |
| Public Eval 2 | 否（间接改善） | FS2 markdown chunk 标题精度可能同步改善 |

---

## 8. agentA 下一轮代码修复提示词草案

```text
你现在是 agentA（代码执行 Agent）。

本轮目标：
修复 SemanticChunker 在 Markdown 标题处不强制断开 chunk 的问题，
使每个 ## 标题成为其所在 chunk 的首行，从而让 sectionAnchor 提取能正确定位节标题。

根因：
SemanticChunker 将 Markdown 标题视为语义单元边界（unit boundary），但不视为
chunk 边界。当一节内容超过 3600 字符时，标题留在前一个 chunk，后续正文溢入
下一个 chunk——导致下一个 chunk 的 sectionAnchor 展示为后续节的标题而非当前内容
所属节的标题。

修改范围：
- 只修改 src/main/java/com/xbk/lattice/infra/chunking/SemanticChunker.java
- 只修改 chunk() 方法（约 5 行新增逻辑）
- 不改其他任何文件

修改要求：
1. 在 chunk() 方法的单元累积循环中，新增判断：
   如果当前 TextUnit 是标题单元（文本 trim 后以 "#" 开头），且当前 chunk builder
   已有内容（非空），则先将当前 chunk 封存，再开始新 chunk 继续累积。
2. 标题单元的判断方式：使用与 extractHeadingFromLine 相同的通用规则——
   文本 trim 后匹配 `^#{1,6}\s`。
3. 封存当前 chunk 后，新 chunk 从当前标题单元开始累积。
4. 保持现有的 maxChars 和 overlap 逻辑不变——标题只是新增了一个可选的 chunk
   边界条件，不替代 size cap。

通用性要求：
- 对任何以 "#" 开头的 Markdown 标题生效，不限于特定标题文本、文件名、文档标题
- 不写入 "下一步计划"、"S2"、"probe-and-incident-operations" 等任何样例字符串
- 对纯文本/PDF/CSV（无 Markdown 标题）的 chunking 行为不变

禁止事项：
- 禁止修改 ChunkHitIdentitySupport.java
- 禁止修改 RrfFusionService / ArticleChunkFtsSearchService
- 禁止修改 AnswerGenerationService / AnswerFallbackConclusionBuilder
- 禁止修改 schema.sql / Mapper XML
- 禁止修改 tests（除非旧测试断言与新的标题边界行为冲突）
- 禁止修改 scripts / prompt / config / 题集
- 禁止提交 commit

redline / mvn test 要求：
- redline BLOCKER=0
- 现有 SemanticChunker 相关测试可能需要调整断言（chunk 数量/大小可能变化）
- mvn test 全量通过

验证计划（交给 agentD）：
1. 编译现有 Public Eval 1 资料（清库 + 导入 + compile）
2. 搜索"下一步计划" → chunk anchor 应显示为"下一步计划"而非"设计取舍与常见风险"
3. S1-S4 全部回归
4. Q1-Q12 全部回归
5. Q6 保护验证
6. 如可能，验证 Public Eval 2 的 FS2（"化学品分类存储" markdown chunk 标题精度）
```

---

## 9. 验证建议

| 验证项 | 方法 | 通过标准 |
|--------|------|----------|
| Redline | `bash scripts/scan-redline.sh special_cases_report.md` | BLOCKER=0 |
| 定向测试 | SemanticChunker 相关单元测试 | 全部通过（可能需要更新旧断言） |
| 全量 Maven | `mvn test` | Failures=0, Errors=0 |
| S2 runtime | 清库 + 重编译 + 搜索"下一步计划" | section anchor 为"下一步计划" |
| S1-S4 回归 | 完整搜索回归 | 无新增 FAIL |
| Q1-Q12 回归 | 完整 query 回归 | 无新增 FAIL |
| Q6 保护 | 验证 tcpSocket.port=8080 | PASS |
| mixed script 保护 | 验证"B级"搜索 | 无回归 |

---

## 10. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集 / redline allowlist
- [x] 未提交 commit
- [x] 未清库 / 重建 / 导入资料
- [x] 未跑 mvn test / redline / baseline
- [x] 未读取 hidden eval
- [x] 所有结论基于源码只读分析 + 数据库只读查询 + runtime gate 报告交叉验证
- [x] 推荐方案为通用 Markdown 标题边界规则，无 case 特判
- [x] 未将具体题面、答案、文件名写入生产代码建议
- [x] 未建议修改 RRF / fallback / AnswerGeneration
