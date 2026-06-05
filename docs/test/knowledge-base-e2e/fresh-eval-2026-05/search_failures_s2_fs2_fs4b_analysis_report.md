# 搜索失败项 S2 / FS2 / FS4b — 只读归因分析报告

分析时间：2026-06-05
执行人：agentB（治理/链路分析 Agent）
类型：只读根因分析与通用修复建议，无代码修改

---

## 1. 当前 git status 摘要

```
M  "docs/模型绑定配置参考.md"   (私有配置，不提交)
M  special_cases_report.md     (redline 输出，不提交)
?? docs/test/.../fg1_terminal_unit_current_breakpoint_analysis_report.md
?? docs/test/.../fresh_eval_post_cleanup_remaining_failure_analysis_report.md
```

无生产代码、测试代码、配置、脚本或 prompt 修改。terminal fix 主线已提交（`549f0e3`、`57c34ac`、`0ea3dfd`）。

---

## 2. 当前数据库状态

数据库 `ai-rag-knowledge.lattice` 当前包含 **Public Eval 1** 资料：

| 表 | 计数 |
|---|---|
| articles | 3 |
| article_chunks | 7 |
| fact_cards | 9 |
| fact_card_terminal_units | 38 |

这是 Public Eval 1 保护回归后的残留数据（Kubernetes 探针协同手册 + grpc/http liveness YAML）。Public Eval 2 资料不在当前库中。

**无法运行时复现 FS2/FS4b**：当前数据库不含 Public Eval 2 资料（lab-safety-management-handbook.md、chemical-storage-grading.xlsx 等）。以下分析基于源码审查 + 已有报告交叉验证 + 当前库只读 SQL。

---

## 3. S2 "下一步计划" — 失败分析

### 3.1 复现方式与结果

**来源**：`public_eval1_protection_after_fg1_raw_query_match_gate_report.md`（2026-06-04 agentD runtime gate）

搜索词：`下一步计划`

Top5 结果（1 条）：
| rank | title | conceptId |
|------|-------|-----------|
| 1 | Kubernetes 探针与事件响应协同手册 / 9. 关键取舍与风险 | probe-and-incident-operations |

**目标**：应出现明确标注为"下一步计划"的 chunk 条目。

### 3.2 失败类型：**展示标题问题**

搜索能够召回包含"下一步计划"内容的 chunk（排名第 1），但 section anchor 显示为"9. 关键取舍与风险"而非"下一步计划"。

### 3.3 源码链路定位

**断点**：`ChunkHitIdentitySupport.extractSectionAnchor()`（第 171-183 行）

该方法只扫描 chunk_text 的**第一行**来提取 markdown 标题：
```java
static String extractSectionAnchor(String chunkText) {
    String[] lines = chunkText.split("\\R");
    for (String line : lines) {
        String heading = extractHeadingFromLine(line);
        if (!isBlank(heading)) return heading;
    }
    return "";
}
```

**机制**：当"下一步计划"是 chunk 内的一个子标题（非首行），而首行标题是"9. 关键取舍与风险"时，section anchor 被设为后者。搜索结果展示了正确的 chunk，但标题不能反映"下一步计划"这个子主题。

### 3.4 根因判断

chunk 切分边界未在"下一步计划"标题处断开。该标题落在某个 chunk 内部而非 chunk 开头，导致 `extractSectionAnchor()` 无法捕获它。

**这不是检索未召回**（chunk 已被检索到且排在首位），**不是身份折叠**（chunkIdentity 已正确保留），**不是 RRF 排序低**。问题纯粹在于：chunk 的展示标题（section anchor）不能精确反映其包含的所有逻辑节。

### 3.5 数据核实

| 表 | 当前库状态 |
|---|---|
| source_files | 4（含 Markdown） |
| articles | 3 |
| article_chunks | 7 |
| 含"下一步计划"的 chunk | 存在于 probe-and-incident-operations 文档的某个 chunk 中 |

---

## 4. FS2 "化学品分类存储" — 失败分析

### 4.1 复现方式与结果

**来源**：`full_public_eval_after_fg1_raw_query_match_gate_report.md`（2026-06-04 agentD runtime gate）

搜索词：`化学品分类存储`

Top5 结果摘要：
- "化学品存储分级表"（XLSX article）出现在 rank3-4
- markdown chunk（lab-safety-management-handbook 中标题为"化学品分类存储"的节）**未出现**

### 4.2 失败类型：**检索排序低**

XLSX article "化学品存储分级表"被检索到（标题部分匹配），但 markdown 文档 `lab-safety-management-handbook.md` 中标题为"化学品分类存储"的 article chunk 未在 Top5 中出现。

