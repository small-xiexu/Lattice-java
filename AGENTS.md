# AGENTS.md

## 项目级环境约定

- 语言：始终使用简体中文回复
- JDK：本项目默认使用 **JDK 21**，不要使用 Java 8 运行 Maven 或测试
- Maven 全局本地仓库：`/Users/sxie/maven/repository`
- Maven 默认策略：优先沿用用户全局 Maven 配置与上述全局本地仓库，不要为项目长期保留独立本地仓库
- 项目级 `maven-settings.xml`：仅在排查全局镜像、网络或仓库污染问题时临时创建；问题确认后应删除
- 当前状态补充：由于全局 `alimaven` 握手不稳定，`.codex/maven-settings.xml` 已临时恢复用于开发验证；如全局镜像恢复正常，可再删除
- 临时缓存目录：`.m2/`、`.m2-central/` 仅用于一次性验证，不是项目必需目录，可直接删除
- 数据库建表策略：项目不再使用 Flyway；首次启动、DDL 变化或需要清库时，显式执行 `./scripts/reset-lattice-schema.sh`（DDL 以 `src/main/resources/db/schema.sql` 为准）
- 本地开发启动入口：联调页面与静态资源时优先使用 `./scripts/run-local-dev.sh`（可选 `--reset-schema`，固定 `local-dev` + `lattice` schema + 默认 `18082`）
- PostgreSQL 默认依赖：使用现有 Docker 容器 `vector_db`（`0.0.0.0:5432->5432`），默认数据库为 `ai-rag-knowledge`
- Redis 默认依赖：使用现有 Docker 容器 `redis`（`0.0.0.0:6379->6379`）
- 日常开发、测试、回归默认直接复用上述现有容器；除非用户明确要求，不要自行 `docker compose up` 新的 PostgreSQL / Redis 实例，避免端口冲突和环境漂移
- 后台真实验收默认优先使用 `openai` 路由完成编译、审查与 query 回归；`claude` 仅在用户明确要求验证 Claude 路由，或需要专项核验 Anthropic 兼容性时再启用，避免高 token 成本成为日常回归负担

## 当前基线结论

- Spring Boot 基线：`3.5.1`
- Spring AI 基线：`1.1.2`
- Spring AI Alibaba Graph 基线：`1.1.2.0`
- Sa-Token Starter：`cn.dev33:sa-token-spring-boot3-starter:1.45.0`
- PostgreSQL Driver：`org.postgresql:postgresql:42.7.7`
- pgvector Java 坐标：`com.pgvector:pgvector:0.1.6`
- Embedding 基线：`text-embedding-3-small + vector(1536)`
- Hibernate 版本：`6.6.18.Final`

## 实施约定

- Git 分支策略：后续一切开发、修复、联调与验收默认直接在 `main` 分支进行；除非用户明确要求，否则不要新建任何功能分支、修复分支或临时分支
- 重构策略：后续一切开发默认按新项目进行重构，不需要兼容老逻辑、保留旧行为，也不需要提供旧数据迁移、历史脚本兼容或平滑升级方案；只有在用户明确提出兼容要求时，才额外评估并设计兼容方案
- Deep Research 绑定策略：运行期默认按 fail-closed 执行；启动期校验与临时降级开关的具体使用方式，以项目启动配置清单为准
- 向量治理入口：向量开关/模型配置与索引重建统一走 `/api/v1/admin/vector/{config,status,rebuild}`（实现参考 `src/main/java/com/xbk/lattice/api/admin/AdminVectorIndexController.java`），不要在业务代码中硬编码向量参数
- Query/检索/回答逻辑治理红线：打磨查询质量时，**零容忍** 任何面向特定业务域、特定文档、特定文件名、特定术语、特定问题样式、特定样例字符串的硬编码分支、白名单、关键词特判、答案模板或兜底文案；一经发现必须删除，不允许以“临时止血”“回归样例保护”“只在测试里复现”之类理由保留在主链实现中。只能保留最小通用文本结构规则（如引用格式、路径/URL/数字/表格等基础解析）、通用证据排序规则与通用提示词约束。若效果不佳，优先回到编译抽取、结构化证据、检索排序与通用提示词层修正，不要在 query 主链里为某份资料“教答案”。
- 当前运行、验收与回归入口以 [`docs/项目启动配置清单.md`](/Users/sxie/xbk/Lattice-java/docs/%E9%A1%B9%E7%9B%AE%E5%90%AF%E5%8A%A8%E9%85%8D%E7%BD%AE%E6%B8%85%E5%8D%95.md)、[`README.md`](/Users/sxie/xbk/Lattice-java/README.md) 与 [`docs/项目全流程真实验收手册.md`](/Users/sxie/xbk/Lattice-java/docs/%E9%A1%B9%E7%9B%AE%E5%85%A8%E6%B5%81%E7%A8%8B%E7%9C%9F%E5%AE%9E%E9%AA%8C%E6%94%B6%E6%89%8B%E5%86%8C.md) 为准
- `B5-B9` 阶段历史文档、Graph 完整设计台账、专题技术方案与一次性回归附录均已退场，不再作为当前推进入口
- 若继续推进后续迭代，先读取上述运行/验收文档；如需多阶段执行，先新建或指定新的执行清单，再开始实施
- B1 默认不引入向量 ORM 映射；向量字段写入和检索后置到 B3，优先使用 `JdbcTemplate/jOOQ + SQL`
- 涉及 PostgreSQL 本机端口访问、Docker `exec` 或外网依赖下载时，注意当前环境可能需要额外权限
- 若做后台/真实链路验收，默认先走 OpenAI 成本更可控的模型绑定完成主回归；只有在“验证 Claude 角色选路是否仍可用”这一类专项场景下，才临时切到 Claude 绑定并在验收后恢复

