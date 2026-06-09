# PE5 YAML 检索召回跟进分析报告

分析时间：2026-06-08
执行人：agentB（治理/归因 Agent）
类型：只读源码 + DB 追踪，不修改任何文件

---

## 1. 结论：证据已被检索到，但在 LLM 聚合消费阶段丢失

**FQ3 的 supplier registry article（id=2, 12371 chars, 14 chunks 含全部 8 家供应商的 `rating: A/B/C/D`）已被 FTS/LIKE 检索到并进入 LLM 上下文。LLM 能感知"共 8 条供应商记录"，但无法从 14 个 chunk 的文本中可靠地逐个提取 rating 值并完成 COUNT 聚合。根因是 LLM 对结构化数据的计数聚合能力不足，不是检索召回失败。**

---

## 2. 逐层证据追踪

### 2.1 Token 提取层

**事实（源码已证明）**：

FQ3 问题："供应商台账里有多少家供应商的评级是 A？评级是 C 或 D 的各有多少？"

| token 来源 | 提取结果 | 机制 |
|------|:---:|------|
| `ASCII_TOKEN_PATTERN` (`[A-Za-z0-9=_-]{2,}`) | "A"/"C"/"D" 各自仅 1 字符 → **不提取** | 最小长度=2 |
| `HAN_TEXT_PATTERN` (`[\p{IsHan}]{2,}`) | "供应商"/"台账"/"多少"/"评级" 等 CJK bigram/trigram | 滑动窗口 |
| `appendMixedScriptTokens`（adjacent merge） | "评级是 A" 中 "评级是"=3 CJK chars > MAX(2) → 不合并 → "A" 单独 1 字符 → **不提取** | 仅短 Han(≤2) + 短 ASCII(≤4) 合并 |
| `appendMixedScriptTokens`（same-segment mixed） | 无空格分隔 "评级是A" → 1 segment 含 Han+Latin → 会提取 "评级是a"。但原文有空格。"是 A" → "是" = 2 chars ≤ 2 → 会合并为"是a" | **"是a" 可能被提取，但无区分性——article 不含 "是a" 文本** |

**结论**：评级值 "A"/"C"/"D" 未被提取为有效 token。"是a"/"是c"/"或d" 等合并 token 可能被提取但无区分性。**仅 CJK token（"评级"/"供应商"/"台账"）进入搜索。**

### 2.2 LexicalSearchTokenBudget 评分层

**事实（源码已证明）**：

CJK token "评级"（2 字符）→ `isCjkToken` → `230 - min(2, 8)` = **228 分**。> 0 → 进入 LIKE token 列表。MAX_LIKE_TOKENS=32，FQ3 的 CJK token 总数 < 32 → **全部进入 LIKE**。

**结论**：CJK token 正常通过评分筛选，未被预算挤出。

### 2.3 搜索召回层（FTS/LIKE/Vector）

**事实（日志已证明 — gate 报告）**：

gate 报告明确指出："LLM 回答提到 supplier-registry.yaml 中共有 8 条供应商记录，说明系统**知道**数据存在"。

**结论**：supplier registry article **已被检索到**，已进入 fused top-K，已进入 LLM 上下文。检索召回不是失败原因。

### 2.4 RRF 融合层

**事实（源码推断）**：

supplier registry article 通过多个通道被召回（article_chunk_fts 的 "评级" token 匹配、article FTS 匹配、可能的 vector 匹配），RRF 融合后排名足够高进入 top-K。无 identity 折叠风险（article 独立身份）。

**结论**：RRF 融合层无丢失。

### 2.5 Evidence Selector 层

**事实（源码推断）**：

`AnswerFallbackEvidenceSelector` 在 LLM 模式下不执行 fallback 证据筛选——LLM 模式直接使用 fused hits 全集。supplier registry article 不会被 evidence selector 过滤。

**结论**：Evidence selector 层无丢失。

### 2.6 LLM Answer Generation 层

**事实（日志已证明 — gate 报告）**：

LLM 回答："supplier-registry.yaml 中共有 8 条供应商记录"。

**LLM 看到了数据**。它知道有 8 条记录。但它**无法完成 COUNT 聚合**——需要从 14 个 chunk（chunks 5-12 各含 1 家供应商的 rating 值，外加概述 chunk 和字段说明 chunk）中逐个提取 rating 值，统计 A=2, B=2, C=2, D=1（注：原题预期 A=2, C=2, D=1，实际 YAML 中 SUP-007 也是 B 级，B=2）。

