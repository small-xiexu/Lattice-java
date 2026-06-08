# Hidden Eval 失败 — 抽象能力缺口分析与 Public 等价物修复方向

分析时间：2026-06-07
执行人：agentB（治理/归因 Agent）
类型：只读抽象分析，基于脱敏报告，不读取 hidden 原题

---

## 1. Hidden A / Hidden B 失败类型统计摘要

| 失败类型 | Hidden A | Hidden B | 合计 | 占比 |
|----------|:---:|:---:|:---:|:---:|
| **检索未召回** | 3 | 2 | **5** | 42% |
| **证据已召回但回答漏点** | 2 | 2 | **4** | 33% |
| **跨文档/跨文件串联不足** | 1 | 1 | **2** | 17% |
| 引用错误 | 1 | 0 | 1 | 8% |
| Hallucination（编造） | 0 | 0 | 0 | 0% |

**正面指标**：
- Hallucination = 0（系统在完全未见过资料上仍不编造）✅
- 已有拒答能力保持（Hidden A 3 题 INSUFFICIENT_EVIDENCE 中部分为正确拒答）

**与 Public Eval 的核心差异**：
- Public eval（PE1-PE4）Answer Accuracy 稳定在 83-100%
- Hidden A 仅 50%，Hidden B 仅 67%
- 差异来源不是 Hallucination（均为 0），而是检索召回和证据消费的泛化能力不足

---

## 2. 最高优先级缺口排名

### Gap 1（最高优先级）：新领域文档的检索召回泛化不足

| 维度 | 值 |
|------|-----|
| 影响面 | Hidden A 3 例 + Hidden B 2 例 = **5 例（42%）** |
| 表现 | 在新领域（与 PE1-PE4 不同）上，FTS/LIKE/Vector 通道无法有效召回目标证据 |
| 与 Public Eval 的关系 | PE1-PE4 的检索召回已稳定（Recall@10 10/10, Search 6/6），但泛化到新领域时下降明显 |
| 是否为 Public Eval 过拟合信号 | **是**——PE1-PE4 在反复修复中可能对 Kubernetes/实验室/设备/合同领域的术语分布产生了隐式偏好 |

**抽象归因**（不引用 hidden 内容）：
- 新领域的术语、缩写、领域特定表达在 tokenization 阶段可能未被有效拆分
- 新领域的文档结构与已训练领域不同（如更密集的表格、更长的条款文本、不同的标题风格）
- 新领域的 Writer 输出质量可能受限于 LIGHTWEIGHT_SMALL_DOC 路径（Hidden A 仅生成 2 篇 article）

### Gap 2（高优先级）：CODE_LIGHT 模式下保护题（FG）机制缺失

| 维度 | 值 |
|------|-----|
| 影响面 | Hidden B FG Accuracy **0/3（0%）** |
| 表现 | 代码类保护题（数值保护、字段区分、拒答）全部失败 |
| 与 Public Eval 的关系 | PE1-PE4 FG Accuracy 均为 3/3（100%），但 Hidden B 的 CODE_LIGHT 路径完全失败 |
| 是否为 CODE_LIGHT 特有 | **是**——DOCUMENT 模式下的 FG 保护逻辑在 CODE_LIGHT 模式下可能失效 |

**抽象归因**（不引用 hidden 内容）：
- CODE_LIGHT 跳过 writer/reviewer/fixer，每文件作为独立 article
- 代码保护题通常需要跨文件关联或从多个 article 中区分正确/错误字段
- 当前 CODE_LIGHT 的 article 是源码原文，LLM 在回答时需要从中提取结构化事实
- FG 保护题的"不要让 sibling 字段抢占"逻辑可能依赖 terminal unit/fact card 的证据粒度，而 CODE_LIGHT 不生成 fact card

### Gap 3（中优先级）：证据已召回但 LLM 消费不完整

| 维度 | 值 |
|------|-----|
| 影响面 | Hidden A 2 例 + Hidden B 2 例 = **4 例（33%）** |
| 表现 | 证据已被检索到 fused top-K，但 LLM 生成的答案未完整覆盖问题要求 |
| 与 Public Eval 的关系 | PE3 FQ3（违约金计算）曾出现类似问题（PARTIAL），但整体比例低于 Hidden |
| 是否为新领域特有 | 部分——新领域的更复杂问题结构可能暴露 LLM 消费证据的瓶颈 |

**抽象归因**（不引用 hidden 内容）：
- 新领域问题可能需要从多个证据片段中提取和组合信息
- LLM 在证据片段之间做信息聚合时可能遗漏部分要求
- 问题结构更复杂时（如多条件判断、多实体比较），LLM 的逐片段处理策略不足

### Gap 4（低优先级）：跨文档/跨文件串联不足

| 维度 | 值 |
|------|-----|
| 影响面 | Hidden A 1 例 + Hidden B 1 例 = **2 例（17%）** |
| 表现 | 需要跨多个 source/article 组合信息的问题回答不完整或错误 |
| 与 Public Eval 的关系 | PE2 FQ8（跨文档组合）、PE3 FQ10（合同+补充协议）已通过，但泛化到新领域时下降 |
| 是否为 CODE_LIGHT 放大 | 可能——CODE_LIGHT 下每文件独立 article，无 LLM 合成的跨文件文章 |