## Query 红线通用规则定义

- 通用文本结构规则：仅限路径、URL、文件后缀、数字、Markdown、JSON、表格、引用标记、代码符号等与业务无关的格式解析。
- 通用证据排序规则：只能基于证据质量、覆盖度、结构完整度、引用可用性、来源优先级等通用信号，不得绑定具体业务域、文档标题、文件名、术语或样例问题。
- 通用提示词约束：只能约束回答必须基于证据、不得编造、引用格式、缺证处理等通用行为，不得写入特定资料的答案模板或业务结论。
- allowlist candidate：路径、URL、数字、Markdown、JSON、表格等通用解析命中只能标记为候选，不得静默放过；是否保留必须经过人工确认。
- 红线硬编码：任何依赖具体业务域、具体文档、具体文件名、具体术语、具体问题问法、具体样例字符串、具体答案片段来改变检索、路由、重写、排序、生成或兜底行为的分支，都按红线处理。

## Query / Answer 修复执行禁令

- 任何 Query、检索、回答、fallback、citation、rerank、AnswerGeneration 相关修复，默认先分析后改代码；未输出失败归因报告前，不准修改 `src/main/java`。
- 每轮只允许处理一个明确根因，不准一轮同时修多个 RC，不准为了“顺手”扩范围。
- 默认不准修改 `src/test/java`；只有用户明确确认“测试断言过期”或“测试预期需要更新”时，才允许改测试。
- 默认不准修改 `scripts/scan-redline.sh`、`AGENTS.md`、`CLAUDE.md`、redline allowlist。
- `AnswerGenerationService`、fallback outcome、evidence selector、snippet selector、fallback conclusion builder 属于高危主链；修改前必须说明：失败类型、通用修复点、为什么不是 case 特判、影响哪些测试、如何验证没有 outcome 过度升级。
- 禁止为了某个测试样例加入具体业务词、文档名、文件名、答案片段、问题问法判断。
- 中文问法识别只能使用配置化、通用语言信号；不得在 Java 主链硬编码。

## 多 Agent 协作规范

- 项目允许并行使用多个 agent，但必须按职责隔离；同一轮最多只能有一个 agent 修改 `src/main/java/**`。
- 每轮代码修复只能有一个可归因变量。若 agentA 正在修 RRF，其他 agent 不准同时修 prompt、fallback、citation、runner、题集或模型配置。
- Query / Answer / Retrieval / Rerank / Citation / Compiler 主链修复，不允许多个 agent 并行改代码。

| Agent | 默认职责 | 是否允许修改生产代码 |
|---|---|---|
| 架构/质量顾问 | 阅读报告、判断风险、拆分下一步、生成提示词、控制红线范围 | 否 |
| agentA | 代码执行 Agent；只按明确提示词执行一个最小代码修复 | 是；每轮只能有一个 |
| agentB | 治理/链路分析 Agent；做只读分析、设计报告、风险判断 | 否 |
| agentC | 报告清理/文档 Agent；整理报告、状态台账、文档说明 | 否，除文档/报告 |
| agentD | 验证/测试 Agent；运行 redline、`mvn test`、query baseline、业务 eval 并输出验证报告 | 否，除报告 |

### 并行规则

- 允许多个只读 agent 并行做失败归因、治理分析、报告清理规划、文档整理、数据库只读审计。
- 允许验证/测试 Agent 在代码修复完成后运行门禁；代码修复进行中不得擅自清库、重建或跑会干扰归因的业务 eval。
- 如果已有一个 agent 正在修改主链代码，其他 agent 禁止修改：
  - `src/main/java/**`
  - `src/test/java/**`
  - `src/main/resources/**`
  - `scripts/**`
  - `docs/test/**`
  - `.claude/**`
- 报告清理 Agent 在代码修复进行中只能做清理规划，不直接删除仍可能被当前修复引用的报告。

### 提示词分发规则

