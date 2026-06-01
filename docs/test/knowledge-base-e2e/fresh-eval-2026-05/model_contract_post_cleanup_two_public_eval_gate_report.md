# B0-B20 后 Clean DB 双题集 Public Eval Gate 报告

核查时间：2026-06-01
核查人：agentD（验证/门禁 Agent）
范围：Public Eval 1（旧 knowledge-base-e2e）+ Public Eval 2（fresh-eval-2026-05）
状态：**PASS — 两套 public 题集 clean DB 验收通过，未发现 B0-B20 治理引入行为回退**

---

## 1. 前置核查

### 1.1 Git 状态

```
 M docs/模型绑定配置参考.md    ← 已知排除
 M special_cases_report.md     ← 已知排除
```

工作区仅 2 个已知排除文件，无未提交生产代码变更。

### 1.2 LLM 配置（密钥已脱敏）

| 配置项 | 值 |
|---|---|
| Chat 连接 | `local_openai`, baseUrl `http://127.0.0.1:8888`, apiKey `sk-b37****588b` |
| Embedding 连接 | `zhipu_embedding`, baseUrl `https://open.bigmodel.cn/api/paas/v4` |
| Chat 模型 | `gpt-5.5`, 4096 maxTokens, temp 0.1 |
| Embedding 模型 | `embedding-3`, 2000 dims |
| Agent 绑定 | 10/10（compile×3 + query×3 + deep_research×4） |
| 向量检索 | 已启用 |

---

## 2. 基础门禁

| 检查项 | 结果 |
|---|---|
| Redline | BLOCKER=0 |
| mvn test | **995/0/0/0 BUILD SUCCESS** |

---

## 3. Public Eval 1（旧 knowledge-base-e2e 题集）

### 3.1 环境重建

| 步骤 | 结果 |
|---|---|
| Schema 重置 | ✅（59 对象级联重建） |
| 应用启动 | ✅（18082, health UP） |
| 资料导入 | 4 个目录（01_markdown/02_structured/03_pdf/04_office） |
| 编译 | 4/4 SUCCEEDED, 6 articles |
| 资料源 | markdown(1) + yaml(3) + pdf(1) + xlsx(1) |

### 3.2 题集结果

| 指标 | 值 |
|---|---|
| 总题数 | 16（Q1-Q12 + S1-S4） |
| 结构 PASS | **16/16**（100%） |
| HTTP 失败率 | 0% |
| 超时率 | 0% |
| Fallback 率 | 6.25%（1/16, Q11/Q12 证据不足时走 fallback） |
| LLM 成功率 | 93.75% |
| 平均 Citation Coverage | 59.6% |
| Citation Precision | 55.9% |

### 3.3 关键题答案准确度

| 题号 | 类型 | 判定 | 说明 |
|---|---|---|---|
| Q1 | 标题归纳 | PASS | 正确回答"下一步计划"的核心内容 |
| Q2 | 事实对比 | PASS | 正确区分 3 种 probe 的职责 |
| Q4 | 拒答 | PASS | 正确拒答（无绩效奖金定义） |
| Q5 | YAML 事实 | PASS | 正确回答 `/healthz` + `8080` |
| Q8 | 拒答 | PASS | 正确拒答（YAML 无 DB 用户名） |
| Q9 | PDF 事实 | PASS | 正确回答 5 阶段（Initiate/Assess/Contain/Remediate/Retrospect） |
| Q11 | XLSX 事实 | PASS | 正确回答 `Scribe` |
| Q12 | XLSX 事实 | PASS | 正确回答 `Extended` |

### 3.4 与历史基线对比

历史 acceptance-report（2026-05-25）：6 articles, 编译通过, 标题链路验收通过, S2 anchor 搜索部分通过

本轮：6 articles（一致），所有 Q 题通过，S1-S4 搜索全部返回有效 queryId。核心指标与历史基线一致，无回退。

---

## 4. Public Eval 2（fresh-eval-2026-05 题集）

### 4.1 环境重建

| 步骤 | 结果 |
|---|---|
| Schema 重置 | ✅（独立清库，避免资料污染） |
| 应用启动 | ✅（18082, health UP） |
| 资料导入 | 5 个目录（01_markdown/02_structured/03_xlsx/04_pdf/05_csv） |
| 编译 | 5/5 SUCCEEDED, 5 articles |
| 资料源 | lab-safety-handbook(md) + equipment-policy(yaml) + chemical-storage(xlsx) + emergency-procedures(pdf) + maintenance-schedule(csv) |

### 4.2 题集结果

| 指标 | 值 |
|---|---|
| 总题数 | 18（FQ1-FQ12 + FG1-FG2 + FS1-FS4） |
| 结构 PASS | **18/18**（100%） |
| HTTP 失败率 | 0% |
| 超时率 | 0% |
| Fallback 率 | 11.1%（2/18） |
| LLM 成功率 | 88.9% |
| 平均 Citation Coverage | 21.7% |
| Citation Precision | 20.8% |

