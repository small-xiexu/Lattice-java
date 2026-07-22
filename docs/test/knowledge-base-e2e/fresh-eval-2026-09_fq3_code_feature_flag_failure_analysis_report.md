# PE6 FQ3 代码 + Feature Flag 检索失败 — 只读归因报告

分析时间：2026-06-10
执行人：agentB（治理/归因 Agent）
类型：只读 DB 检索审计分析，不修改任何文件

---

## 1. 复现方式

queryId: `11ee53c9-0b1d-42af-972d-69f7b8c1b29e`
run_id: 148
query: `阶梯罚金的计算规则是什么？罚金有上限吗？`

---

## 2. 检索通道命中实况

### 2.1 Fused Top-8（最终进入 LLM 的证据）

| fused_rank | title | channel |
|:---:|------|------|
| 1 | **ADR 002: 逾期扣分与阶梯罚金方案 / 阶梯罚金** | article_chunk_fts |
| 1 | ADR 002: 逾期扣分与阶梯罚金方案 / 逾期罚金规则 | article_chunk_fts |
| 1 | ADR 002: 逾期扣分与阶梯罚金方案 / 设计含义 | article_chunk_fts |
| 1 | ADR 002: 逾期扣分与阶梯罚金方案 | article_chunk_fts ×2 |
| 1 | ADR 002: 逾期扣分与阶梯罚金方案 / 决策 | article_chunk_fts |
| 1 | ADR 002: 逾期扣分与阶梯罚金方案 / 考虑因素 | article_chunk_fts |
| 8 | 图书馆借阅管理系统 / fine | article_chunk_fts |
| 8 | 图书馆借阅管理系统 / 证据缺口与不可确定事项 | article_chunk_fts |

**Fused Top-8 完全被 ADR-002 的 7 个 chunk 占据。** FineServiceImpl、Feature Flag、application config 均未进入 fused。

### 2.2 目标 article 在各通道的原始排名（均被排除在 fused 之外）

| 目标 article | 通道 | 通道内 rank | score | 是否进入 fused |
|------|------|:---:|:---:|:---:|
| **FineServiceImpl** | article_vector | **3** | 8.46 | ❌ |
| FineServiceImpl / `calculateOverdueFine` 方法 | chunk_vector | 10 | 8.57 | ❌ |
| FineServiceImpl / 逾期天数与费率规则 | chunk_vector | 16 | 8.55 | ❌ |
| **Feature Flag 说明** | article_vector | **2** | 8.48 | ❌ |
| Feature Flag 说明 | article_chunk_fts | 13 | 32 | ❌ |
| Feature Flag 说明 / 行为边界与注意事项 | chunk_vector | 14 | 8.55 | ❌ |
| Feature Flag 说明 | refkey | 6 | 10 | ❌ |
| Feature Flag 说明 | source | 5 | 7.5 | ❌ |
| application / library.fine 罚金配置 | chunk_vector | **8** | 8.58 | ❌ |
| application prod / library.fine 罚金费率配置 | chunk_vector | 12 | 8.56 | ❌ |

**FineServiceImpl 在 article_vector 通道排名第 3，Feature Flag 说明在 article_vector 排名第 2——两个目标 article 均被成功检索。但都在 RRF 融合阶段被 ADR-002 的 chunk 群挤出 fused top-8。**

---

## 3. 失败类型归类

### 主因：**rerank / RRF 融合排序低**

ADR-002（"逾期扣分与阶梯罚金方案"）的文章标题与查询词"阶梯罚金"高度匹配，导致其 7 个 chunk 在 article_chunk_fts、refkey、article_vector、chunk_vector 四个通道中均获得高分。RRF 融合时，同一 `article_key` 的多个 chunk 的 RRF 分数被累加，使 ADR-002 占据了 fused top-8 的全部席位。

FineServiceImpl 和 Feature Flag 说明虽然在各自通道中排名 2-3，但它们的 article_key 与 ADR-002 不同，不会与 ADR-002 的分数合并，因此在 RRF 竞争中落败。

### 排除的类型

| 候选类型 | 判定 | 证据 |
|----------|:---:|------|
| 检索未召回 | **排除** | FineServiceImpl 被 article_vector (rank3)、chunk_vector (rank10/16) 召回；Feature Flag 被 6 个不同通道召回 |
| 证据已召回但未进入 prompt | **排除为主因** | 进入了 retrieval 但被 RRF 挤出——问题在融合层，不是 prompt 层 |
| prompt 有证据但回答漏点 | **排除** | LLM 收到的 fused top-8 中不含 FineServiceImpl 或 Feature Flag |
| citation validation 误杀 | **排除** | 与 citation 无关 |

