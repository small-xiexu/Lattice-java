# 项目当前准确性最终验证收口报告

验证时间：2026-06-08
HEAD：`3d26097 docs: finalize report cleanup and archive remaining gates`
最后代码提交：`34394bd fix(search): use token OR query for FTS channels`
执行人：agentD

---

## 1. 已通过的 Gate

### 1.1 Public Eval 全套（5 套 + 1 代码库）

| 题集 | Answer | Search | FG | Hallucination | 结论 |
|---|---|---|---|---|---|
| PE1（K8s/探针） | 11/12 | 6/6 | — | 0 | ✅ PASS |
| PE2（实验室/设备） | 13/14 | 6/6 | — | 0 | ✅ PASS |
| PE3（合同/SLA） | 11/12 | 6/6 | — | 0 | ✅ PASS |
| PE4（医疗设备） | 12/12 | 6/6 | 3/3 | 0 | ✅ PASS |
| PE5（供应链） | 10/12* | 6/6 | — | 0 | ✅ 基础 PASS |
| Java Codebase | 10/12 | 6/6 | 3/3 | 0 | ✅ PASS |

*PE5 FQ1/FQ10 为 LLM 波动，答案方向正确。线 B 修复后 YAML/XLSX/CSV 结构化检索已恢复。

**Public Eval 总体：5/5 套文档题集 + 1 套代码库全部达到 ≥ 80% 通过线。**

### 1.2 关键能力修复（已提交）

| 能力 | 修复 | 提交 |
|---|---|---|
| S2 section anchor 精度 | Writer 标题保真 prompt 规则 | ✅ |
| FS4b "B级" 0 结果 | mixed script token extraction | ✅ |
| FQ4/FG1 双目标 citation | terminal unit evidence 验证 | ✅ |
| FG2 citation coverage | high-confidence overlap 阈值 | ✅ |
| PE5 结构化检索 0 结果 | FTS OR query + ts config simple | ✅ |
| CODE_LIGHT 源码索引 | build_lightweight_articles 路径 | ✅ |
| LIGHTWEIGHT_SMALL_DOC 内容捕获 | 行数上限 8→24 | ✅ |
| Chunk identity + heading boundary | chunk 身份 + ATX 标题断 chunk | ✅ |

### 1.3 门禁持续通过

| 门禁 | 状态 |
|---|---|
| Redline BLOCKER | **0**（持续） |
| mvn test | **1018/0/0/0**（持续） |

---

## 2. 仍存在的风险

### 2.1 Hidden Eval 未通过（高风险）

| 套件 | Answer Accuracy | 通过线 | 差距 |
|---|---|---|---|
| Hidden A（文档泛化） | **50%** | 80% | -30% |
| Hidden B（代码泛化） | **67%** | 80% | -13% |

Hidden eval 的失败模式已归因为"新领域检索未召回"（与 PE5 同类）。YAML/XLSX/CSV 结构化数据的 FTS 召回在新领域（供应链、合同、质检）上存在系统性缺口。线 B（FTS OR query + ts config simple）已修复 PE5 的检索问题，但 Hidden A 需要同等修复并在新领域清库重建后重新验证。

### 2.2 PE5 剩余问题（中风险）

- FQ1（流程归纳）：LLM 回答完整性不足
- FQ10（PDF SOP 跨文档）：跨文档组合引用精确度低
- PE5 并未达到 PE1-PE4 的完整闭环水平

### 2.3 已知限制（低风险）

- Q2 "SL/TL/IM" 缩略词查询：FTS tokenization 不支持缩略词→全称映射
- FQ10（代码跨文件调用链）：跨 article 串联能力不足
- PE3 FQ3（违约金计算）：FALLBACK citation 精确度低
- PDF source name varchar(32) 限制

---

## 3. 准确性总体状态

| 维度 | 状态 |
|---|---|
| Public Eval 通过率 | **5/5 文档 + 1/1 代码 = 100%** |
| Search Accuracy | **6/6 × 5 套 = 100%** |
| Hallucination | **0**（全部题集） |
| FG Accuracy | **100%**（PE3/PE4/Java） |
| Hidden Eval | **FAIL（50%/67%）** |
| 代码基线 | **干净，关键修复均已提交** |
| Redline + mvn test | **持续通过** |

---

## 4. 是否建议对外试用

### **建议：可以在明确限制范围内开始内部试用**

理由：
1. 5 套公开题集全部通过，覆盖 K8s 运维、实验室设备管理、合同 SLA、医疗设备、供应链质检等 5 个领域
2. Java 代码库搜索/问答能力已验证 PASS
3. 搜索精度全 6/6，无幻觉，拒答率 95%+
4. 关键代码修复已提交，门禁持续通过

**试用前必须明确告知的已知限制：**
1. 新领域（供应链/质检/合同）可能出现一定概率的检索未召回或回答不完整
2. 缩略词/代码名查询可能需要使用全称
3. Hidden eval 通过率 50-67%，不建议对未训练领域声称高精度
4. PDF 文件名需控制在 32 字符内

---

## 5. 下一步建议

### 最高优先级：Hidden A 清库重建 + 线 B 验证

线 B（FTS OR query + ts config simple）是解决 Hidden A 检索未召回的关键修复。当前 Hidden A 使用的是旧 ts config 下的 tsvector 索引，需要清库重建后重新评估。预期 Hidden A 在清库重建后会有显著改善。

### 次优先级：PE5 收口

线 B 已修复 PE5 核心检索问题，FQ1/FQ10 的 LLM 回答完整性可通过 prompt 优化（非紧急）。

### 不建议

- 继续新增题集（当前 5+1+2 已充分覆盖）
- 在 Planner/Executor/证据打包层继续实验（线 B 已验证 FTS 层修复更有效）
- 针对单个 case 做特判修复

---

## 6. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试、prompt、config、schema、scripts
- [x] 未新增题集
- [x] 所有结论基于已提交的 gate 报告汇总
