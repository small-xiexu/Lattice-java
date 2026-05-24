# 干净数据库全链路验证 — 验收资产设计报告

- 生成时间：2026-05-22
- 执行 Agent：agentB（只读设计）
- 代码修改：**否**
- 数据库操作：**否**
- 适用范围：为后续 agentD 执行“干净数据库全链路验证”提供完整资产清单、Query case 表与操作流程

---

## 1. 现有可复用资料清单

### 1.1 知识源文件（可直接用于 compile）

| 文件 | 路径 | 大小 | 覆盖能力 |
|------|------|------|----------|
| 质量打磨台账 | `docs/quality-progress-and-lessons.md` | 35KB | 普通长文档、多章节拆分、踩坑记录、结构化结论表 |
| 卡券迁移方案 | `docs/卡券三期-迁移方案.md` | 143KB | 大型技术文档、架构决策、MQ 边界、接口契约、多来源引用 |
| 项目启动清单 | `docs/项目启动配置清单.md` | 16KB | 配置参考、端口/容器/数据库具体值、可问答事实 |
| 模型绑定参考 | `docs/模型绑定配置参考.md` | 10KB | 模型绑定配置、具体参数值、连接/模型/绑定分层 |
| OCR 运行态说明 | `docs/文档识别与OCR运行态说明.md` | 3KB | OCR 状态说明、Provider 配置、文件类型识别路径 |
| 场景用例 Excel | `docs/scenarios.xlsx` | 572KB | 结构化表格、行列定位、字段投影、聚合统计、两行对比 |

### 1.2 已有 Query 验收 Case 集

| Case 集 | 路径 | Case 数 | 状态 | 覆盖维度 |
|---------|------|---------|------|----------|
| 固定回归套件 | `docs/test/query-regression-suite.json` | 10 | **已接入回归引擎** | 运行态直答、结构化行/投影/聚合/对比、精确路径、架构边界、配置解释、Deep Research 多跳、无命中保护 |
| SWIP 候选集 | `docs/test/swip-query-eval-candidates.json` | 23 | 已审批，**未接入回归引擎** | SWIP 使用手册+安装手册，21 ANSWERABLE + 2 UNANSWERABLE |

### 1.3 已有验证脚本与工具

| 脚本 | 路径 | 用途 |
|------|------|------|
| Schema 重置 | `scripts/reset-lattice-schema.sh` | 清库重建 lattice schema |
| Query 回归引擎 | `scripts/run-query-regression.mjs` | 读取 suite JSON → POST query → 写 results/metrics/manifest |
| 结构化回归 | `scripts/run-b1-structured-regression.sh` | 运行 13 个结构化测试类 + gate 检查 |
| 知识输入 Smoke | `scripts/smoke-knowledge-input.sh` | mvn compile + 定向单元测试 |
| Redline 扫描 | `scripts/scan-redline.sh` | BLOCKER/REVIEW/ALLOWLIST 分类扫描 |
| 本地开发启动 | `scripts/run-local-dev.sh` | 启动 18082 + 可选 --reset-schema |

### 1.4 已有 Java 测试类（可用于回归验证）

| 测试类 | 覆盖能力 |
|--------|----------|
| `StructuredTableQueryRegressionTests` | 结构化表格 11 个内联 case（CSV/XLSX） |
| `NonCouponComplexDocumentRegressionTests` | 非卡券复杂文档回归（operations-handbook + data-quality-playbook） |
| `GraphSmokeTests` | StateGraph compile/invoke 生命周期 |
| `PostgresSmokeTests` | PostgreSQL vector + FTS smoke |
| `DeepResearchBaselineSchemaTests` | Deep Research v2.6 schema 基线 |
| `AdminCompileFailureRegressionTests` | Compile 失败回归 |

### 1.5 现成 Smoke 源目录（/tmp 下已有）

| 目录 | 内容 | 验证能力 |
|------|------|----------|
| `/tmp/lattice-normal-doc-smoke-src-rerun` | `quality-progress-and-lessons.md` | 单文档 5-topic split |
| `/tmp/lattice-gate-full-smoke-src` | 同上 + `卡券三期-迁移方案.md` | 双文档专题化 compile + document overview collapse |

---

## 2. 干净全链路资料集设计

### 2.1 设计原则

- **最小但完整**：只选能覆盖全部验收维度的最少文件
- **复用现成**：全部来自项目 `docs/` 目录，不新增业务硬编码样例
- **可复现**：文件受 Git 版本控制，任何 clean rebuild 均可还原到相同状态
- **不构造陷阱**：不刻意制造会触发 human review 的“坏文档”；human review 触发作为观察项而非强制验收项

### 2.2 推荐资料清单

