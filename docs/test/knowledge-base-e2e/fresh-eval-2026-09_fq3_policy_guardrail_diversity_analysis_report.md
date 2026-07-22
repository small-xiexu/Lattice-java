# FQ3 POLICY Guardrail 多样性归因报告

分析时间：2026-06-10
类型：只读归因汇总 + 最小修复边界

## 1. 问题

FQ3 问题为“阶梯罚金的计算规则是什么？罚金有上限吗？”。上一轮 Docker runtime gate 中，阶梯罚金分段规则已经回答正确，但未回答罚金上限 `library.fine.max-days=60`。

## 2. 三层链路结论

| 层级 | 结论 |
|---|---|
| raw retrieval | `feature-flags.md` 与 `library.fine.max-days=60` 相关证据已被多通道召回 |
| fused / topK | 最终 fused hits 未保留 feature flag / config 证据 |
| prompt evidence | prompt 中没有 feature flag / config 证据，因此 LLM 无法回答上限 |

之前 gate 报告中的“feature-flags.md 未召回”表述不准确；更准确的失败类型是：**证据已召回，但在融合/structured guardrail 后未保留**。

## 3. 根因

FQ3 被判为规则类 `POLICY` 形态后进入 structured evidence guardrail。`RrfFusionService` 中新增的 diversity topK 选择只作用于 GENERAL 路径；`POLICY` 路径仍直接从 structured 排序结果取前 N 个候选，导致同一 article/source 的多个高相关 chunk 继续占据 topK，互补的 feature flag / config 证据被挤出。

该问题是通用融合选择缺口，不绑定 FQ3、ADR、feature-flags、FineServiceImpl 或具体配置 key。

## 4. 最小修复边界

只处理一个根因：让 structured / POLICY guardrail 路径也复用通用 diversity topK 选择。

不在本轮处理：

- 不修改 prompt 模板
- 不修改中文 exact / numeric 识别
- 不修改题集 expected
- 不添加任何具体业务词、文件名、配置 key 或答案模板特判

## 5. 验证要求

- `WeightedRrfFusionTest` 覆盖 POLICY structured guardrail 的多样性选择
- redline 扫描 `BLOCKER=0`
- 后续 Docker runtime gate 需确认 FQ3 的 fused / prompt 重新包含 feature flag 或等价 config 上限证据