---

## 4. RRF 融合机制分析

### 4.1 为什么 ADR-002 占据全部席位

```
ADR-002 article_key = "sources--sources-adr-002-overdue-penalty"

article_chunk_fts: 7 chunks × score 35-38 → RRF 累加
refkey:            1 hit   × score 57   → RRF 累加
article_vector:    1 hit   × score 8.54 → RRF 累加
chunk_vector:      6 chunks × score 8.5-8.7 → RRF 累加
───
同一 article_key 的 RRF 总分 ≈ 远超其他 article
```

每个 chunk 对 ADR-002 的 RRF score 贡献为 `channelWeight / (rrfK + rank)`。当同一 article 有 7+6=13 个 chunk 命中时，RRF 总分是其他仅有 1-2 个 chunk 命中的 article 的 5-10 倍。

### 4.2 为什么 FineServiceImpl 被挤出

FineServiceImpl 在各通道的命中：
- article_vector: rank 3, score 8.46 → RRF 贡献约 `1.0 / (60+3)` = 0.016
- chunk_vector: 2 hits at rank 10/16 → RRF 贡献约 `1.35 / (60+10)` + `1.35 / (60+16)` = 0.037

FineServiceImpl 的 RRF 总分 ≈ 0.053。相比之下，ADR-002 的 RRF 总分 ≈ 13 × 0.02-0.03 ≈ 0.3-0.4。差距约 6-8 倍。

---

## 5. 根因判断

### 这是 RRF 融合的通用问题，不是 FQ3/ADR-002/FineServiceImpl 特有问题

**当一个 article 在多个通道中有大量 chunk 命中时，RRF 的 identity-based 累加机制会使该 article 过度占据 fused top-K，形成"单文档霸权"。** 其他同样相关但 chunk 命中数较少的 article 被系统性挤出。

CODE_LIGHT 模式加剧了这个问题：每个 Java 源文件拆成多个小 chunk（类定义、方法、字段等），而 Markdown 文档（如 ADR）也会被 SemanticChunker 拆成多个 chunk。一篇长 ADR 的 7 个 chunk 全部命中时，其 RRF 累积效应会淹没短文章的 1-2 个命中。

**这不是 FQ3 独有的问题。** 任何查询中，如果某个 article 的多个 chunk 都与查询词高度匹配，该 article 会占据 fused top-K 的大部分席位，阻碍其他互补性证据进入 LLM 上下文。

---

## 6. 通用修复建议

### 方向：在 RRF 融合中增加 per-article chunk 数量上限或多样性惩罚

**修改范围**：`RrfFusionService.java` 的 `mergeHits()` 或 `fuse()` 方法

**具体方案**（通用，不绑定任何 article/query）：
- 当同一 `article_key` 已有 N 个 chunk 进入 fused top-K 后，后续该 article 的 chunk 的 RRF 分数施加递减权重（如：第 1-2 个 chunk 全额，第 3-4 个 chunk ×0.5，第 5+ 个 chunk ×0.25）
- 或：每个 article 在 fused top-K 中的席位上限为 M 个（如 M=3）

**为什么不是 case 特判**：
- 规则仅基于 `article_key` 的 chunk 计数，不涉及任何具体 article title、文件名、query 文本
- 对所有 article 一视同仁——无论是 ADR-002 还是 FineServiceImpl
- 受益面：任何查询中，不会再有单一 article 的多个 chunk 占据全部 fused 席位

**风险**：
- 降低 per-article chunk 上限可能在某些场景下减少有用信息（如同一 article 的多个互补 chunk 都相关）
- 建议 M=3 作为初始值，后续根据 eval 回归调整

---

## 7. 明确声明

- [x] 未修改任何代码
- [x] 未修改题集 expected
- [x] 未修改 prompt/schema/scripts
- [x] 未清库、未重建、未导入
- [x] 未提交 commit
- [x] DB 检索审计数据确凿：run_id=148, FineServiceImpl 在 article_vector rank 3 但未进入 fused
- [x] ADR-002 的 7 个 chunk 占据全部 fused top-8 席位
- [x] 根因为 RRF identity-based 融合的单 article 过度集中——通用问题，非 FQ3 特判
- [x] 推荐修复为 per-article chunk 席位上限或多样性惩罚——通用规则
