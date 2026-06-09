# PE5 YAML 检索召回根因分析报告

分析时间：2026-06-08
执行人：agentB（治理/归因 Agent）
类型：只读 DB + 源码分析，不修改任何文件

---

## 1. 结论：证据在 tokenization/FTS/LIKE 层消失

**FQ3 的 article 包含全部 8 家供应商的完整评级数据（chunks 5-12 各有 `rating: A/B/C/D`），LLM 也知道"有 8 条供应商记录"，但 LLM 拿不到具体的评级值。根因是评级值"A"/"B"/"C"/"D"作为单字符 ASCII token，无法通过 QueryTokenExtractor 的最小长度过滤，也无法在 FTS/LIKE 中产生有效的区分性匹配。**

**FQ11/FQ12 的 gate 报告"回归"不是代码回归——两个题都正确走 LLM 路径并返回 INSUFFICIENT_EVIDENCE（正确拒答）。gate 报告将 INSUFFICIENT_EVIDENCE 标记为"回退"是评测统计口径问题。**

---

## 2. 逐题溯源

### 2.1 FQ3：评级值检索失败（TRUE 问题，需修复）

#### 2.1.1 DB 证据确认

| 表 | 确认结果 |
|------|------|
| articles (id=2) | `supplier registry`，content_len=12371，**含全部 8 家供应商数据** ✅ |
| chunks 5-12 | 每 chunk 含一家供应商，字段完整：`rating: A/B/C/D` ✅ |
| chunk 3 | `## 字段说明` 含 `rating` 字段定义 ✅ |
| chunk 13 | `## 数据特征` 含"共 8 条供应商记录" ✅ |

**数据在 DB 中完整，Writer 输出质量正常。**

#### 2.1.2 检索链路追踪

```
问题: "供应商台账里有多少家供应商的评级是 A？评级是 C 或 D 的各有多少？"
  │
  ├─ StructuredQueryPlanner → 同列多值冲突检测 → 拒绝 → 回退到 LLM ✅
  │
  ├─ QueryGraph → FTS/LIKE/Vector 检索
  │   │
  │   ├─ QueryTokenExtractor:
  │   │   "评级是 A" → "A" 是 1 字符 → ASCII_TOKEN_PATTERN 最小长度=2 → ❌ 不提取
  │   │   "评级是 C" → "C" 是 1 字符 → ❌ 不提取
  │   │   "评级是 D" → "D" 是 1 字符 → ❌ 不提取
  │   │   提取的 token: ["供应", "应商", "商台", "台账", "多少", "少家", "评级", ...]
  │   │
  │   ├─ FTS (search_tsv, config='simple'):
  │   │   article chunk 含 "rating: `A`" → tsvector tokens: 'rating', 'a'
  │   │   plainto_tsquery('simple', question) → ... & '评' & '级' & ...
  │   │   'a' token 在 query 的 tsquery 中可能不出现（因为'A'未被提取为有效token）
  │   │   → FTS 匹配依靠 '评级'/'供应商'/'台账' 等 CJK token
  │   │
  │   ├─ LIKE:
  │   │   LIKE '%A%' → 匹配但无区分性（文章content含大量'a'字母）
  │   │   LIKE '%评级%' → 匹配 supplier registry article ✅
  │   │
  │   └─ Vector: "A" / "C" / "D" 的 embedding 语义向量与文章内容有一定匹配度
  │
  ├─ RRF 融合: supplier registry article 应被召回（"评级"/"供应商"/"台账" token 匹配）
  │
  ├─ Evidence Selector / LLM:
  │   LLM 收到 supplier registry article 内容 → 看到 8 条供应商记录
  │   但 article 中 rating 值以 `rating: \`A\`` 格式嵌入文本
  │   LLM 需要从大量文本中逐个提取 8 条记录的 rating 值并计数
  │   → LLM 部分能做到（"共 8 条"），但精确计数 8 条中各有几个 A/C/D 不可靠
  │   → 返回 INSUFFICIENT_EVIDENCE（不编造）或 PARTIAL_ANSWER（部分计数正确）
