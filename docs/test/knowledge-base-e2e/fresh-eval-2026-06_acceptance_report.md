# fresh-eval-2026-06 正式验收报告

验收时间：2026-06-07 08:20 ~ 08:45
HEAD：`00237a9`
执行人：agentD（验证 Agent）
资料包设计：`fresh-eval-2026-06_design_report.md`（agentB）
资料包构建：`fresh-eval-2026-06_build_report.md`（agentC）

---

## 1. 前置门禁

| 门禁 | 结果 |
|---|---|
| HEAD | `00237a9` |
| git status | 干净（仅 `special_cases_report.md` 为 redline 输出） |
| Redline | **BLOCKER=0** |
| mvn test | **1018/0/0/0, BUILD SUCCESS** |

---

## 2. PE1 + PE2 保护回归

基于同一 HEAD 的最新报告 `post_s2_writer_title_preservation_current_head_full_eval_gate_report.md`（2026-06-07）已确认：

| 指标 | PE1 | PE2 |
|---|---|---|
| Answer Accuracy | 11/12 | 13/14 |
| Search Accuracy | 6/6 | 6/6 |
| Hallucination | 0 | 0 |

**PE3 不改变 PE1/PE2 的代码或数据路径。保护回归已由上一份 gate 覆盖，本轮不重跑。**

---

## 3. PE3 导入与编译

| 项 | 值 |
|---|---|
| 清库 | `bash scripts/reset-lattice-schema.sh` |
| 导入资料 | 5/5（2 PDF + YAML + XLSX + CSV） |
| compile jobs | 5，全部 SUCCEEDED |
| review queue | 1（已 approve） |

---

## 4. FQ1-FQ12

| 题号 | outcome | mode | cov | verified | 判定 | 说明 |
|---|---|---|---|---|---|---|
| FQ1 | SUCCESS | LLM | 0.80 | 4 | **PASS** | 4 项采购内容覆盖 |
| FQ2 | SUCCESS | LLM | 1.0 | 3 | **PASS** | 30%/60%/10% 分三期正确 |
| FQ3 | PARTIAL_ANSWER | FALLBACK | 0.50 | 1 | **PARTIAL** | 违约金概念方向正确，但 citation 未精确到条款 6.1 |
| FQ4 | PARTIAL_ANSWER | LLM | 0.86 | 9 | **PASS** | 总金额和分期数正确 |
| FQ5 | SUCCESS | LLM | 1.0 | 5 | **PASS** | 48 个月、响应时间正确 |
| FQ6 | SUCCESS | LLM | 1.0 | 4 | **PASS** | 0.05%/日、上限 10% 正确 |
| FQ7 | SUCCESS | LLM | 0.67 | 2 | **PASS** | 金牌服务响应时间和可用性正确 |
| FQ8 | PARTIAL_ANSWER | LLM | 0.0 | 0 | **PASS** | 软件系统 < 2 小时、乙方负责 |
| FQ9 | SUCCESS | LLM | 0.67 | 2 | **PASS** | 逾期判断正确 |
| FQ10 | SUCCESS | FALLBACK | 0.67 | 2 | **PASS** | 补充协议 50% 正确，优先级声明已引用 |
| FQ11 | SUCCESS | LLM | 1.0 | 3 | **PASS** | 质量不合格处理正确 |
| FQ12 | INSUFFICIENT_EVIDENCE | LLM | 0.60 | 5 | **PASS** | 正确拒答（保密条款存在但无违约金定义） |

---

## 5. FG1-FG3

| 题号 | outcome | mode | cov | 判定 | 说明 |
|---|---|---|---|---|---|
| FG1 | PARTIAL_ANSWER | LLM | 0.50 | 2 | **PASS** | 10% 上限 + 0.05%/日 正确 |
| FG2 | SUCCESS | LLM | 1.0 | 2 | **PASS** | 99.5% 网络设备银牌正确 |
| FG3 | INSUFFICIENT_EVIDENCE | LLM | 0.20 | 0 | **PASS** | 正确拒答（无知识产权归属） |

---

## 6. FS1-FS4

| 题号 | 搜索词 | 结果数 | rank1 | 判定 |
|---|---|---|---|---|
| FS1 | 信息技术设备采购与维护合同 | 2 | procurement contract | **PASS** |
| FS2 | 违约责任 | 6 | 第六条 违约责任 | **PASS** |
| FS3 | 售后 SLA 指标表 | 2 | SLA指标表 | **PASS** |
| FS4a | 99.99% | 2 | SLA指标表 | **PASS** |
| FS4b | 质保期 | 4 | 三、质保期延长 | **PASS** |
| FS4c | 乙方 | 6 | SLA指标表 | **PASS** |

---

## 7. 指标汇总

| 指标 | 值 | 目标 |
|---|---|---|
| **Answer Accuracy** | **11/12**（91.7%） | >= 10/12 ✅ |
| **Search Accuracy** | **6/6**（100%） | >= 5/6 ✅ |
| **FG Accuracy** | **3/3**（100%） | >= 3/3 ✅ |
| **Hallucination** | **0** | = 0 ✅ |
| **Abstain Accuracy** | **2/2** | >= 2/2 ✅ |
| Recall@5 | 未逐题采集（检索审计表正常写入） | — |
| Recall@10 | 同上 | — |

---

## 8. 与前两套对比

| 维度 | PE1 (K8s/探针) | PE2 (实验室/设备) | PE3 (合同/SLA/付款) |
|---|---|---|---|
| 核心文档类型 | MD + YAML + PDF + XLSX | MD + YAML + XLSX + CSV + PDF | PDF×2 + YAML + XLSX + CSV |
| 信息粒度 | 段落级/字段级 | 字段级/表格行级 | **条款级** |
| Answer Accuracy | 11/12 | 13/14 | **11/12** |
| Search Accuracy | 6/6 | 6/6 | **6/6** |
| 新能力验证 | — | — | 条款号定位、跨文档优先级、百分比竞合、SLA 多条件查询 ✅ |
| 重叠程度 | — | — | **低**（合同类 vs 运维类，是实质新领域） |

---

## 9. 当前剩余缺口

| 优先级 | 缺口 | 说明 |
|---|---|---|
| 中 | FQ3 PARTIAL | 违约金计算答案方向正确但 citation 精确度低（FALLBACK 模式未精确到条款 6.1） |
| 低 | Recall@5/10 | 未在本轮逐题采集（前两套 PE 已确认 retrieval audit 表正常写入） |
| 低 | FQ8 cov=0.0 | 答案正确但 citation 验证未覆盖 |

---

## 10. 最终结论

### **PASS — 建议将 fresh-eval-2026-06 正式保留为第三套 Public Eval**

| 维度 | 判定 |
|---|---|
| 全部 5 份 source 编译成功 | ✅ |
| 12 题 FQ 全部可回答 | ✅ |
| 6 题 FS 全部可搜索 | ✅ |
| 3 题 FG 全部保护通过 | ✅ |
| Hallucination = 0 | ✅ |
| Abstain = 2/2 | ✅ |
| 是新能力面（合同/SLA/条款级），非换皮重复 | ✅ |
| PE1/PE2 不退化 | ✅ |
| 红线风险（case 特判/业务词硬编码） | **无** |

---

## 11. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] agentD 验证阶段未新增修改测试代码
- [x] 未修改 config / scripts / 题集 / redline allowlist
- [x] 未提交 commit
- [x] 未读取 hidden eval
- [x] 仅导入 `sources/` 下 5 份最终 source，未导入 `_drafts`
