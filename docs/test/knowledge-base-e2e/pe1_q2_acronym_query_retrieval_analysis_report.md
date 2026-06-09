# PE1 Q2 缩略词查询检索失败 — 只读归因报告

分析时间：2026-06-06
执行人：agentB（治理/链路分析 Agent）
类型：只读根因归因，无代码修改

---

## 1. 本轮目标

只读归因 PE1 Q2 原始缩略词查询 `"三类probe（SL/TL/IM）的职责分别是什么？"` 为什么检索不到已入库的 PDF 文章，而全名查询 `"Situation Lead、Technical Lead 和 Incident Manager 的职责分别是什么？"` 可以 PASS（cov=1.0）。

---

## 2. 当前数据状态

| 项 | 值 |
|----|-----|
| 数据库 | `ai-rag-knowledge.lattice` — PE1 runtime gate 数据 |
| PDF article (id=2) | `incident response reference lite`，content_len=3969 |
| review_status / lifecycle | `passed` / `ACTIVE` |
| article 含 "Situation Lead" | **是** ✅ |
| article 含 "Technical Lead" | **是** ✅ |
| article 含 "SL"（独立缩略词） | **否** ❌ |
| article 含 "TL"（独立缩略词） | **否** ❌ |
| article 含 "IM"（独立缩略词） | **否** ❌ |

**关键事实**：Writer 生成的文章只包含角色全名（"Situation Lead"、"Technical Lead"），不包含缩略词形式（"SL"、"TL"、"IM"）。源 PDF 本身也可能只含全名不含缩略词。

---

## 3. 原始 Q2 查询结果

**来源**：`pe1_q2_lightweight_small_doc_runtime_gate_report.md`（agentD, 2026-06-06）

| 查询 | outcome | mode | cov | 判定 |
|------|---------|------|:---:|:---:|
| 原始 Q2: `三类probe（SL/TL/IM）的职责分别是什么？` | NO_RELEVANT_KNOWLEDGE | RULE_BASED | — | **FAIL** |
| 全名查询: `Situation Lead、Technical Lead 和 Incident Manager 的职责分别是什么？` | PARTIAL_ANSWER | LLM | **1.0** | **PASS** |

全名查询可正常召回 PDF article 并生成正确答案。缩略词查询 fused hits 为空，直接返回 NO_RELEVANT_KNOWLEDGE。

---

## 4. Token Extraction 分析

### 4.1 缩略词查询的 Token 提取

`QueryTokenExtractor.extract("三类probe（SL/TL/IM）的职责分别是什么？")`

| 模式 | 正则 | 匹配到的 token | 归一化后 |
|------|------|---------------|----------|
| `ASCII_TOKEN_PATTERN` | `[A-Za-z0-9=_-]{2,}` | `probe`、`SL`、`TL`、`IM` | `probe`、`sl`、`tl`、`im` |
| `HAN_TEXT_PATTERN` | `[\p{IsHan}]{2,}` | `三类`、`职责`、`分别是`、`什么` | — |

**ASCII 模式正确提取了 "SL"、"TL"、"IM" 作为 token**（2 字符大写 Latin 满足最小长度 2）。Token extraction 本身不是根因。

### 4.2 全名查询的 Token 提取

`QueryTokenExtractor.extract("Situation Lead、Technical Lead 和 Incident Manager 的职责分别是什么？")`

| 模式 | 匹配到的 token | 归一化后 |
|------|---------------|----------|
| `ASCII_TOKEN_PATTERN` | `Situation`、`Lead`、`Technical`、`Lead`（去重）、`Incident`、`Manager` | `situation`、`lead`、`technical`、`incident`、`manager` |
| `HAN_TEXT_PATTERN` | `职责`、`分别是`、`什么` | — |

全名查询产生 `situation`、`lead` 等 token，与 article 内容中的 "Situation Lead" 成功匹配。

---

## 5. FTS / LIKE 匹配分析

### 5.1 FTS 匹配

`plainto_tsquery('simple', '三类probe（SL/TL/IM）的职责分别是什么？')` 产生 token 集含 `'sl'`、`'tl'`、`'im'`。

`to_tsvector('simple', article_content)` 对 article content（含 "Situation Lead"、"Technical Lead"）产生 token：
- `'situation'`、`'lead'`、`'technical'` 等
- **不产生** `'sl'`、`'tl'`、`'im'`——`simple` 字典不做首字母缩写

FTS 无法匹配。

### 5.2 LIKE 匹配

SQL `searchLexical` 对每个 LIKE token 执行 `lower(column) like '%token%'`：

| LIKE token | article title (`incident response reference lite`) | article content (`Situation Lead...`) |
|:---:|------|------|
| `%sl%` | "incident respon**s**e reference **l**ite" → **不匹配** | 全文检查：含 "sl" 子串？→ **否**（"Situation Lead" 中 "s" 和 "l" 之间有空格） |
| `%tl%` | "incident response reference li**t**e" → **不匹配**（前面没有 "t" 紧邻 "l"） | **否** |
| `%im%` | "incident response reference l**i**te" → **不匹配** | **否** |

