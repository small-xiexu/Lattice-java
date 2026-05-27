---
title: "向量索引运维手册"
summary: "向量索引重建与状态校验说明"
---

# 向量索引运维手册

## 向量索引重建流程与状态校验

当 embedding 模型、向量维度或索引策略发生变化时，需要先检查 schema 维度，再执行重建任务。
重建完成后必须校验 indexedArticleCount、annIndexType 与 annIndexReady，确保查询侧已切回可用状态。

### 重建前检查

- 当前 embedding 模型
- 期望维度
- schema 维度

### 重建后校验

- indexedArticleCount
- annIndexType
- annIndexReady