| 编号 | 文件名 | 来源 | 覆盖能力 | 预期 compile 行为 |
|------|--------|------|----------|-------------------|
| D1 | `quality-progress-and-lessons.md` | `docs/` | 普通长文档（35KB）、多章节拆分（Gate/踩坑/禁止事项/下一步）、结构化结论表 | Writer 拆 5 个 topic → Reviewer 审查 → Fixer 修复 → 正常入库 |
| D2 | `卡券三期-迁移方案.md` | `docs/` | 大型技术文档（143KB）、架构决策（MQ 边界/入口防腐层）、接口路径、多服务职责描述 | Writer 拆多个 topic + document overview → 正常入库 |
| D3 | `项目启动配置清单.md` | `docs/` | 配置参考（端口/容器/数据库名）、可问答具体值、场景分类说明 | Writer 拆 1-2 个 topic → 正常入库 |
| D4 | `模型绑定配置参考.md` | `docs/` | 模型绑定参数（连接数/模型数/绑定数）、scene/role 映射表、常见漏配错误表 | Writer 拆 1-2 个 topic → 正常入库 |
| D5 | `scenarios.xlsx` | `docs/` | 结构化表格（多列多行）、行列定位、字段投影、聚合统计、两行对比 | 结构化表解析 → Fact Card 生成 → 正常入库 |
| D6 | `文档识别与OCR运行态说明.md` | `docs/` | 短文档（3KB）、OCR Provider 状态说明、文件类型识别路径 | Writer 生成 1 个 topic → 正常入库 |

### 2.3 各维度覆盖矩阵

| 验收维度 | D1 | D2 | D3 | D4 | D5 | D6 |
|----------|----|----|----|----|----|----|
| 正常入库 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 多来源引用 | ✅ | ✅ | — | — | — | — |
| 结构化/表格资料 | — | — | — | — | ✅ | — |
| 可问答事实 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 长文档拆分 | ✅ | ✅ | — | — | — | — |
| 短文档 | — | — | — | — | — | ✅ |
| Markdown | ✅ | ✅ | ✅ | ✅ | — | ✅ |
| Excel | — | — | — | — | ✅ | — |

### 2.4 关于“触发待人工确认”的说明

当前资料集不刻意构造会触发 `needs_human_review` 的文档。原因：
- LLM Reviewer 的审查行为取决于模型实际判断，无法通过文档内容确定性触发
- 强行构造“坏文档”违反“不新增业务硬编码样例”原则
- 历史验收已通过 `PersistArticlesNodeTests` 单元测试覆盖 human review 路径

**替代验证方案**：在验证流程中增加一项“Review Queue 观察”，记录 compile 后 review queue 中有无 `needs_human_review` 项。若有，则额外验证 approve/reject 路径；若无，则记录为“本轮 LLM Reviewer 未标记待确认项”，不影响 gate 通过。

---

## 3. Query 验收 Case 设计

### 3.1 Case 设计原则

- 每个 case 绑定明确的知识来源（资料集 D1-D6）
- `expectedBehavior` 只分 ANSWER / ABSTAIN
- 不设计 hidden eval 泄漏内容；所有 case 均为 PUBLIC_INTERNAL
- `expectedAnswerPoints` 只描述答案应覆盖的信息点，不预设具体措辞
- 覆盖指标按 case 类型分别标注适用项

### 3.2 Case 总表

| caseId | question | 来源 | 行为 | 覆盖指标 |
|--------|----------|------|------|----------|
| E2E-001 | 当前项目的质量打磨阶段有哪些禁止事项？请至少列出 5 条。 | D1 | ANSWER | Answer Accuracy, Recall@5, Citation Accuracy |
| E2E-002 | 卡券三期迁移方案中，dpfm-api-service 的职责是什么？它和 dpfm-callback-service 的边界在哪里？ | D2 | ANSWER | Answer Accuracy, Recall@5, Citation Accuracy |
| E2E-003 | 本地开发环境默认使用什么 PostgreSQL 容器名、端口、数据库名？Redis 呢？ | D3 | ANSWER | Answer Accuracy, Recall@5, Citation Accuracy |
| E2E-004 | 项目启动需要配置几个 LLM 连接、几个模型、几条 Agent 绑定？请列出 compile 场景的三个角色。 | D4 | ANSWER | Answer Accuracy, Recall@5, Citation Accuracy |
| E2E-005 | 当前 OCR / 文档识别功能是否可用？图片和扫描 PDF 能识别吗？ | D6 | ANSWER | Answer Accuracy, Recall@5, Citation Accuracy |
| E2E-006 | scenario_id=100912 这一行的 case_num、场景名称、备注分别是什么？注意不要把 scenario_id 当 case_num。 | D5 | ANSWER | Answer Accuracy, Recall@5, Citation Accuracy |
| E2E-007 | 按 category 统计 scenarios.xlsx 中场景用例各有多少条？ | D5 | ANSWER | Answer Accuracy, Recall@10, Citation Accuracy |
| E2E-008 | 综合说明卡券迁移方案中 FC、DPFM、入口防腐层和 MQ 消费者边界之间的关系，以及这些边界为什么能降低上游改造成本。 | D2 | ANSWER | Answer Accuracy, Recall@10, Citation Accuracy |
| E2E-009 | 质量打磨台账中记录的“多 agent 同时改主链会导致不可归因”这条踩坑结论是什么？后续规则是什么？ | D1 | ANSWER | Answer Accuracy, Recall@5, Citation Accuracy |
| E2E-010 | 模型绑定配置中，embedding 模型漏配 `expectedDimensions` 会导致什么问题？ | D4 | ANSWER | Answer Accuracy, Recall@5, Citation Accuracy |
| E2E-011 | 请查询 xbk_nonexistent_config_key_20260522 的取值和负责人。 | 无来源 | ABSTAIN | Abstain Accuracy, Hallucination |
| E2E-012 | 什么是量子计算中的量子纠缠？请详细说明。 | 无来源 | ABSTAIN | Abstain Accuracy, Hallucination |

### 3.3 逐 Case 详细设计

#### E2E-001 — 禁止事项列举