**LIKE 同样无法匹配。** `%sl%` 不会匹配 "Situation Lead"，因为两个词之间有空格，"s" 和 "l" 不在同一 token 中连续出现。

### 5.3 向量匹配

向量检索依赖 embedding 语义相似度。"SL" 作为 2 字符缩略词，embedding 向量与 "Situation Lead" 的语义距离可能较大。且向量通道权重和 `RETRIEVAL_CANDIDATE_LIMIT=16` 的限制使单 token 命中难以进入 top-K。

---

## 6. 根本原因

**查询使用缩略词（SL/TL/IM），但索引内容只含全名（Situation Lead/Technical Lead/Messenger）。两者之间存在 token-level 语义鸿沟，FTS、LIKE、向量三条路径均无法桥接。**

这不是 token extraction 缺陷——"SL"、"TL"、"IM" 被正确提取为 2 字符 ASCII token。这不是 retrieval 召回失败——索引用全名 token 正确索引了内容。问题是**查询 token 空间与索引 token 空间之间的映射缺失**。

### 为什么全名查询能匹配

全名查询的 token（`situation`、`lead`、`technical`）与 article 内容中的 token 完全一致 → FTS 匹配 → LIKE 加分 → article 被召回。

---

## 7. 搜索对比实验（基于源码分析，非运行时）

| 搜索词 | 预期能否命中 PDF article | 原因 |
|--------|:---:|------|
| `SL` | **否** | 2 字符 token，article 无 "sl" 子串 |
| `TL` | **否** | 同上 |
| `IM` | **否** | 同上 |
| `SL TL IM` | **否** | 同上（OR 语义仍无法匹配） |
| `Situation Lead` | **是** | article 含 "Situation Lead"，token 匹配 |
| `Technical Lead` | **是** | article 含 "Technical Lead" |
| `Incident Manager` | 部分 | article 不含 "Incident Manager"（使用 "Messenger" 代替） |

---

## 8. 主失败类型归类

### **query rewrite / synonym expansion 缺失**

查询中的缩略词（SL/TL/IM）没有被展开为对应的全名（Situation Lead/Technical Lead），索引中的全名也没有被反查为缩略词。两者之间缺少通用映射机制。

**排除的类型**：

| 候选类型 | 判定 | 理由 |
|----------|:---:|------|
| 检索未召回 | **排除为独立根因** | 检索层正确执行，只是 token 空间不匹配 |
| token extraction 缺失 | **排除** | "SL"/"TL"/"IM" 被正确提取 |
| 编译抽取缺失 | **排除** | 全名查询已 PASS（Writer 修复后） |
| rerank 排序低 | **排除** | fused hits 为空，非排序问题 |
| 证据已召回但回答漏点 | **排除** | 检索层未召回任何证据 |
| 评测/预期口径问题 | **排除** | 缩略词查询是合理的用户输入形式 |

---

## 9. 根因判断总结

```
查询 "SL/TL/IM"
  → QueryTokenExtractor 提取: "sl", "tl", "im" ✅
    → FTS: article tsvector 无 'sl'/'tl'/'im' token ❌
    → LIKE: article 无 "sl"/"tl"/"im" 连续子串 ❌
    → Vector: 短缩略词语义距离大 ❌
  → fused hits = 0 → NO_RELEVANT_KNOWLEDGE

查询 "Situation Lead"
  → QueryTokenExtractor 提取: "situation", "lead" ✅
    → FTS: article tsvector 含 'situation'/'lead' ✅
    → LIKE: article content 含 "Situation Lead" ✅
  → article 被召回 → LLM 答案 → PASS
```

---

## 10. 为什么不能写 Q2/SL/TL/IM 特判

- 生产代码中写入 `"SL" → "Situation Lead"` 映射 → 违反 Query 红线（具体业务词硬编码）
- 为 `incident-response-reference-lite.pdf` 写文件特判 → 红线禁止
- 为 Q2 写题号特判 → 红线禁止
- 为 "probe 角色" 写答案模板 → 红线禁止
- 为 "SL/TL/IM" 写正则白名单 → 红线禁止

任何通用修复必须对所有文档、所有缩略词一视同仁。

---

## 11. 下一步最小动作建议

### 唯一推荐：**Writer prompt 增强——在文章内容中保留源文档的缩略词形式**

### 理由

