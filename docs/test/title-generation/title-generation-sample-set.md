# 知识条目标题生成样本集

**版本**：v1
**更新时间**：2026-05-24
**用途**：作为“知识条目标题生成优化”固定回归样本集，覆盖强语义标题、泛化标题、弱标题、多子主题标题，以及 Office / PDF 标题不稳定场景。后续任何规则层或 LLM 兜底逻辑调整，都必须先回归本样本集。

---

## 1. 使用规则

- 本样本集只服务通用标题能力回归，不服务业务知识正确性判断。
- 样本中的标题、正文与期望标题都不得被实现层硬编码为专用映射。
- 当规则层与样本期望不一致时，先分析是样本定义不合理还是实现退化，不允许直接放宽样本。
- 若新增样本，必须同步更新机器可读文件 `src/test/resources/title-generation/title-generation-sample-set.json`。

---

## 2. 样本清单

| caseId | 场景类型 | fixture | 来源标题期望 | 锚点标题 | 目标模式 | 代表性标题期望 | 说明 |
|---|---|---|---|---|---|---|---|
| `TG-MD-WEAK-001` | Markdown 长文 + 泛化标题 | `src/test/resources/title-generation/quality-progress-and-lessons.md` | `quality-progress-and-lessons` | `下一步计划` | `RULE_BASED` | `Dashboard 状态摘要接入与质量台账回写要求` | 验证泛化标题不能直接充当最终标题 |
| `TG-MD-STRONG-001` | Markdown 长文 + 强语义标题 | `src/test/resources/title-generation/vector-index-operations.md` | `向量索引运维手册` | `向量索引重建流程与状态校验` | `ANCHOR_DIRECT` | `向量索引重建流程与状态校验` | 验证强语义标题应直接保留 |
| `TG-MD-WEAK-002` | Markdown 长文 + 弱标题 | `src/test/resources/title-generation/compile-review-governance.md` | `compile-review-governance` | `说明` | `RULE_BASED` | `人工确认入库约束与审查元数据一致性要求` | 验证“说明”类标题需基于正文重写 |
| `TG-MD-MULTI-001` | Markdown 长文 + 多子主题 | `src/test/resources/title-generation/multi-section-migration.md` | `migration-notes` | `总结` | `RULE_BASED` | `接口契约、路由切换与回退边界` | 验证并列子主题的压缩表达 |
| `TG-OFFICE-UNSTABLE-001` | Office 抽取标题不稳定 | `src/test/resources/title-generation/office-deployment-guide.parsed.json` | `部署联调手册` | `部署步骤` | `ANCHOR_DIRECT` | `部署步骤` | 验证 `sourceTitle` 可从 bundle 级候选稳定回退 |
| `TG-PDF-UNSTABLE-001` | PDF 抽取标题缺失 + 泛化锚点 | `src/test/resources/title-generation/pdf-incident-summary.parsed.json` | `monthly-incident-summary` | `附录` | `RULE_BASED` | `高频风险来源与人工确认积压事项` | 验证文件名回退与弱标题重写 |
| `TG-LLM-FALLBACK-001` | 规则低置信度 + LLM 兜底 | `src/test/resources/title-generation/quality-progress-and-lessons.md` | `质量打磨阶段进展` | `下一步计划` | `LLM_FALLBACK` | `Dashboard 状态摘要接入与质量台账回写要求` | 验证低置信度规则标题会进入模型兜底 |

---

## 3. 覆盖维度

- Markdown 长文
- 泛化标题
- 强语义标题
- 多 section 并列主题
- Office / PDF 来源标题不稳定
- `sourceTitle` 回退链路
- `ANCHOR_DIRECT / RULE_BASED / LLM_FALLBACK` 三条主路径

---

## 4. 当前说明

- 当前样本集已补 `TG-LLM-FALLBACK-001`，用于约束低置信度规则标题进入 LLM 兜底的最小闭环。
- Office / PDF 场景当前先用 parsed fixture 固化输入契约，不要求在本轮 P0 引入真实二进制样本。