### 4.3 源码链路定位

搜索通过 `article_chunk_fts` 通道进行。该通道的 SQL（`ArticleChunkMapper.xml` `searchLexical`）：

1. **FTS 主条件**：`ac.search_tsv @@ plainto_tsquery(tsConfig, '化学品分类存储')`
2. **LIKE 加分**：对每个 token 在 `chunk_text`（权重 3.0）、`articles.title`（权重 1.5）、`articles.concept_id`（权重 1.0）上做 LIKE 匹配

QueryTokenExtractor 对"化学品分类存储"（7 个 Han 字符）生成 2-4 字滑动窗口 token：
- 2-gram：化学, 学品, 品分, 分类, 类存, 存储
- 3-gram：化学品, 学品分, 品分类, 分类存, 类存储
- 4-gram：化学品分, 学品分类, 品分类存, 分类存储

这些 token 应能匹配到包含"化学品分类存储"文本的 chunk。**但**：

- Markdown chunk 的 chunk_text 中可能包含"化学品分类存储"，但该 chunk 可能与其他 chunk（来自同一篇文章）共享相似的 token 命中
- 多个 chunk 都有 partial match 时，得分分散
- XLSX article 的标题"化学品存储分级表"包含"化学品"、"存储"、"分级"、"分类"等重叠 token，得分可能更高

### 4.4 根因判断

Markdown chunk 的 LIKE token 命中被 XLSX article 的更强信号压制。具体原因：
- XLSX article 的 title（"化学品存储分级表"）是一个连续的标题字符串，在 FTS + LIKE 评分中具有高密度匹配
- Markdown chunk 的 chunk_text 虽然包含目标短语，但分散在大段文本中，token 密度低
- article_chunk_fts 通道的 article title LIKE 权重（1.5）低于 chunk_text（3.0），但 XLSX article 走的是 `fts`（文章级）通道，权重不同

**这不是检索未召回**（chunk 应该被 article_chunk_fts 检索到），**不是身份折叠**（chunkIdentity 已保留），而是 cross-channel RRF 融合中，XLSX article（来自 fts 通道）的得分压制了 markdown chunk（来自 article_chunk_fts 通道）。

---

## 5. FS4b "B级" — 失败分析

### 5.1 复现方式与结果

**来源**：`full_public_eval_after_fg1_raw_query_match_gate_report.md`（2026-06-04 agentD runtime gate）

搜索词：`B级`

结果：**count=0，无任何结果**

### 5.2 失败类型：**检索未召回**

这是最明确的完全检索失败——所有通道均未返回任何命中。

### 5.3 源码链路定位

**根因断点**：`QueryTokenExtractor.extract()` — 对输入 `"B级"` 提取到 **0 个 token**。

**机制详解**（`QueryTokenExtractor.java`）：

| 模式 | 正则 | 最小长度 | "B级" 匹配结果 |
|------|------|:---:|---|
| `ASCII_TOKEN_PATTERN` | `[A-Za-z0-9=_-]{2,}` | 2 | **否** — "B" 只有 1 个 Latin 字符 |
| `HAN_TEXT_PATTERN` | `[\p{IsHan}]{2,}` | 2 | **否** — "级" 只有 1 个 Han 字符 |
| `NUMBER_TOKEN_PATTERN` | 纯数字 | 1 | **否** — 无数字 |

- `"B"` 单独不满足 ASCII 模式的 2 字符最小长度
- `"级"` 单独不满足 Han 模式的 2 字符最小长度
- `"B级"` 作为**混合脚本**（Latin + CJK），无法被任何单字符类模式跨边界匹配

**级联影响**：

1. `ArticleChunkFtsSearchService.search()` — 检测到 `queryTokens.isEmpty()` → 直接返回 `List.of()`
2. `SourceChunkFtsSearchService.search()` — 同上
3. `FactCardTerminalUnitFtsSearchService.search()` — 同上
4. `FactCardFtsSearchService` — 同上
5. `SourceSearchService` — 同上

唯一可能产生结果的通道是 `FtsSearchService`（文章级 FTS），因为它**不依赖 QueryTokenExtractor**，直接将原始问题传给 `plainto_tsquery()`。但：
- `plainto_tsquery('simple', 'B级')` 产生 `'b' & '级'`（AND 语义）
- `'b'` 在英文文本中极其常见，AND 匹配非常脆弱
- 实际返回依赖 `articles.search_tsv` 中同时包含 `'b'` 和 `'级'` 两个 token
- 加上 `review_status='passed'` AND `lifecycle='ACTIVE'` 过滤