- **caseId**: `E2E-001`
- **question**: 当前项目的质量打磨阶段有哪些禁止事项？请至少列出 5 条。
- **sourceDocuments**: [D1] `quality-progress-and-lessons.md`
- **expectedBehavior**: ANSWER
- **expectedAnswerPoints**:
  1. 不准多个 agent 同时改主链
  2. 不准在 SWIP clean 库上跑主 baseline
  3. 不准继续调题集来追 pass
  4. 不准写 SWIP/文档/case 特判
  5. 不准跳过 redline/mvn test/baseline 归因
  6. 不准把 rule-based review 描述成 LLM 内容审查
- **expectedCitationSources**: [`quality-progress-and-lessons.md`]
- **coverageMetrics**:
  - Answer Accuracy: 至少覆盖 5 条禁止事项，不得编造
  - Recall@5: `quality-progress-and-lessons.md` 应在 top-5 检索结果中
  - Citation Accuracy: 引用应指向“当前禁止事项”章节
- **mustNotClaim**: 把“当前禁止事项”之外的规则当作禁止事项列出
- **humanJudgement**: 人工确认禁止事项来自台账的“当前禁止事项”章节，事项内容和数量与源文一致
- **visibility**: PUBLIC_INTERNAL

#### E2E-002 — 服务职责边界

- **caseId**: `E2E-002`
- **question**: 卡券三期迁移方案中，dpfm-api-service 的职责是什么？它和 dpfm-callback-service 的边界在哪里？
- **sourceDocuments**: [D2] `卡券三期-迁移方案.md`
- **expectedBehavior**: ANSWER
- **expectedAnswerPoints**:
  1. dpfm-api-service 负责 API 接收、持久化、MQ 发送
  2. dpfm-api-service 不消费自己发出的 MQ
  3. SRKIT/SVC 相关 MQ 消费者统一落在 dpfm-callback-service
  4. dpfm-consumer-service 保留原有消费职责
- **expectedCitationSources**: [`卡券三期-迁移方案.md`]
- **coverageMetrics**:
  - Answer Accuracy: 覆盖 api-service 职责 + callback-service 归属 + consumer-service 保留职责
  - Recall@5: `卡券三期-迁移方案.md` 应在 top-5 检索结果中
  - Citation Accuracy: 引用应指向 MQ 消费者归属相关章节
- **mustNotClaim**: dpfm-api-service 消费自己发出的 MQ；dpfm-consumer-service 新增 SRKIT/SVC 消费者
- **humanJudgement**: 至少覆盖 api-service 职责、callback-service 归属、consumer-service 保留职责三类要点
- **visibility**: PUBLIC_INTERNAL

#### E2E-003 — 本地开发配置查值

- **caseId**: `E2E-003`
- **question**: 本地开发环境默认使用什么 PostgreSQL 容器名、端口、数据库名？Redis 呢？
- **sourceDocuments**: [D3] `项目启动配置清单.md`
- **expectedBehavior**: ANSWER
- **expectedAnswerPoints**:
  1. PostgreSQL 容器名: `vector_db`
  2. PostgreSQL 端口: `5432`
  3. 数据库名: `ai-rag-knowledge`
  4. Redis 容器名: `redis`
  5. Redis 端口: `6379`
- **expectedCitationSources**: [`项目启动配置清单.md`]
- **coverageMetrics**:
  - Answer Accuracy: 5 个配置值全部正确
  - Recall@5: `项目启动配置清单.md` 应在 top-5 检索结果中
  - Citation Accuracy: 引用指向启动配置章节
- **mustNotClaim**: 默认数据库为 ai-rag-knowledge-test；需要启动新容器
- **humanJudgement**: 5 个配置值全部正确且区分开发默认库与测试隔离库
- **visibility**: PUBLIC_INTERNAL

#### E2E-004 — 模型配置数量

- **caseId**: `E2E-004`
- **question**: 项目启动需要配置几个 LLM 连接、几个模型、几条 Agent 绑定？请列出 compile 场景的三个角色。
- **sourceDocuments**: [D4] `模型绑定配置参考.md`
- **expectedBehavior**: ANSWER
- **expectedAnswerPoints**:
  1. 连接数: 2（Chat + Embedding）
  2. 模型数: 2（Chat 模型 + Embedding 模型）
  3. 绑定数: 10
  4. compile 场景角色: writer, reviewer, fixer
- **expectedCitationSources**: [`模型绑定配置参考.md`]
- **coverageMetrics**:
  - Answer Accuracy: 数量全部正确 + compile 三个角色全部正确
  - Recall@5: `模型绑定配置参考.md` 应在 top-5 检索结果中
  - Citation Accuracy: 引用指向配置数量相关内容
- **mustNotClaim**: 绑定数为 7（漏了 deep_research）
- **humanJudgement**: 2/2/10 数量和 compile 三角色均正确
- **visibility**: PUBLIC_INTERNAL

#### E2E-005 — OCR 运行态查询

- **caseId**: `E2E-005`
- **question**: 当前 OCR / 文档识别功能是否可用？图片和扫描 PDF 能识别吗？
- **sourceDocuments**: [D6] `文档识别与OCR运行态说明.md`
- **expectedBehavior**: ANSWER
- **expectedAnswerPoints**:
  1. OCR 可用性取决于 Provider 连接配置是否已配置并启用
  2. 图片或扫描 PDF 的识别是否可用取决于 OCR Provider 状态
  3. 答案应基于运行态说明文档，而非架构设计文档