1. **最接近根因**：问题的根源是 article 内容只含全名不含缩略词。如果在 compile 期保留缩略词，query 期的所有检索通道（FTS/LIKE/Vector）都自动受益。
2. **通用性最强**：适用于所有含缩略词的文档（不限于 PDF、不限于 SL/TL/IM）
3. **修改面最小**：仅调整 Writer system prompt（`src/main/resources/prompts/compiler/writer-*.md`），不改 Java 代码
4. **无副作用**：文章内容增加缩略词形式不会影响现有 FTS/LIKE 匹配，全名查询继续工作
5. **不引入新 token/code**：不新增 query expansion 逻辑、不新增 synonym 配置维护、不新增 acronym extraction 算法

### 具体方向

在 Writer 的 system prompt 中增加一条通用规则（不是针对 SL/TL/IM 的特判）：

> 当源文档中出现多词术语（如 "Situation Lead"），且源文档中同时提供了其缩略词形式（如 "SL"），必须在文章正文中首次提及时以 "全名（缩略词）" 的格式呈现。不得自行编造源文档中未出现的缩略词。

**注意**：这个规则的前提是源文档（PDF）中存在缩略词。如果源 PDF 也只含全名，Writer 无法凭空生成缩略词。此时需要确认源 PDF 的内容——如果源 PDF 不含缩略词，则需要更上层的解决方案（如 compile 期 acronym extraction 或 query 期 config-based synonym）。

### 如果源 PDF 不含缩略词

备选方案（按通用性排序）：

| 方案 | 说明 | 通用性 | 风险 |
|------|------|:---:|:---:|
| 编译期从多词术语生成首字母缩略词 alias | 在 Materializer 或 Writer 中，对多词 title/role 生成大写首字母缩写作为额外 alias | **高** | 可能产生误缩写（如 "High" → "H"、"Low" → "L"） |
| 查询期配置化 acronym expansion | 在 `config/synonyms.yaml` 中维护缩略词→全名映射 | **中** | 需要人工维护映射表，不能自动发现新缩略词 |
| 查询期通用 acronym token 展开 | 对 2-3 字符全大写 token，用首字母匹配 near 全大写单词 | 中 | 算法复杂，假阳性风险高 |

### 不推荐的方向

- ❌ 在 query 主链硬编码 `"SL" → "Situation Lead"` — case 特判
- ❌ 在 query rewrite 中加正则匹配缩略词模式 — 过度工程，维护成本高
- ❌ 降低 ASCII token 最小长度到 1 — 会导致大量单字母 token 噪音

---

## 12. agentA 修复提示词草案

```text
你现在是 agentA（代码执行 Agent）。

本轮目标：
确认 PE1 Q2 源 PDF 中是否包含缩略词（SL/TL/IM）与全名（Situation Lead/Technical Lead）的配对。
如果包含，修改 Writer prompt 使其在文章正文中保留缩略词形式。

背景：
Writer 生成的文章只含全名（"Situation Lead"、"Technical Lead"），不含缩略词（"SL"、"TL"）。
缩略词查询无法通过 FTS/LIKE 匹配到文章内容。

修改范围（取决于 PDF 内容检查结果）：

情况 A（PDF 含缩略词）：
- 只修改 src/main/resources/prompts/compiler/writer-text.md 和 writer-image.md
- 在 prompt 中增加通用规则（非 SL/TL/IM 特判）

情况 B（PDF 不含缩略词）：
- 不做 Writer prompt 修改
- 输出报告说明："源文档不含缩略词，Writer 无法凭空生成，建议后续评估编译期 acronym extraction 或查询期 synonym config"
- 不做代码修改

检查方法：
- 读取 docs/test/knowledge-base-e2e/sources/03_pdf/incident-response-reference-lite.pdf
- 用 pdftotext 或等价方法提取文本
- 搜索 "SL"、"TL"、"IM" 是否为独立 token（非代码片段、非 URL 一部分）

Prompt 规则草案（情况 A）：
当源文档中出现多词命名的术语（如角色名、配置项名），且源文档同时以缩略词形式
引用该术语时，在文章正文中首次提及时使用 "全名（缩略词）" 格式。
不得自行编造源文档中未出现的缩略词。

禁止事项：
- 禁止在 prompt 中写入 SL、TL、IM、Situation Lead、Technical Lead 等具体业务词
- 禁止修改 query/retrieval/answer 主链
- 禁止新增 Java 代码
- 禁止提交 commit

验证计划（交给 agentD）：
1. PE1 清库重编译
2. Q2 缩略词查询 → 应返回 PASS（非 NO_RELEVANT_KNOWLEDGE）
3. Q2 全名查询 → 保持 PASS
4. 其他 PE1 题目无回归
```

---

## 13. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt / config / schema / scripts
- [x] 未修改题集 / redline allowlist
- [x] 未提交 commit
- [x] 未清库 / 重建 / 导入资料
- [x] 未读取 hidden eval
- [x] 服务未运行，检索对比实验基于源码分析和数据库内容推断——已标注
- [x] article content 已在 DB 中确认为只含全名不含缩略词
- [x] 推荐修复为通用 Writer prompt 规则或编译期 acronym alias，不包含 case 特判