---

## 3. 每个缺口对应的 Public 等价物方向

### Gap 1：新领域检索召回 → PE5 供应链/质检 Public Eval

**方法**：设计一套全新的 public eval（PE5），使用与 PE1-PE4 完全不同的业务领域（如供应链管理/质量检验），覆盖：
- Markdown 制度文档（含领域特定术语和缩写）
- YAML 结构化指标配置
- XLSX 质检记录表
- CSV 供应商评估表

**验证目标**：
- 新领域文档的 Writer 输出质量（是否产生足够的 article）
- FTS/LIKE token 对新领域术语的覆盖
- Recall@10 在新领域上是否 >= 85%

**为什么不会过拟合到 hidden**：PE5 使用完全不同的具体业务场景和术语，只验证"新领域检索召回"这个抽象能力。

### Gap 2：CODE_LIGHT FG 保护题 → 扩展 java-codebase-public-eval 的 FG 题集

**方法**：在现有 java-codebase-public-eval fixture 中增加专门针对 CODE_LIGHT 模式的保护题：
- 数值保护：字段 A 的值 vs 字段 B 的值（如 `maxRetryCount` vs `connectionTimeout`）
- 配置区分：application-dev.yml vs application-prod.yml 的同名配置项
- 拒答保护：fixture 中不存在的内容（如"数据库密码是什么"——fixture 不含密码）

**验证目标**：
- CODE_LIGHT 下 FG Accuracy >= 2/3

**为什么不会过拟合到 hidden**：使用已有的公开 java-codebase-public-eval fixture，只增加通用保护题类型。

### Gap 3：证据消费不完整 → PE3/PE4 中增加多条件复合问题

**方法**：在现有 PE3（合同）或 PE4（设备维护）中增加更复杂的问题变体：
- "如果 X 条件满足且 Y 条件不满足，结果是什么"（多条件组合）
- "列出所有满足条件 A 和条件 B 的条目"（多实体过滤）
- 验证 LLM 是否从已召回的证据中提取完整信息

**验证目标**：
- 复合问题 Answer Accuracy >= 80%

### Gap 4：跨文档串联 → PE5 中增加跨 source 组合题

**方法**：在 PE5 中设计需要跨 2-3 个 source 组合信息的问题：
- "根据《供应商管理办法》和《质量检验标准》，A 类供应商的审核频率和检验项目分别是什么"
- 验证 citation 是否分别指向正确的 source

**验证目标**：
- 跨文档组合题 Answer Accuracy >= 75%

---

## 4. 不要触碰 Hidden 的原因说明

| 原因 | 说明 |
|------|------|
| **治理协议红线** | `hidden_eval_governance_protocol.md` 第四节明确禁止 agentB 读取 hidden 题目和答案 |
| **防止过拟合** | 如果基于 hidden 原题修代码，系统将失去在新领域上的泛化验证能力 |
| **Public eval 已有的教训** | PE1-PE4 的反复修复已经产生了一定的领域偏好（Search 在 PE1-PE4 上 6/6，但 Hidden A 检索召回下降明显） |
| **修复路径存在** | 所有 4 个缺口都可以通过设计新的 public eval 或扩展现有 public eval 来驱动修复，不需要触碰 hidden |

---

## 5. 下一步建议（只写一个最小动作）

### **设计并落地 PE5：供应链/质检领域 Public Eval**

**选择理由**：
1. 直接针对最大缺口——Gap 1（检索未召回，5 例，42%）
2. 改善检索召回会连带改善 Gap 3（证据消费）和 Gap 4（跨文档串联）——证据召回更好，LLM 有更多素材可消费
3. PE5 已经在 eval-validation-roadmap 中规划为第 5 套 public eval，可以直接推进
4. 完全使用公开资料，不触碰 hidden
5. 如果 PE5 通过后重新跑 Hidden A，预期检索召回应有显著改善

**具体步骤**（由 agentB 设计，agentC 落地，agentD 验证）：
1. agentB 设计 PE5 题集（~15 题问答 + 6 搜索 + 3 保护题），聚焦供应链管理/质量检验领域
2. agentC 生成资料包（Markdown 制度 + YAML 指标 + XLSX 记录表 + CSV 评估表 + PDF 审核报告）
3. agentD 清库 → 导入 PE5 → 编译 → 执行验收
4. 如果 PE5 通过，重新跑 Hidden A 验证泛化改善

**Gap 2（CODE_LIGHT FG）应作为独立的后续动作**，不在 PE5 中混修——PE5 是 DOCUMENT profile 的泛化验证，CODE_LIGHT FG 是不同的问题维度。

---

## 6. 明确声明

- [x] 未读取 hidden eval 题目、答案、关键词、文件名、case id、expected citation
- [x] 未修改生产代码、测试、prompt、schema、scripts
- [x] 未修改题集
- [x] 未提交 commit
- [x] 未向任何渠道泄露 hidden 内容
- [x] 所有分析仅基于脱敏报告中的指标和失败类型分布
- [x] Public 等价物方向均为新增或扩展现有 public eval，不引用 hidden case 细节
- [x] 推荐的唯一下一步动作为设计 PE5 public eval（纯 public，不触碰 hidden）