- **expectedCitationSources**: [`文档识别与OCR运行态说明.md`]
- **coverageMetrics**:
  - Answer Accuracy: 说明 OCR 状态取决于 Provider 配置
  - Recall@5: `文档识别与OCR运行态说明.md` 应在 top-5 检索结果中
  - Citation Accuracy: 引用指向 OCR Provider 连接配置相关内容
- **mustNotClaim**: 在无运行态证据时声称 OCR 已可用；把架构中支持 PDF 解析等同于 OCR 已上线
- **humanJudgement**: 回答需基于运行态文档说明状态，不得无证据断言可用
- **visibility**: PUBLIC_INTERNAL

#### E2E-006 — 结构化行定位

- **caseId**: `E2E-006`
- **question**: scenario_id=100912 这一行的 case_num、场景名称、备注分别是什么？注意不要把 scenario_id 当 case_num。
- **sourceDocuments**: [D5] `scenarios.xlsx`
- **expectedBehavior**: ANSWER
- **expectedAnswerPoints**:
  1. 定位 scenario_id=100912 对应的唯一行
  2. 返回该行 case_num 字段值
  3. 返回该行场景名称字段值
  4. 返回该行备注字段值
  5. 不把 scenario_id 当作 case_num
- **expectedCitationSources**: [`scenarios.xlsx`]
- **coverageMetrics**:
  - Answer Accuracy: 三个字段值均来自同一行且对应正确
  - Recall@5: `scenarios.xlsx` 应在 top-5 检索结果中
  - Citation Accuracy: 引用指向 scenario_id=100912 所在行
- **mustNotClaim**: case_num 等于 100912（除非表格行确实如此）；使用其他行的值
- **humanJudgement**: 三个字段值必须来自同一行，字段名和值对应正确
- **visibility**: PUBLIC_INTERNAL

#### E2E-007 — 结构化聚合统计

- **caseId**: `E2E-007`
- **question**: 按 category 统计 scenarios.xlsx 中场景用例各有多少条？
- **sourceDocuments**: [D5] `scenarios.xlsx`
- **expectedBehavior**: ANSWER
- **expectedAnswerPoints**:
  1. 按 category 列分组统计
  2. 列出每个 category 及其数量
  3. 数量之和等于参与统计的总行数
- **expectedCitationSources**: [`scenarios.xlsx`]
- **coverageMetrics**:
  - Answer Accuracy: 所有 category 均出现，数量正确
  - Recall@10: `scenarios.xlsx` 应在 top-10 检索结果中（聚合需多行数据）
  - Citation Accuracy: 引用指向 category 列及数据行
- **mustNotClaim**: 只列部分 category 却声称完整；用非 category 字段分组；数量凭模型估算
- **humanJudgement**: 分组字段必须是 category；所有 category 都应出现；数量不能凭模型估算
- **visibility**: PUBLIC_INTERNAL

#### E2E-008 — 多跳综合推理

- **caseId**: `E2E-008`
- **question**: 综合说明卡券迁移方案中 FC、DPFM、入口防腐层和 MQ 消费者边界之间的关系，以及这些边界为什么能降低上游改造成本。
- **sourceDocuments**: [D2] `卡券三期-迁移方案.md`
- **expectedBehavior**: ANSWER
- **expectedAnswerPoints**:
  1. FC 是迁移前/旧链路中的履约中台或旧接口契约来源
  2. DPFM 新链路入口通过入口防腐层保持 API path、参数与 FC 兼容
  3. 上游只需切换调用地址，降低改造成本
  4. SRKIT/SVC MQ 消费者统一落在 dpfm-callback-service
  5. dpfm-api-service 只做 API 接收、持久化、MQ 发送
- **expectedCitationSources**: [`卡券三期-迁移方案.md`]
- **coverageMetrics**:
  - Answer Accuracy: 覆盖 FC/DPFM/防腐层/MQ 边界四类关系
  - Recall@10: `卡券三期-迁移方案.md` 应在 top-10 检索结果中（多跳需更广召回）
  - Citation Accuracy: 引用支撑核心结论
- **mustNotClaim**: DPFM 可以改变对外接口契约；dpfm-api-service 消费自己发出的 MQ
- **humanJudgement**: 综合四类关系并解释降低改造成本的依据；至少 1 个可核验证据锚点
- **visibility**: PUBLIC_INTERNAL

#### E2E-009 — 踩坑结论提取