- 架构/质量顾问输出提示词时必须明确写明：交给哪个 agent、本轮目标、允许修改范围、禁止修改范围、是否允许改代码、是否允许跑测试、是否允许清库/重建、输出报告名称、redline / `mvn test` / baseline 要求。
- 代码执行 Agent 的交付物命名为 `*_fix_result_report.md`。
- 验证/测试 Agent 的交付物命名为 `*_verification_report.md` 或 `*_gate_report.md`。
- 治理/链路分析 Agent 的交付物命名为 `*_analysis_report.md` 或 `*_design_report.md`。
- 清理/文档 Agent 的交付物命名为 `*_cleanup_report.md`、`*_cleanup_plan.md` 或 `*_status.md`。

### 失败处理

- redline `BLOCKER > 0`：所有 agent 停止准确率、baseline、eval 和测试修复；下一步只允许处理 redline BLOCKER。
- `mvn test` 编译失败：停止 baseline / eval；下一步只允许处理编译失败。
- baseline 或业务 eval 下降：先判断是否由本轮唯一代码改动造成；若是，优先回退或缩小本轮改动，不得继续扩大修改。
- 报告显示“预期通过”但未端到端实测：不得标记完成，必须进入验证轮。

### 质量打磨台账

- 后续推进质量打磨、Query/SWIP eval、baseline 修复、多 agent 协作前，必须先读取 [`docs/quality-progress-and-lessons.md`](/Users/sxie/xbk/Lattice-java/docs/quality-progress-and-lessons.md)。
- 分配 agent 或选择模型前，必须先读取 [`docs/multi-agent-model-routing-guide.md`](/Users/sxie/xbk/Lattice-java/docs/multi-agent-model-routing-guide.md)；模型与 agent 分工由用户按该手册手动指定，不允许执行 agent 自行决定。
- 如果本轮改变了当前 gate、下一步计划、踩坑结论或 agent 分工，必须同步更新该文档。
- 该文档是质量打磨阶段的进度台账，不替代计划文件；如果用户指定 `docs/**/plans/*.md`，仍以计划文件为唯一进度台账。

## 质量工程推进流程

- 后续推进项目质量、Query 准确率、检索/回答链路或 bad case 修复时，默认必须按以下顺序执行，不允许跳步。
- 第一步：红线扫描。先运行 `bash scripts/scan-redline.sh special_cases_report.md`，必须确认 `BLOCKER=0`；不允许通过修改 `scripts/scan-redline.sh`、扩大 allowlist 或删除扫描规则来通过门禁。
- 第二步：基础测试。运行 `mvn test`；若测试失败，先修通用工程问题，不进入准确率调优。
- 第三步：干净基线评测。运行现有 query regression，输出 `baseline_report.md`；本阶段只允许评测，不允许修改 `src/main/java`。
- 第四步：失败类型归因。每个失败 case 必须归入且只能归入以下类别之一：资料缺失、编译抽取缺失、chunk 切分问题、检索未召回、rerank 排序低、证据已召回但回答漏点、引用错误、应拒答但编造、多证据冲突未处理。
- 第五步：通用能力修复。只能基于失败类型修通用能力，不允许针对单个 case 写保护逻辑；优先级依次为证据召回、证据排序、citation binding、answer grounding、prompt 和表达。
- 第六步：回归验证。每次修复后必须重新运行 redline scan、`mvn test`、query regression，并输出修复前后指标对比。

## 准确率指标定义

- 不得只使用单一 Answer Accuracy 判断项目质量；每次 query regression 至少输出以下指标。
- Answer Accuracy：最终答案是否正确。
- Recall@5：正确证据是否进入前 5 条候选。
- Recall@10：正确证据是否进入前 10 条候选。
- Citation Accuracy：引用是否真实支撑答案。
- Abstain Accuracy：证据不足时是否正确拒答。
- Hallucination Count：无证据编造次数。
- 若 Recall@10 不达标，优先修检索、编译、chunk、fact card、source_ref、rerank，不得通过 prompt 或答案模板硬补。

## Eval 使用规则

- 评测集分为 public eval 与 hidden eval。
- public eval：允许 AI 查看，用于调试和失败归因。
- hidden eval：不允许 AI 查看，只用于最终验收。
- 禁止将 hidden eval 的问题、标准答案、关键词、文件名、case id、expected citation 写入 `src/main/java`、`src/main/resources`、prompt 模板、`config/rules.yaml`、`config/synonyms.yaml`、SQL 初始化数据或回归脚本。
- 若 hidden eval 准确率明显低于 public eval，优先判断是否存在过拟合或测试集污染。

## Bad Case 修复路径

- 先复现并记录到 `eval/bad_cases.jsonl` 或新的执行清单，不在 query 主链直接写保护逻辑。
- 若问题来自知识缺失，优先补充或修正 `kb/sources`。
- 若问题来自术语归一，优先迁移到 `config/synonyms.yaml`。
- 若问题来自可配置规则，优先迁移到 `config/rules.yaml` 或 `compiler/config`。
- 若问题来自抽取质量，优先修正编译抽取、结构化证据、索引字段或证据排序。
- 若问题来自回答约束，优先调整通用提示词或通用后处理规则，不写特定答案模板。
- 修复后必须用 bad case 回归验证；未经验证不得标记完成。
