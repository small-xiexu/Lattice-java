# Query Cache 中文语气词归一化不足 — 失败归因报告

分析时间：2026-06-10
执行人：agentA
类型：代码归因 + 修复方案

---

## 1. 故障现象

两句语义几乎相同的中文问法返回了形式明显不同的答案：

- Q1: `发布检查表里还有哪些检查项没有完成呢？分别是哪个责任人呢？`
- Q2: `发布检查表里还有哪些检查项没有完成？分别是哪个责任人？`

差异仅在于 Q1 多了句尾语气词"呢"和全角问号变体，但实际语义完全等价。

## 2. 根因分析

### 缓存机制

Query Graph 第一条节点 `normalize_question` 将用户原始 question 归一化为缓存 key：

```java
// QueryGraphAnswerSupport.java:143
state.setNormalizedQuestion(question.trim());
```

`normalizedQuestion` 直接作为 Redis 缓存 key（`llm:query:cache:{normalizedQuestion}`），在 `check_cache` 节点查找，命中则直接返回旧答案，完全跳过 `dispatch_retrieval` 和 LLM 调用。

### 根因

`normalizeQuestion()` 当前**只做 `trim()`**，不做任何中文语气词、标点等价归一化。导致：

| 输入 | normalizedQuestion（缓存 key） |
|---|---|
| `...没有完成呢？分别是哪个责任人呢？` | `...没有完成呢？分别是哪个责任人呢？` |
| `...没有完成？分别是哪个责任人？` | `...没有完成？分别是哪个责任人？` |

两个不同 key → 两个不同缓存条目 → 命中不同历史答案 → 返回形式不一致。

## 3. Docker 日志证据

queryId `64e4f24a` 和 `57bd29a5` 均只在日志中出现 `check_cache → finalize_response`，没有 `dispatch_retrieval`，证实二者都命中了缓存，且是**不同的缓存条目**。

## 4. 修复方案

在 `normalizeQuestion()` 中增加通用的 query cache key canonicalization：

1. **前后空白归一**（已有 `trim()`）
2. **中英文标点归一**：移除常见句尾/句中无实义标点（`？?！!。；;，,：:…`），替换为空格，保留词边界
3. **中文句尾语气词归一**：移除无实义句尾语气词（`呢吧啊呀嘛哦哟咯啦`）

### 为什么不改检索、不改 prompt、不改 AnswerGeneration

缓存 key 归一化是缓存层关注点。检索和答案生成应继续使用原始 question 语义，只改缓存层避免同一语义的不同表达形式产生多份缓存。

### 保守设计

- 不归一 `吗`：`吗` 标记 yes/no 问句，有实义，去掉会改变问句类型
- 不归一 `吗`：`吗` 有实义（yes/no 问句标记），去掉会改变问句类型
- 不归一否定词、数值、实体名、字段名、英文 token
- 仅用于 cache key 的 get/put，不影响用户原始问题、rewrite question、审计日志

## 5. 等价与非等价保证

- **等价归一会合并**：仅语气词和标点不同 → 同一 cache key
- **非等价不会被错误合并**：不同编号、字段名、状态词的问题，语气词归一后仍保留不同内容词 → 不同 cache key

## 6. 明确声明

- [x] 未修改代码（本报告为归因阶段）
- [x] 未修改检索/rerank/prompt/AnswerGeneration 主链
- [x] 修复方向通用，不绑定"发布检查表"/CHK 编号/任何题集内容