- **caseId**: `E2E-009`
- **question**: 质量打磨台账中记录的“多 agent 同时改主链会导致不可归因”这条踩坑结论是什么？后续规则是什么？
- **sourceDocuments**: [D1] `quality-progress-and-lessons.md`
- **expectedBehavior**: ANSWER
- **expectedAnswerPoints**:
  1. 表现：多 agent 同时改主链时 eval 波动无法定位
  2. 结论：每轮只允许一个代码主变量
  3. 后续规则：同一轮最多一个 agent 改 src/main/java/**；其他 agent 只读或写报告
- **expectedCitationSources**: [`quality-progress-and-lessons.md`]
- **coverageMetrics**:
  - Answer Accuracy: 表现/结论/后续规则三点完整
  - Recall@5: `quality-progress-and-lessons.md` 应在 top-5 检索结果中
  - Citation Accuracy: 引用指向踩坑记录中对应条目
- **mustNotClaim**: 允许多 agent 同时改主链
- **humanJudgement**: 表现、结论、后续规则三者齐全且与台账一致
- **visibility**: PUBLIC_INTERNAL

#### E2E-010 — 配置错误场景

- **caseId**: `E2E-010`
- **question**: 模型绑定配置中，embedding 模型漏配 `expectedDimensions` 会导致什么问题？
- **sourceDocuments**: [D4] `模型绑定配置参考.md`
- **expectedBehavior**: ANSWER
- **expectedAnswerPoints**:
  1. 向量维度默认 2048，与 pgvector 索引不匹配
  2. 当前基线是 2000 维，必须显式设置
- **expectedCitationSources**: [`模型绑定配置参考.md`]
- **coverageMetrics**:
  - Answer Accuracy: 说明维度不匹配问题 + 基线 2000 维
  - Recall@5: `模型绑定配置参考.md` 应在 top-5 检索结果中
  - Citation Accuracy: 引用指向常见漏配/错配章节
- **mustNotClaim**: 不设 expectedDimensions 也能正常工作
- **humanJudgement**: 说明默认 2048 vs 基线 2000 的维度不匹配问题
- **visibility**: PUBLIC_INTERNAL

#### E2E-011 — 无命中拒答（配置项）

- **caseId**: `E2E-011`
- **question**: 请查询 xbk_nonexistent_config_key_20260522 的取值和负责人。
- **sourceDocuments**: 无
- **expectedBehavior**: ABSTAIN
- **expectedAnswerPoints**:
  1. 明确当前知识库没有该配置项的证据
  2. 不编造取值
  3. 不编造负责人
- **expectedCitationSources**: []（无引用）
- **coverageMetrics**:
  - Abstain Accuracy: 应拒答或说明证据不足
  - Hallucination: 不得出现具体取值或负责人
- **mustNotClaim**: 该配置项存在且值为 X；负责人是 X
- **humanJudgement**: 拒答或说明证据不足；给出具体取值或负责人即失败
- **visibility**: PUBLIC_INTERNAL

#### E2E-012 — 无命中拒答（通用知识）

- **caseId**: `E2E-012`
- **question**: 什么是量子计算中的量子纠缠？请详细说明。
- **sourceDocuments**: 无
- **expectedBehavior**: ABSTAIN
- **expectedAnswerPoints**:
  1. 明确当前知识库不包含量子计算相关内容
  2. 不基于模型内部知识回答
  3. 说明无法基于给定证据回答
- **expectedCitationSources**: []（无引用）
- **coverageMetrics**:
  - Abstain Accuracy: 应拒答
  - Hallucination: 不得出现量子纠缠的具体解释
- **mustNotClaim**: 量子纠缠是……；根据量子力学……
- **humanJudgement**: 拒答且不泄露模型内部知识
- **visibility**: PUBLIC_INTERNAL

### 3.4 Case 覆盖矩阵总览

| caseId | 资料 | 类型 | 行为 | Answer Acc | Recall@5 | Recall@10 | Citation Acc | Abstain Acc | Hallucination |
|--------|------|------|------|------------|----------|-----------|-------------|-------------|---------------|
| E2E-001 | D1 | 长文档列举 | ANSWER | ✅ | ✅ | — | ✅ | — | — |
| E2E-002 | D2 | 架构边界 | ANSWER | ✅ | ✅ | — | ✅ | — | — |
| E2E-003 | D3 | 配置查值 | ANSWER | ✅ | ✅ | — | ✅ | — | — |
| E2E-004 | D4 | 数量查值 | ANSWER | ✅ | ✅ | — | ✅ | — | — |
| E2E-005 | D6 | 状态查询 | ANSWER | ✅ | ✅ | — | ✅ | — | — |
| E2E-006 | D5 | 结构化行定位 | ANSWER | ✅ | ✅ | — | ✅ | — | — |
| E2E-007 | D5 | 结构化聚合 | ANSWER | ✅ | — | ✅ | ✅ | — | — |
| E2E-008 | D2 | 多跳推理 | ANSWER | ✅ | — | ✅ | ✅ | — | — |
| E2E-009 | D1 | 踩坑提取 | ANSWER | ✅ | ✅ | — | ✅ | — | — |
| E2E-010 | D4 | 配置错误 | ANSWER | ✅ | ✅ | — | ✅ | — | — |
| E2E-011 | — | 拒答-虚构项 | ABSTAIN | — | — | — | — | ✅ | ✅ |
| E2E-012 | — | 拒答-通用知识 | ABSTAIN | — | — | — | — | ✅ | ✅ |

### 3.5 Case 集与已有回归套件的关系

- E2E-001 至 E2E-010 为新增验收 case，绑定 D1-D6 资料集
- E2E-006/E2E-007 与已有 `Q-STRUCT-ROW-001`/`Q-STRUCT-AGG-001` 类似但使用相同 D5 资料
- E2E-011 与已有 `Q-NO-HIT-001` 功能等价，可用已有 case 替代
- E2E-012 与已有验收报告中的 S4 case（量子纠缠拒答）功能等价
- **推荐**：干净全链路验证时将 E2E case 集与已有 10 个回归 case 合并执行，形成 20+ case 的综合回归

---

## 4. 完整验证流程设计

### 4.1 前置条件检查

执行验证前必须确认：

| 检查项 | 方法 | 期望 |
|--------|------|------|
| Docker 容器可用 | `docker ps --format '{{.Names}}'` | 含 `vector_db` + `redis` |
| JDK 21 | `java -version` | 21.x |
| Maven 可用 | `mvn -version` | 3.9+ |
| 端口未占用 | `lsof -i :18082` | 空（或可接受复用） |
| pgvector 扩展 | `docker exec vector_db psql -U postgres -d ai-rag-knowledge -c "SELECT * FROM pg_extension WHERE extname='vector'"` | 存在 |
| 模型网关可达 | `curl http://localhost:8888/v1/models`（按实际网关地址调整） | 200 |

### 4.2 清库重建步骤

```bash
# Step 0: 确保应用未运行（避免旧连接残留）
# 手动停止 Spring Boot 进程

# Step 1: 清库重建
cd /path/to/lattice-java
./scripts/reset-lattice-schema.sh

# Step 2: 验证空库
docker exec vector_db psql -U postgres -d ai-rag-knowledge \
  -c "SELECT count(*) FROM lattice.articles;"
# 期望: 0

docker exec vector_db psql -U postgres -d ai-rag-knowledge \
  -c "SELECT count(*) FROM lattice.compile_jobs;"
# 期望: 0
```

### 4.3 模型绑定配置检查项

**必须按顺序执行：先建连接 → 再建模型 → 最后建绑定**

| 步骤 | 检查项 | 验证方法 | 期望 |
|------|--------|----------|------|
| 1 | Chat 连接 | `GET /api/v1/admin/llm/connections` | count ≥ 1, providerType=openai_compatible, enabled=true |
| 2 | Embedding 连接 | 同上 | count ≥ 1, providerType=openai_compatible, enabled=true |
| 3 | Chat 模型 | `GET /api/v1/admin/llm/models` | modelKind=chat, enabled=true |
| 4 | Embedding 模型 | 同上 | modelKind=embedding, enabled=true, expectedDimensions 已设 |
| 5 | compile 绑定×3 | `GET /api/v1/admin/llm/bindings` | scene=compile, roles=[writer,reviewer,fixer] 齐全 |
| 6 | query 绑定×3 | 同上 | scene=query, roles=[answer,reviewer,rewrite] 齐全 |
| 7 | deep_research 绑定×4 | 同上 | scene=deep_research, roles=[planner,researcher,synthesizer,reviewer] 齐全 |
| 8 | 向量配置 | `GET /api/v1/admin/vector/config` | vectorEnabled=true, embeddingModelProfileId 非 null |

**一键验证命令**（应用启动后执行）：

```bash
BASE=http://127.0.0.1:18082/api/v1/admin/llm

# 连接数
echo "连接数: $(curl -s $BASE/connections | jq '.count')"  # 期望 2

# 模型数
echo "模型数: $(curl -s $BASE/models | jq '.count')"  # 期望 2

# 绑定数
echo "绑定数: $(curl -s $BASE/bindings | jq '.count')"  # 期望 10

# 绑定分布
curl -s $BASE/bindings | jq '[.items[] | {scene, agentRole}] | group_by(.scene) | map({scene: .[0].scene, roles: map(.agentRole) | sort})'

# 向量配置
curl -s http://127.0.0.1:18082/api/v1/admin/vector/config | jq '{vectorEnabled, embeddingModelProfileId}'
```

### 4.4 上传/导入资料顺序

推荐使用目录型 compile（`sourceDir` 模式），所有资料放入同一个目录：

```bash
# 准备资料目录
mkdir -p /tmp/lattice-e2e-clean-rebuild-src

# 复制资料（用 cp 保证原文件不被修改）
cp docs/quality-progress-and-lessons.md /tmp/lattice-e2e-clean-rebuild-src/
cp docs/卡券三期-迁移方案.md /tmp/lattice-e2e-clean-rebuild-src/
cp docs/项目启动配置清单.md /tmp/lattice-e2e-clean-rebuild-src/
cp docs/模型绑定配置参考.md /tmp/lattice-e2e-clean-rebuild-src/
cp docs/scenarios.xlsx /tmp/lattice-e2e-clean-rebuild-src/
cp docs/文档识别与OCR运行态说明.md /tmp/lattice-e2e-clean-rebuild-src/
```

### 4.5 编译与人工确认步骤

```bash
# Step 1: 发起全量编译
curl -s -X POST http://127.0.0.1:18082/api/v1/compile \
  -H 'Content-Type: application/json' \
  -d '{"sourceDir":"/tmp/lattice-e2e-clean-rebuild-src","incremental":false}'
# 记录返回的 jobId

# Step 2: 轮询编译状态（每 10s 一次，直到终态）
JOB_ID="<上一步返回的 jobId>"
while true; do
  STATUS=$(curl -s http://127.0.0.1:18082/api/v1/admin/compile/jobs/$JOB_ID | jq -r '.status')
  echo "$(date): $STATUS"
  if [ "$STATUS" = "SUCCEEDED" ] || [ "$STATUS" = "FAILED" ]; then
    break
  fi
  sleep 10
done

# Step 3: 检查编译结果
curl -s http://127.0.0.1:18082/api/v1/admin/compile/jobs/$JOB_ID | jq '{
  status, reviewMode, persistedCount, reviewRoute,
  fixAttemptCount, fixDisplayMessage
}'

# Step 4: 检查 Review Queue（关键观察点）
curl -s http://127.0.0.1:18082/api/v1/admin/compile/review-queue | jq '{
  total, needsHumanReviewCount
}'

# Step 5: 若 needsHumanReviewCount > 0
#   a. 查看待确认列表
#   b. 逐条 approve（确认内容质量可接受）
#   c. 或 reject（确认内容质量不可接受）
#   d. 记录 approve/reject 决策及理由

# Step 6: 若 needsHumanReviewCount = 0
#   记录为"本轮 LLM Reviewer 未标记待确认项"
#   不影响 gate 通过判断
```

### 4.6 入库和向量索引检查

```bash
# 文章数
docker exec vector_db psql -U postgres -d ai-rag-knowledge \
  -c "SELECT count(*) AS article_count FROM lattice.articles;"

# 文章状态分布
docker exec vector_db psql -U postgres -d ai-rag-knowledge \
  -c "SELECT review_status, lifecycle, count(*) FROM lattice.articles GROUP BY review_status, lifecycle;"

# Chunk 数
docker exec vector_db psql -U postgres -d ai-rag-knowledge \
  -c "SELECT count(*) AS chunk_count FROM lattice.article_chunks;"

# 源文件数
docker exec vector_db psql -U postgres -d ai-rag-knowledge \
  -c "SELECT count(*) AS source_count FROM lattice.source_files;"

# 向量索引状态
curl -s http://127.0.0.1:18082/api/v1/admin/vector/status | jq '{
  vectorEnabled, dimensionsMatch, indexedArticleCount, lastUpdatedAt
}'

# 关键判断:
# - indexedArticleCount ≈ article_count（所有 passed+ACTIVE 文章都已索引）
# - dimensionsMatch = true
# - review_status 全部为 'passed'（无 needs_human_review 漏入）
# - lifecycle 全部为 'ACTIVE'
```

### 4.7 Query Regression 执行步骤

```bash
# Step 1: 准备合并回归套件
# 方式 A（推荐）: 使用已有回归引擎 + 已有 suite + 追加 E2E case
QUERY_REGRESSION_SUITE=docs/test/query-regression-suite.json \
QUERY_REGRESSION_BASE_URL=http://127.0.0.1:18082 \
  node scripts/run-query-regression.mjs

# Step 2: 执行 E2E case（需先将 E2E case 写入 suite JSON 或单独执行）
# 推荐：将 E2E-001 至 E2E-012 合并到已有 suite 的 cases 数组中
# 或单独创建 docs/test/e2e-clean-rebuild-suite.json

# Step 3: 查看回归结果
cat query_metrics.json | jq '{
  totalCases, passedCases, failedCases,
  casePassRate, httpFailureRate, timeoutRate,
  fallbackRate, llmSuccessRate, averageCitationCoverage
}'

# Step 4: Gate 检查
# - casePassRate ≥ 0.8
# - httpFailureRate = 0.0
# - timeoutRate ≤ 0.05
# - fallbackRate ≤ 0.4
# - llmSuccessRate ≥ 0.4
# - averageCitationCoverage ≥ 0.6

# Step 5: 逐 case 分析失败
cat query_summary.tsv | awk -F'\t' '$NF != "PASS" {print $1, $2, $NF}'
```

### 4.8 失败归因分类方式

每个失败 case 必须归因到以下类别之一：

| 归因类别 | 编码 | 说明 | 处理方式 |
|----------|------|------|----------|
| 检索未命中 | `RETRIEVAL_MISS` | 知识源未被检索到或排名过低 | 检查 source 是否入库、向量索引是否覆盖 |
| 证据不足 | `INSUFFICIENT_EVIDENCE` | 知识源存在但内容不足以回答问题 | 检查 compile 拆分是否覆盖相关段落 |
| LLM 合成错误 | `LLM_HALLUCINATION` | 检索命中但 LLM 答案与源文矛盾 | 检查 prompt、temperature、检索片段质量 |
| Citation 标记错误 | `CITATION_ERROR` | 答案正确但引用指向错误来源 | 检查 citation 标记逻辑 |
| 拒答误判 | `ABSTAIN_FALSE_POSITIVE` | 应回答但拒答 | 检查 outcome guard 阈值 |
| 应拒答未拒答 | `ANSWER_FALSE_POSITIVE` | 应拒答但给出了答案 | 检查无答案保护路径 |
| 基础设施故障 | `INFRA_FAILURE` | HTTP 5xx、超时、模型调用失败 | 检查服务健康状态、模型网关 |
| 结构化解析失败 | `STRUCT_PARSE_ERROR` | Excel 表格未被正确解析 | 检查 document parse 配置、表格结构 |
| 配置缺失 | `CONFIG_MISSING` | 模型绑定/向量配置不完整 | 回到 4.3 节补配置 |

归因流程：
1. 先看 HTTP 状态和 answerOutcome → 排除 `INFRA_FAILURE`
2. 看 retrievalTargets 是否命中 → 区分 `RETRIEVAL_MISS` vs 其他
3. 看 requiredAnswerTerms 覆盖情况 → 区分 `INSUFFICIENT_EVIDENCE` vs `LLM_HALLUCINATION`
4. 看 forbiddenAnswerTerms 是否出现 → 确认 `ANSWER_FALSE_POSITIVE`
5. 看 citation 覆盖率 → 确认 `CITATION_ERROR`

---

## 5. 是否需要新增资料文件

### 5.1 不需要新增的资料

以下资料直接复用 `docs/` 下已有文件，**不需要创建新文件**：
- D1-D6 全部知识源文件

### 5.2 建议新增的文件（本轮不创建）

| 文件名 | 路径 | 内容要求 | 优先级 | 说明 |
|--------|------|----------|--------|------|
| E2E 回归套件 | `docs/test/e2e-clean-rebuild-suite.json` | 包含本报告第 3 节 12 个 E2E case 的 JSON，格式与 `query-regression-suite.json` 一致 | **高** | 供 `run-query-regression.mjs` 直接读取执行 |
| E2E 验证脚本 | `scripts/run-e2e-clean-rebuild.sh` | 一键执行清库→启动→配置检查→compile→入库检查→query regression 的全流程脚本 | 中 | 可基于 `reset-lattice-schema.sh` 和 `run-query-regression.sh` 组合 |
| E2E 合并套件 | `docs/test/full-e2e-merged-suite.json` | 合并 10 个已有回归 case + 12 个 E2E case 的综合套件 | 低 | 可在执行时动态合并，不必预先创建 |

---

## 6. 风险和前置条件

### 6.1 前置条件

| 条件 | 说明 | 不满足时的影响 |
|------|------|---------------|
| 模型网关可用 | Chat 模型和 Embedding 模型的网关必须可达 | **阻塞**：compile 和 query 均无法执行 |
| Docker 容器运行 | `vector_db` + `redis` 必须正常运行 | **阻塞**：应用无法启动 |
| 模型绑定完整 | 2 连接 + 2 模型 + 10 绑定必须全部配置 | **阻塞**：compile 会因缺少 reviewer 绑定而失败 |
| 向量扩展已安装 | pgvector extension 必须存在于数据库中 | **部分阻塞**：compile 可完成但向量索引不可用，query 退化为纯 FTS |
| 应用正常启动 | `/actuator/health` 返回 UP | **阻塞**：所有验证无法执行 |
| 数据库为空 | articles、compile_jobs 等业务表初始为空 | 建议满足：避免旧数据干扰验证结论 |

### 6.2 已知风险

| 风险 | 影响范围 | 缓解措施 |
|------|----------|----------|
| LLM Reviewer 可能不标记任何 `needs_human_review` | E2E case 集中无 human review 路径覆盖 | 已有 `PersistArticlesNodeTests` 单元测试覆盖；记录为观察项而非阻塞项 |
| Embedding 网关组合可能导致向量降级 | Query 向量检索不可用，Recall 下降 | 验证流程中包含向量索引状态检查；降级时 query 仍可通过 FTS 回答，但需标注“向量检索未生效” |
| `scenarios.xlsx` 解析可能受 document parse 配置影响 | 结构化 case E2E-006/E2E-007 可能失败 | 执行前确认 document parse 连接已配置；失败时归因到 `STRUCT_PARSE_ERROR` |
| 单次 LLM 回答波动 | 同一 case 两次执行可能得到不同结果 | 回归引擎支持重放；关键 case 可执行 3 轮取稳定区间 |
| mvn test 预存失败（DocumentParseResultNormalizerTests） | 不影响运行时验证 | 已在 `phase_current_workspace_existing_cases_acceptance_report.md` 中记录；干净验证前建议先 `mvn clean` |
| Review queue 的 `reviewSummary` 字段为 null | 可观测性受限，但不影响实际 compile 执行 | 已在验收报告中记录；不阻塞验证 |

### 6.3 禁止事项（与项目台账一致）

- 不准在验证过程中修改 `src/main/java/**`
- 不准为通过 case 而调整资料内容
- 不准在 SWIP clean 库或其他非空库上执行本验证
- 不准跳过 redline 扫描
- 不准把 rule-based review 描述成 LLM 内容审查
- 不准把弱通过当作质量收口

---

## 7. 执行顺序建议

```
Phase 0: 前置检查（4.1）
  ├── Docker 容器 ✓
  ├── JDK 21 ✓
  ├── 端口可用 ✓
  └── 模型网关可达 ✓

Phase 1: 清库重建（4.2）
  ├── 停止应用
  ├── reset-lattice-schema.sh
  └── 验证空库

Phase 2: 应用启动 + 配置（4.3）
  ├── 启动应用
  ├── 健康检查
  ├── 创建连接×2
  ├── 创建模型×2
  ├── 创建绑定×10
  ├── 配置向量模型
  └── 验证配置完整性

Phase 3: 资料导入 + Compile（4.4 + 4.5）
  ├── 复制资料到 /tmp
  ├── 发起全量 compile
  ├── 轮询 compile 状态
  ├── 检查 Review Queue
  └── Approve/Reject（如有）

Phase 4: 入库检查（4.6）
  ├── 文章数/状态检查
  ├── Chunk 数检查
  ├── 源文件数检查
  └── 向量索引状态检查

Phase 5: Query Regression（4.7）
  ├── 执行已有 10 case 回归
  ├── 执行 E2E 12 case 回归
  ├── Gate 检查
  └── 逐 case 分析

Phase 6: 失败归因（4.8）
  ├── 分类所有失败 case
  ├── 逐类分析根因
  └── 输出归因报告
```

---

## 8. 一句话结论

干净全链路验证的资产已就绪：6 份现成知识源覆盖普通文档/大型文档/结构化表格/配置参考/短文档全部类型，12 个 E2E case + 10 个已有回归 case 覆盖 ANSWER/ABSTAIN/结构化/多跳/拒答全部行为维度，验证流程从清库到归因共 6 个 Phase 可逐步执行。本轮只输出设计，所有文件创建和实际执行留给后续 agentD。