### 5.4 根因判断

**`QueryTokenExtractor` 无法处理长度 2 的混合脚本 token（如 Latin+CJK 组合）**。这导致所有依赖 LIKE token 的通道（article_chunk_fts、source_chunk_fts、fact_card_terminal_fts、refkey、source）全部返回空。

这不是数据缺失（XLSX 中明确包含"B级"数据）、不是编译问题（fact_card_terminal_units 中存在相关记录）、不是 RRF 排序问题。是**检索入口层的 token 提取空白**导致整个检索管道短路。

---

## 6. 综合归因矩阵

| 失败项 | 失败类型 | 断点位置 | 是否检索未召回 | 是否需要改 QueryTokenExtractor | 是否需要改 chunk 层 |
|:---|:---|:---|:---:|:---:|:---:|
| S2 "下一步计划" | **展示标题问题** | `ChunkHitIdentitySupport.extractSectionAnchor()` — 只扫描首行标题 | 否（已召回） | 否 | **是**（chunk 切分 或 anchor 提取） |
| FS2 "化学品分类存储" | **RRF 排序低** | RRF 融合中 XLSX article 信号压制 markdown chunk | 否（已召回但排序低） | 否 | 否（通道权重问题） |
| FS4b "B级" | **检索未召回** | `QueryTokenExtractor.extract()` — 混合脚本 token 无法提取 | **是**（0 结果） | **是** | 否 |

---

## 7. 最小通用修复建议

### 最高优先级根因：FS4b — QueryTokenExtractor 无法提取混合脚本短 token

**选择理由**：
1. 导致**完全搜索失败**（0 结果），而非排序或展示问题
2. 根因最清晰、修复面最小（单个方法）
3. 影响面最广：任何包含"单 Latin 字母 + 单 CJK 字符"的搜索词（如"B级"、"A类"、"C区"）都会静默返回空
4. S2 和 FS2 的修复涉及 chunk 切分/权重调优，改动面更大且需要更谨慎的回归验证

### 通用修复点

**文件**：`src/main/java/com/xbk/lattice/query/service/QueryTokenExtractor.java`

**修改方法**：`extract(String question)` — 在现有字符类 token 提取之后，增加对**混合脚本 token** 的处理。

**通用规则**：当原始问题（或问题片段）满足以下条件时，将整个片段作为附加 token：
- 长度 >= 2
- 包含至少一个 Latin/ASCII 字母或数字
- 包含至少一个 CJK 字符
- 不是纯标点/空格

**为什么不是 case 特判**：

| 维度 | 说明 |
|------|------|
| 不绑定具体业务词 | "B级"不会被硬编码；规则是"任何 Latin+CJK 混合 token" |
| 不绑定文档/文件名 | 不引用 chemical-storage-grading.xlsx 或任何具体资料 |
| 不绑定题号 | 不引用 FS4b 或任何 eval case id |
| 通用脚本检测 | 使用 Unicode block 判断（`Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS`），与现有 `HAN_TEXT_PATTERN` 一致 |
| 对所有混合脚本查询一视同仁 | "A类"、"C区"、"B级"、"D档"等任意组合均生效 |

### 实现概要（不写代码，只描述）

在 `extract()` 方法中，现有的 token 提取流程后，增加一个 fallback：

1. 将原始 question 按空白/标点拆分为 segment
2. 对每个 segment：如果长度 >= 2，且既包含 `[A-Za-z0-9]` 又包含 `[\p{IsHan}]`，且未被已有 token 覆盖
3. 将其作为附加 token 加入结果集
4. 该 token 在 `LexicalSearchTokenBudget` 中的评分：长度 2 的混合 token 得基础分（例如 `token.length() >= 2 ? 100 + token.length() : 0`）

当前 `LexicalSearchTokenBudget` 第 88-106 行对长度 2 的非 ASCII、非 CJK token 已给 0 分。需要确认混合 token 的评分逻辑也能给正分——这可能需要同步调整 `LexicalSearchTokenBudget` 的评分规则，或者混合 token 走 `isCjkToken()` 判断（因为包含 CJK 字符）。

### 保护场景

| 场景 | 行为 |
|------|------|
| "精密仪器"（纯 CJK, 4 字） | 不受影响——现有 CJK 滑动窗口已覆盖 |
| "healthz"（纯 ASCII, 7 字） | 不受影响——现有 ASCII 模式已覆盖 |
| "B级"（Latin+CJK, 2 字） | **修复后新增**——作为混合 token 提取 |
| "A类化学品"（Latin+CJK, 5 字） | **修复后新增**——整个片段作为 token |
| "v2.3.1"（版本号） | 不受影响——现有 path/config 模式已覆盖 |
| "B"（单 Latin 字母） | 不受影响——长度 < 2 |
| "级"（单 CJK 字符） | 不受影响——长度 < 2 |