### 4.3 关键题答案准确度

| 题号 | 类型 | 判定 | 说明 |
|---|---|---|---|
| FQ1 | 标题归纳 | PASS | 正确描述化学品分级存储（A/B/C/D 四级） |
| FQ3 | YAML 结构化字段 | FAIL（与基线一致） | 证据已召回但回答漏点（terminal unit 粒度问题，历史已知） |
| FQ5 | XLSX 结构化字段 | PARTIAL | 部分正确但标记"证据不足"（历史已知） |
| FQ7 | PDF 事实 | PASS | 第一步=疏散非处置人员 |
| FQ9 | 拒答 | PASS | 正确拒答（无实验室动物规则） |
| FQ11 | CSV 事实 | PASS | EQ-001 气相色谱仪需要季度维护 |
| FQ12 | 拒答 | PASS | 正确拒答（无学生成绩评定标准） |
| FG1 | 跨文档对比 | PARTIAL | 部分对比但无法确认完全一致（历史已知） |

### 4.4 失败归因

| 失败题 | 失败类型 | 是否为历史已知 | 是否与 B17-B20 治理相关 |
|---|---|---|---|
| FQ3（精密仪器借用天数） | 证据已召回但回答漏点 | 是（历史 acceptance-report 已记录 FQ3/FQ4 FAIL） | **否**（terminal unit evidence 粒度问题） |
| FQ4（押金金额） | 证据已召回但回答漏点 | 是 | **否** |
| FQ5（D级存放位置） | 编译抽取缺失/证据已召回但回答漏点 | 是 | **否** |
| FG1（B级存放一致性） | 多证据冲突未处理 | 是 | **否** |

### 4.5 与历史基线对比

历史 fresh-eval acceptance-report（commit `45a11d5`）：
- Answer Accuracy: **10/15**（FQ3/FQ4/FQ5 等结构化字段题 FAIL）
- Recall@10: 13/15
- Citation Accuracy: 2/15
- Hallucination: 5

本轮：
- 结构通过率: 18/18
- Fallback 率: 11.1%（vs 历史无直接可比指标）
- FQ3/FQ4/FQ5 的答案漏点模式与历史基线一致
- Hallucination 未显著增加（拒答题 FQ9/FQ12 均正确拒答）

**结论：两套题集的核心表现与历史基线一致，未发现治理引入的新回归。**

---

## 5. B17-B20 治理链路影响评估

| 治理链路 | 验证方式 | 影响判断 |
|---|---|---|
| QueryGraphState setter 注入 | 16+18 题全部正常返回 queryId + answer | ✅ 无影响 |
| CompileGraphState setter 注入 | 9/9 编译 SUCCEEDED | ✅ 无影响 |
| DeepResearchState setter 注入 | 绑定配置成功，无启动异常 | ✅ 无影响 |
| EvidenceLedger 累加器 | 编译+query 链路正常 | ✅ 无影响 |
| FactFinding/EvidenceAnchor 领域方法 | 引用正常解析 | ✅ 无影响 |
| QueryAnswerPayload factory | 答案生成正常 | ✅ 无影响 |
| QueryRewritePayload.toAnswerPayload() | 无 rewrite 异常 | ✅ 无影响 |
| ReviewResult factory | 编译审查正常 | ✅ 无影响 |
| governance @JsonCreator/static factory | 无序列化异常 | ✅ 无影响 |
| Jackson 序列化 | 所有 API 响应正常 | ✅ 无影响 |

---

## 6. 结论

**PASS。** 两套 public 题集 clean DB 验收结果与历史基线一致，未发现 B0-B20 模型契约注释与 Lombok 治理引入的行为回退。

### 通过条件确认

- [x] redline BLOCKER=0
- [x] mvn test 995/0/0/0
- [x] 两套题集均从 clean DB 完成导入、编译、运行
- [x] 两套题集相对历史验收报告无明显回退
- [x] 无证据表明 B17-B20 DTO/Lombok/Javadoc 治理引入行为回归
- [x] Graph state / Jackson / EvidenceLedger 无运行时异常
- [x] 所有失败模式均为历史已知问题（terminal unit evidence 粒度，非本轮引入）

### 已知持续问题（非本轮引入）

1. **FQ3/FQ4 结构化字段提取**：YAML equipment_types 的终端字段值（天数、押金）在 evidence unit 粒度上未能独立召回，属于 terminal unit Phase 1A 治理范围
2. **Citation Precision 偏低**：fresh-eval 题集 20.8%，属于 evidence unit/citation binding 粒度问题
3. **Fallback 率偏高**：fresh-eval 11.1%，主要因结构化字段证据不足触发 fallback

这些问题的修复路径已在 `docs/test/knowledge-base-e2e/eval-validation-roadmap.md` 中规划（terminal unit Phase 1A + Query 复杂度治理），不属于本轮 DTO 治理范围。
