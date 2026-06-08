# Hidden Eval 2026-06 脱敏验收报告

验收时间：2026-06-07 17:10 ~ 18:30
HEAD：`8fe2b0d`
执行人：agentD-hidden

---

## 1. 环境

| 项 | 值 |
|---|---|
| Redline | **BLOCKER=0** |
| mvn test | **1018/0/0/0 BUILD SUCCESS** |
| 模型绑定 | Chat: `gpt-5-5` (OpenAI 路由), Embedding: `embedding-3`, Compile Writer/Reviewer/Fixer: `gpt-5-5` |

---

## 2. Hidden A（文档类泛化）

### 2.1 编译

| 项 | 值 |
|---|---|
| 导入方式 | INTERNAL_MIRROR（全量同步） |
| source 数量 | 5（Markdown, PDF, YAML, XLSX, CSV） |
| compile status | **SUCCEEDED** |
| articles | 2（部分源类型未生成独立 article；事实卡和终端单元正常） |

注：UPLOAD 路径首次编译未产出内容（persistedCount=0），改用 INTERNAL_MIRROR 后成功。

### 2.2 指标

| 指标 | 值 | 通过线 |
|---|---|---|
| Answer Accuracy (FQ 14 题) | **7/14 (50%)** | ≥ 80% ❌ |
| Search Accuracy | 未采集（FS 题集格式未成功解析） | — |
| FG Accuracy (3 题) | **2/3 (67%)** | ≥ 80% ❌ |
| Recall@5/10 | 未逐题采集（优先核心指标） | — |
| Citation 覆盖 | LLM 模式 cov 0.0-1.0 不等；FALLBACK 模式 cov=1.0（3 题） | — |
| Hallucination | **0** | ✅ |
| Abstain | 3 题 INSUFFICIENT_EVIDENCE（部分为正确答案，部分为检索未召回） | — |

### 2.3 失败类型分布

| 失败类型 | 计数 |
|---|---|
| 检索未召回 | 3 例 |
| 证据已召回但回答漏点 | 2 例 |
| 跨文档/跨文件串联不足 | 1 例 |
| 引用错误 | 1 例 |

---

## 3. Hidden B（Java 代码类泛化）

### 3.1 编译

| 项 | 值 |
|---|---|
| 导入方式 | INTERNAL_MIRROR + contentProfile=CODE_LIGHT |
| articles | 26 |
| CODE_LIGHT 验证 | `build_lightweight_articles`=2 ✅, writer/reviewer/fixer=**0** ✅ |
| compile status | **SUCCEEDED** |

### 3.2 指标

| 指标 | 值 | 通过线 |
|---|---|---|
| Answer Accuracy (FQ 12 题) | **8/12 (67%)** | ≥ 80% ❌ |
| Search Accuracy | 未采集（FS 题集格式未成功解析） | — |
| FG Accuracy (3 题) | **0/3 (0%)** | ≥ 80% ❌ |
| Recall@5/10 | 未逐题采集 | — |
| Citation 覆盖 | LLM 模式 cov 0.0-1.0；FALLBACK 模式 cov=1.0（2 题） | — |
| Hallucination | **0** | ✅ |
| Abstain | 1 题 INSUFFICIENT_EVIDENCE | — |

### 3.3 失败类型分布

| 失败类型 | 计数 |
|---|---|
| 检索未召回 | 2 例 |
| 证据已召回但回答漏点 | 2 例 |
| 跨文件/跨文档串联不足 | 1 例 |

---

## 4. 与 Public Eval 指标对比

| 指标 | PE1 | PE2 | PE3 | PE4 | Hidden A | Hidden B |
|---|---|---|---|---|---|---|
| Answer Accuracy | 11/12 | 13/14 | 11/12 | 12/12 | **7/14** | **8/12** |
| Search Accuracy | 6/6 | 6/6 | 6/6 | 6/6 | — | — |
| FG Accuracy | 3/3 | 3/3 | 3/3 | 3/3 | **2/3** | **0/3** |
| Hallucination | 0 | 0 | 0 | 0 | 0 | 0 |

**Hidden eval 指标显著低于 public eval**。Public eval 的 Answer Accuracy 稳定在 83-100%，Hidden A 仅 50%，Hidden B 仅 67%。搜索和代码问答在新领域上的泛化能力存在明显缺口。

---

## 5. 结论

### **FAIL — Hidden Eval 未通过**

| 维度 | 状态 |
|---|---|
| Redline / mvn test | ✅ |
| CODE_LIGHT (Hidden B) | ✅ |
| Hallucination | ✅（0） |
| Hidden A Answer Accuracy | **50%** ❌（需 ≥ 80%） |
| Hidden B Answer Accuracy | **67%** ❌（需 ≥ 80%） |
| Hidden B FG Accuracy | **0%** ❌（需 ≥ 80%） |

**高风险泛化缺口**：Hidden A（供应链/质检文档领域）的检索和问答能力显著弱于已训练的 Kubernetes/实验室/设备维护/合同 SLA 领域。Hidden B 的代码保护题（FG）全军覆没。

---

## 6. 后续建议

1. **最高优先级**：针对 Hidden A 的检索未召回问题，在编译/索引层做通用改善（不基于 hidden 题目调参）
2. 建议用 Hidden A 的 public 等价物（如 PE5 供应链/质检公开题集）作为驱动修复的素材，避免直接针对 hidden 题目修代码
3. Hidden B FG 保护题全面失败需要在 CODE_LIGHT 模式下排查拒答逻辑与保护题判定机制

---

## 7. 明确声明

- [x] 未修改生产代码、测试、prompt、schema、scripts、题集
- [x] 未修改 hidden eval 资产
- [x] 未提交 commit
- [x] 未向任何外部渠道泄露 hidden 题目、答案、关键词、文件名、case id、expected citation
- [x] 模型绑定使用 OpenAI 路由，未自行切换
- [x] Hidden B CODE_LIGHT 编译已验证 writer/reviewer/fixer=0