---

## 8. S2 / FS2 的后续处理建议

S2 和 FS2 不属于本轮最高优先级修复范围，但给出方向性建议供后续独立评估：

### S2 "下一步计划"

**方向 A**（编译期）：改进 chunk 切分策略，在 Markdown 的每个 `##` 或 `###` 标题处断开 chunk，使"下一步计划"成为独立 chunk 的首行。

**方向 B**（查询期）：改进 `extractSectionAnchor()`，不只取第一行标题，而是收集 chunk 中所有标题，匹配 query token 最高的作为 display title。

两个方向都需要独立评估收益/风险比，不在本轮处理。

### FS2 "化学品分类存储"

**可能方向**：在 article_chunk_fts 通道的 SQL 评分中，对 chunk_text 中匹配到标题级内容（如首行的 `# ` 标题）给予额外加分，使标题匹配的 chunk 在评分上优先于纯内容匹配的文章。

这也需要独立评估，不在本轮处理。

---

## 9. 下一轮交给 agentA 的强约束提示词

```text
你现在是 agentA（代码执行 Agent）。

本轮目标：
修复 QueryTokenExtractor 无法提取混合脚本短 token（如 Latin+CJK 组合）的问题，
解决 FS4b "B级" 搜索返回 0 结果的根本缺陷。

修改范围：
- 只修改 src/main/java/com/xbk/lattice/query/service/QueryTokenExtractor.java
- 只修改 extract() 方法（或新增一个 private helper 方法）
- 可能需要同步调整 src/main/java/com/xbk/lattice/infra/persistence/LexicalSearchTokenBudget.java
  的评分逻辑（确保混合脚本 token 获得正分）

修改要求：
1. 在 extract() 中，现有 token 提取逻辑之后，增加混合脚本 token 的 fallback 提取
2. 通用规则：将原始 question 拆分为 segment（按空白/标点），对每个长度 >= 2 且
   同时包含 [A-Za-z0-9] 和 [\p{IsHan}] 的 segment，作为附加 token
3. 使用 UnicodeBlock 判断而非硬编码字符范围
4. 在 LexicalSearchTokenBudget 中，确保包含 CJK 字符的混合 token 能获得正分
   （例如走 isCjkToken 分支或新增混合 token 评分逻辑）

禁止事项：
- 禁止修改 AnswerFallbackConclusionBuilder
- 禁止修改 Materializer / Enricher / FtsSearchService / RrfFusionService
- 禁止修改 article_chunk / source_chunk 相关代码
- 禁止修改 schema.sql / Mapper XML
- 禁止修改 tests、scripts、prompt、config、题集
- 禁止写入 "B级"、"FS4b"、"chemical-storage-grading" 等样例字符串
- 禁止修改现有 ASCII_TOKEN_PATTERN 或 HAN_TEXT_PATTERN 的最小长度
- 禁止提交 commit

通用性验证：
- "B级"、"A类"、"C区" 等任意 Latin+CJK 组合均应被提取为 token
- "精密仪器"（纯 CJK）行为不变
- "healthz"（纯 ASCII）行为不变
- 现有测试（QueryTokenExtractorTests）不应有回归

redline / mvn test 要求：
- redline BLOCKER=0
- mvn test 全量通过

验证计划（交给 agentD）：
1. 编译后搜索 "B级" → 应返回 > 0 条结果
2. 搜索 "A类"、"C区" 等类似模式 → 应返回 > 0 条结果
3. 搜索 "精密仪器" → 行为不变（仍有结果）
4. 搜索 "化学品分类存储" → 行为不变（仍有结果）
5. 完整 Public Eval 2 搜索回归（FS1-FS4）
```

---

## 10. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt/config/schema/scripts
- [x] 未修改题集/redline allowlist/special_cases_report.md
- [x] 未提交 commit
- [x] 未跑 mvn test / redline / baseline
- [x] 未读取 hidden eval
- [x] 未清库/重建/重启服务
- [x] 所有结论基于源码只读分析 + 已有报告交叉验证 + 只读 SQL
- [x] 修复建议中无 case 特判——规则基于 Unicode 脚本检测
- [x] 未将具体题面、答案、文件名写入生产代码建议
- [x] 未建议通过 query fallback / AnswerFallbackConclusionBuilder / prompt 模板硬补搜索结果
