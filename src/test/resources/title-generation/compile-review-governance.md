---
title: "compile-review-governance"
summary: "人工确认入库与审查元数据治理说明"
---

# compile-review-governance

## 说明

当前需要把审查失败的草稿留在人工确认队列，并在通过后再写入正式 articles。
同时要保留 reviewStatus、riskLevel 和 sourcePaths 的一致性，避免正文与元数据脱钩。
当人工拒绝草稿时，仍需保留审计记录，不能直接覆盖历史处理痕迹。

### 处理原则

- 不直接入库
- 通过后重建 chunks 和 vector index
- 拒绝时保留审计记录