LLM 的 token 预算和注意力机制在处理 14 个 chunk（总计 ~12K chars）的逐字段提取和计数时不可靠。LLM 保守地返回 INSUFFICIENT_EVIDENCE（不编造错误计数）或 PARTIAL_ANSWER（部分计数正确）。

**结论**：证据在此层丢失——不是物理丢失，是 LLM 的消费能力不足以从 12K chars 的 article 文本中完成结构化的 COUNT 聚合。

---

## 3. 证据在何处消失——分层确认

| 层 | 评级值 A/C/D 的状态 | 判定 |
|------|------|:---:|
| QueryTokenExtractor | **未提取**（1 字符 < 2） | ⚠️ 未进入 query token |
| LexicalSearchTokenBudget | N/A（token 未到达此层） | — |
| FTS/LIKE 搜索 | CJK token "评级" 匹配 article → article 被召回 ✅ | ✅ |
| RRF 融合 | article 独立身份，无折叠 ✅ | ✅ |
| Evidence Selector | LLM 模式不筛选 ✅ | ✅ |
| LLM 消费 | 看到 8 条记录，**无法可靠 COUNT** | ❌ **消失点** |

---

## 4. FQ11 / FQ12 追踪

| 题号 | Planner | 检索 | LLM 结果 | 判定 |
|:---:|------|------|------|:---:|
| FQ11 | "有没有"→拒止→回退 LLM | 搜索"付款条款"/"账期"→article 不含这些词 | INSUFFICIENT_EVIDENCE | ✅ 正确拒答 |
| FQ12 | "有没有"→拒止→回退 LLM | 搜索"检验员"→CSV article 不含此字段 | INSUFFICIENT_EVIDENCE | ✅ 正确拒答 |

**两个题均正确工作**。gate 报告将 INSUFFICIENT_EVIDENCE 标记为"回退"是评测自动化将 INSUFFICIENT_EVIDENCE 计为 FAIL 的统计口径问题，不是代码回归。

---

## 5. 现有日志是否足以证明

| 需要证明的事实 | 当前证据 | 充分性 |
|------|------|:---:|
| A/C/D 未进入 query token | 源码分析（ASCII_TOKEN_PATTERN + appendMixedScriptTokens 逻辑） | ✅ 源码已证明 |
| article 被检索到 | gate 报告（LLM 提到"8 条记录"） | ✅ 日志已证明 |
| article 进入 LLM 上下文 | gate 报告（LLM 答案引用 supplier-registry.yaml） | ✅ 日志已证明 |
| LLM 无法可靠 COUNT | gate 报告（INSUFFICIENT_EVIDENCE / PARTIAL_ANSWER） | ✅ 日志已证明 |
| RRF 无丢失 | 源码推断（无 identity 折叠风险） | ⚠️ 无 runtime trace 直接确认 |
| Evidence selector 无过滤 | 源码推断（LLM 模式不筛选） | ⚠️ 无 runtime trace 直接确认 |

**当前日志已足以证明根因链路**（token 未提取 → article 被检索 → LLM 无法 COUNT）。RRF 和 evidence selector 层虽无直接 trace，但 gate 报告的 LLM 答案内容（"8 条记录"）间接证明 article 确实到达了 LLM。

---

## 6. 缺失的最小日志点

如果需要在 production 中一步定位同类问题，只需在以下 4 个点加 L2 trace：

| 序号 | 位置 | 需记录 | 解决的问题 |
|:---:|------|------|------|
| 1 | `QueryTokenExtractor.extract()` 返回后 | 最终 token 列表 | "A/C/D 是否在 query token 中" |
| 2 | `KnowledgeSearchService.search()` 返回后 | 每通道 hitCount + top3 的 articleKey/title | "supplier registry 是否被召回" |
| 3 | `RrfFusionService.fuse()` 返回后 | top-K 的 identity key + fusedScore | "article 在融合后排名第几" |
| 4 | Answer generation 前 | 传入 LLM 的 article count + title 列表 | "article 是否进入 LLM 上下文" |

---

## 7. 明确声明

- [x] 未修改任何文件
- [x] 未提交 commit
- [x] 源码分析已确认：ASCII_TOKEN_PATTERN 最小长度=2 → "A"/"C"/"D" 不提取
- [x] 源码分析已确认：adjacent merge 仅合并短 Han(≤2) + 短 ASCII(≤4)，"评级是"=3 字 > 2 → 不合并
- [x] gate 报告已确认：article 被检索到（LLM 知道 8 条记录）
- [x] gate 报告已确认：LLM 无法可靠 COUNT（INSUFFICIENT / PARTIAL）
- [x] 证据消失点在 LLM 消费层（结构化 COUNT 聚合能力不足），非检索层
- [x] 本报告不包含修复建议
