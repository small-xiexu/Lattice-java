# FQ3 RRF Article Diversity 修复 Docker Runtime Gate 报告

验证时间：2026-06-10 15:55 ~ 16:05
HEAD：`463910c`
执行人：agentD
修复报告：`fresh-eval-2026-09_fq3_rrf_article_diversity_fix_result_report.md`（agentA）
前置分析：`fresh-eval-2026-09_fq3_code_feature_flag_failure_analysis_report.md`（agentB）

---

## 1. 门禁

| 门禁 | 结果 |
|---|---|
| Redline | **BLOCKER=0** |
| WeightedRrfFusionTest | **BUILD SUCCESS** |
| Health | **UP** |
| Jar 部署 | ✅（hash: `bd1cb713...`） |

---

## 2. FQ3 回答结果

| 字段 | 值 |
|---|---|
| queryId | `58091880-8f0e-4b68-8c0c-52d72c7de5f6` |
| answerOutcome | PARTIAL_ANSWER |
| generationMode | LLM |
| coverageRate | **0.8** |
| verifiedCount | 6 |
| demotedCount | 2 |

### 回答内容

```
阶梯罚金按逾期天数分段计算：
- 1-7 天: 1.0 元/天 ✅
- 8-14 天: 2.0 元/天 ✅
- 15 天以上: 5.0 元/天 ✅

罚金上限（library.fine.max-days=60）：未提及 ❌
```

---

## 3. 检索分析

| 指标 | 修复前 | 修复后 |
|---|---|---|
| FineServiceImpl 进入 fused top10 | ❌ 被 ADR 挤出 | ✅ **rank 8** |
| ADR-002 占 topK 比例 | 接近 100% | ~80%（多样性改善） |
| answer_shape | POLICY | LLM（LLM 路径下多样性修复生效） |

**RRF diversity 修复生效：FineServiceImpl 从无法进入 top10 改善为 rank 8。** ADR 仍然占主导，但已有改善。

---

## 4. 差距评估

| 维度 | 状态 | 说明 |
|---|---|---|
| 罚金计算规则 | ✅ PASS | 1.0/2.0/5.0 正确 |
| 罚金上限 | ❌ 缺失 | `library.fine.max-days=60` 未在答案中 |
| 目标证据 FineServiceImpl | ✅ rank 8 | 已进入 top10 |
| 目标证据 feature-flags.md | ❌ 未召回 | 上限信息未达 LLM |

---

## 5. 结论

### **PARTIAL — 罚金规则已修复，上限仍缺失**

| 维度 | 判定 |
|---|---|
| RRF diversity 修复 | ✅ 生效（FineServiceImpl rank 0→8） |
| FQ3 罚金计算 | ✅ 正确 |
| FQ3 上限 | ❌ 缺失（feature-flags.md 未召回） |

**修复已改善为核心规则正确，但上限信息（feature-flags.md）未进入 LLM 证据。** 不是 diversity 修复的问题——feature-flags.md 的检索召回需要独立分析，不属于本轮 diversity 修复范围。

---

## 6. 下一步

上限信息（library.fine.max-days=60）在 feature-flags.md 中。建议检查该文件的 FTS/Vector 索引是否覆盖了 `max-days`、`罚金上限` 等关键词，以及是否被其他同名/相似标题的文章压制。属于检索召回优化问题，不涉及 RRF diversity 代码变更。

---

## 7. 明确声明

- [x] 未修改生产代码
- [x] 未清库、未重建
- [x] 未提交 commit
- [x] 清除了 FQ3 缓存 key 后重新查询