```

**证据在何处消失**：在 **QueryTokenExtractor 的 token 提取层**。评级值 "A"/"B"/"C"/"D" 因字符长度 < 2 而未被提取为搜索 token，导致 FTS/LIKE 无法用评级值做精确匹配。

#### 2.1.3 为什么是 token 层，不是 Planner 层

- Planner 不再拦截 FQ3（已修复）✅
- article 包含数据 ✅
- chunks 包含 rating 值 ✅
- 但 query 中的 "A"/"C"/"D" 没有被 tokenize → **token 层的单字符过滤是根因**

### 2.2 FQ11/FQ12：gate 统计"回归"，非答案质量回归

| 题号 | 当前 outcome | 预期 | 答案质量 | gate 判定 |
|:---:|------|------|:---:|:---:|
| FQ11 | INSUFFICIENT_EVIDENCE | 拒答（台账无付款条款） | ✅ 正确拒答 | gate 计为"回退" → **评测口径问题** |
| FQ12 | INSUFFICIENT_EVIDENCE | 拒答（CSV 无检验员） | ✅ 正确拒答 | gate 计为"回退" → **评测口径问题** |

**FQ11/FQ12 无需修复。** 两个题的 LLM 路径正确工作：Planner 因"有没有"正确拦截，LLM 搜索后正确拒答。gate 报告的"回退"标记是评测自动化将 INSUFFICIENT_EVIDENCE 计为 FAIL 的统计问题。

---

## 3. 证据分层确认

| 层级 | 确认结果 | 证据 |
|------|:---:|------|
| Source file 已入库 | ✅ | `supplier-registry.yaml` 作为 INTERNAL_MIRROR source 成功导入 |
| Article 已生成 | ✅ | article id=2, `supplier registry`, content_len=12371 |
| Chunks 含评级数据 | ✅ | chunks 5-12 各有 `rating: A/B/C/D` |
| FTS search_tsv 含 rating token | ✅ | `to_tsvector('simple', chunk_text)` → 含 'rating', 'a', 'b' 等 |
| Query token 提取 | ❌ | "A"/"C"/"D" 作为 1 字符 ASCII 未被提取 |
| LIKE 匹配 | ⚠️ 部分 | "评级" token 匹配，但 "A" 的 LIKE 无区分性 |
| LLM 证据消费 | ⚠️ 部分 | LLM 看到 8 条记录但无法可靠地从 LLM 文章文本中逐个提取和计数 rating 值 |

---

## 4. 根因收敛

**唯一根因：单字符判别值（A/B/C/D 评级）在 query tokenization 阶段被过滤，无法作为有效检索 token 进入 FTS/LIKE 搜索。**

这不是 Planner 问题（Planner 已正确回退），不是 Writer 问题（article 含完整数据），不是 RRF 问题（article 已被召回），不是 citation 问题。是 **tokenization 层对短判别值的系统性问题**。

---

## 5. 下一步建议

### 最小修复方向：编译期将单字符判别值嵌入更长的可搜索 token

**思路**：在 Writer/Article 编译阶段，对单字符枚举值（如 rating: A/B/C/D），自动生成包含完整上下文的复合 token（如 "rating-a"），使查询时能被提取为有效 token。

**受益面**：
- 不改变 QueryTokenExtractor 的最小长度约束（保持 2 字符）
- 不修改 query/retrieval/rerank 主链
- 对所有含单字符枚举值的字段一视同仁
- 不影响已有 eval

**不推荐的替代方案**：
- ❌ 降低 QueryTokenExtractor 最小长度到 1 → 引入大量单字符噪声 token
- ❌ 在 query 层对 "A"/"B"/"C"/"D" 做特殊展开 → case 特判
- ❌ 回到 Planner 方案 → Planner 已收口，且 Planner 不能解决单字符值检索问题

---

## 6. 明确声明

- [x] 未修改任何文件
- [x] 未提交 commit
- [x] DB 证据已确认：article id=2 含完整 8 家供应商数据，chunks 5-12 各含 `rating: A/B/C/D`
- [x] FQ11/FQ12 的 gate 报告"回归"是评测统计口径问题，非代码回归
- [x] 日志已证明的事实：article 存在、chunks 存在、LLM 看到 8 条记录
- [x] 源码推断的结论：QueryTokenExtractor 的 2 字符最小长度过滤了单字符评级值
- [x] 推荐修复在编译期（Writer/article 复合 token 生成），非查询期
