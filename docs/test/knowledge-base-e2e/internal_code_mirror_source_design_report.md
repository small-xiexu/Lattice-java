# 内部代码镜像源设计报告

## 结论

不建议恢复旧 `SERVER_DIR`。应新增一个明确的资料源类型，例如 `INTERNAL_MIRROR`，定位为“由公司内网同步系统维护、由 Lattice 只读扫描的内部镜像源”。

该能力不应复用 `UPLOAD`，因为它不是一次性资料包；不应复用 `GIT`，因为 Lattice 不负责拉取远端仓库、凭据和分支；也不应只做新的 `sourceDir` 编译入口，因为那会绕过资料源台账、manifest、同步历史、跳过未变化、来源追溯和后续权限治理。

推荐路径：`INTERNAL_MIRROR` 作为 `knowledge_sources.source_type` 的新类型；物化阶段只读扫描受控镜像目录，按通用过滤规则复制到 staging，再复用现有 `SourceUploadService.acceptMaterializedSource(...) -> compile job -> source_files/source_chunks/fact_cards/AST graph/query` 主链。

## 背景与问题定义

目标场景是内网私有 Java 项目持续同步到服务器固定镜像目录，知识库需要：

- 递归扫描镜像目录。
- 持续发现新增、修改、删除。
- 支持后续问答、检索、引用、编译、Java AST 图谱。
- 不把公开 Git 作为前提。
- 不把具体项目名、业务域、目录名、问题样例写入生产逻辑。

核心问题不是“让编译接口能读一个目录”，而是“把一个长期存在、持续变化、需要治理和追溯的目录注册为资料源”。

## 现状盘点

| 事实 | 当前实现位置 | 判断 |
|---|---|---|
| 资料源后台白名单只有 `UPLOAD` + `GIT` | `AdminSourceController.ALLOWED_SOURCE_TYPES` | 已无 `SERVER_DIR` |
| Git 资料源创建、校验、同步已存在 | `SourceSyncWorkflowService.createGitSource/syncSource` | 可复用同步工作流模式 |
| Git 物化支持私有仓库凭据 | `SourceMaterializationService.resolveCredentials` + `SourceCredentialService` | 凭据能力已具备，但只服务 Git 拉取 |
| `UPLOAD` 与物化后的 `GIT` 最终都走统一上传/编译链路 | `SourceUploadService.acceptUpload/acceptMaterializedSource` | 新镜像源应复用此链路 |
| `sourceDir` 是编译输入目录 | `CompileController`、`IngestSourcesNode`、`CompileRequest` | 不是资料源入口 |
| 编译摄入已支持 Java 后端常见文本格式 | `IngestNode.SUPPORTED_TEXT_FORMATS` | 包含 `java/xml/properties/yml/yaml/json/js/css/html/sh/py/csv/md/txt` 等 |
| Java AST 图谱只处理 `.java` | `ExtractAstGraphNode`、`AstGraphExtractService` | Java 源码有结构化增强能力 |
| 当前编译摄入会跳过部分产物目录 | `IngestNode.SKIPPED_DIRECTORIES` | 现有仅 `.git/target/dist/node_modules`，镜像源需要更完整策略 |
| 当前 bundle manifest 基于相对路径、文件大小、内容 hash | `BundleFeatureExtractor` | 适合作为镜像源增量基础，但要先过滤再计算 |
| 旧 `SERVER_DIR` 已验证移除 | `server_dir_source_removal_verification_report.md` | 不应按旧概念回滚 |

关键差异：`sourceDir` 能让编译跑起来，但不会天然形成一个稳定资料源；`INTERNAL_MIRROR` 应先解决“来源、边界、同步、增量、审计、删除”的问题，再把结果交给编译。

## 方案选型对比

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| 恢复旧 `SERVER_DIR` | 改动看似最小 | 语义历史包袱重，容易退回“任意服务器目录”风险；与已完成移除结论冲突 | 不建议 |
| 复用 `UPLOAD` | 可复用现有上传链路 | 无长期源配置、无刷新策略、无镜像边界、无删除同步语义 | 不适合 |
| 复用 `GIT` | 有 source/sync/credential 模型 | 内部镜像不等于 Git remote；很多镜像目录可能无 `.git` 或不允许应用拉取远端 | 不适合 |
| 只新增 compile `sourceDir` 入口 | 实现最薄 | 绕过 source id、manifest、history、source snapshots、UI 管理和后续权限 | 不建议 |
| 新增 `INTERNAL_MIRROR` source type | 概念清晰；可复用现有物化到 staging + compile 主链；便于持续同步与治理 | 需要新增受控目录配置、扫描过滤、快照差异、删除处理 | 推荐 |

## 推荐架构

### 组件分层

| 层 | 职责 |
|---|---|
| 外部镜像同步器 | 公司现有同步机制，负责把私有项目同步到服务器目录。Lattice 不拉远端、不保存仓库凭据。 |
| 镜像根配置 | 运维配置一个或多个允许扫描的根目录引用，如 `mirrorRootRef`，不允许 API 直接提交任意绝对路径。 |
| `INTERNAL_MIRROR` source | 每个资料源绑定一个镜像根引用和一个相对项目路径，形成稳定边界。 |
| 镜像物化服务 | 校验路径、过滤目录、计算 manifest、复制纳入范围的文件到 staging，输出物化元数据。 |
| 统一同步链路 | 复用 `acceptMaterializedSource` 创建 `SourceSyncRun`、跳过未变化、提交 compile job。 |
| 编译与检索 | 复用现有 `source_files/source_file_chunks/fact_cards/graph_entities/query` 链路。 |

### Source 配置建议

`INTERNAL_MIRROR` 的配置建议只表达通用能力：

| 字段 | 含义 |
|---|---|
| `mirrorRootRef` | 指向服务端配置里的镜像根，不是任意绝对路径 |
| `projectPath` | 镜像根下的相对项目路径 |
| `includeGlobs` | 可选纳入规则，默认按代码知识库通用文件类型 |
| `excludeGlobs` | 可选排除规则，在默认排除规则上追加 |
| `scanLimits` | 最大文件数、最大总字节、单文件最大字节、最大深度 |
| `refreshPolicy` | 手动、定时、webhook 触发策略 |
| `readinessPolicy` | 可选稳定标记或两次扫描一致性策略 |

生产逻辑不得出现具体项目名、仓库名、业务模块名、业务术语、题集问题或答案片段。

## 目录同步边界

推荐边界：全局允许多个镜像根，但每个资料源只绑定一个项目根。

| 问题 | 建议 |
|---|---|
| 单个固定根目录 | 可作为最小部署形态，例如只配置一个 `mirrorRootRef` |
| 多项目根目录列表 | 支持，作为运维 allowlist；适合不同团队、不同挂载盘、不同同步器 |
| 根目录下全量递归扫描 | 只允许在“已注册资料源的项目根”内递归；不建议把多项目总目录作为一个大 source 全量扫描 |
| 多项目批量接入 | 可以提供“发现一级子目录并批量创建 source”的管理能力，但最终仍是一项目一 source |
| 符号链接 | 默认不跟随；如必须支持，只能跟随到 allowlist 内部，并记录真实路径 |
| 路径校验 | `projectPath` 必须 canonicalize 后仍位于 `mirrorRootRef` 内，拒绝 `..`、绝对路径、越界软链 |

这样做能避免一个同步根下的多个项目互相污染，也方便按 source 查看同步历史、失败、删除、权限和问答范围。

## 更新机制

推荐分三层推进：

| 阶段 | 机制 | 定位 |
|---|---|---|
| MVP | 手动刷新 | 最小可控，便于验证镜像边界、过滤、manifest、编译结果 |
| 标准能力 | 定时轮询 | 对持续同步最稳，按 source 配置间隔、错峰、限流、失败退避 |
| 增强能力 | Webhook | 只作为触发器，不作为变更事实来源；收到事件后仍重新扫描并计算 manifest |

Webhook 不应接收任意目录路径，只能接收 `sourceCode` 或受控项目标识。触发请求需要鉴权、幂等、限频；如果当前 source 有活跃 run，应合并或跳过，不并发扫描。

### 镜像稳定性

真实内网镜像常见风险是同步器正在写文件时 Lattice 开始扫描。建议至少支持一种稳定策略：

- 最佳：外部同步器写入临时目录后原子切换 `current` 指针或项目目录。
- 可接受：外部同步器写入通用 ready marker，Lattice 扫描前后确认 generation 未变化。
- 兜底：Lattice 做两次 manifest 快照；若短时间内不一致，本轮标记失败或重试。

不要依赖“目录 mtime 看起来稳定”作为唯一依据。

## 增量同步策略

推荐以 manifest/hash 为准，mtime 只作加速线索。

| 依据 | 适用性 | 风险 | 建议 |
|---|---|---|---|
| 文件 mtime | 快速判断候选变化 | rsync、解压、跨文件系统、时钟漂移都会误判 | 不作为最终依据 |
| manifest/hash | 准确识别内容变化 | 大仓首次扫描成本高 | 作为事实来源 |
| 目录快照差异 | 能识别新增、修改、删除 | 需要持久化文件级 manifest | 作为增量执行计划 |

manifest 条目建议包含：相对路径、文件大小、内容 SHA-256、文件类型、扫描时发现的安全状态、可选 mtime。全局 manifest hash 由排序后的条目计算。

同步决策：

- manifest hash 与 `knowledge_sources.latest_manifest_hash` 一致：`SKIPPED_NO_CHANGE`。
- manifest hash 变化：生成 added/modified/deleted 差异。
- 新增/修改：进入 staging 并编译。
- 删除：必须从 source 作用域下撤销对应 `source_files/source_chunks/fact_cards/articles/graph_entities` 或标记不可见；不能只忽略，否则问答会命中已删除代码。

MVP 可以先全量编译变化后的镜像，但生产可用前必须补齐删除 reconciliation。否则“持续同步”只会追加和覆盖，不会真实同步。

## 目录白名单/黑名单策略

建议使用“默认 include + 默认 exclude + source 级追加配置”的组合。

### 默认纳入

Java 后端代码知识库默认纳入：

- 源码与配置：`.java`、`.xml`、`.yml`、`.yaml`、`.properties`、`.json`、`.sql`、`.md`、`.txt`
- 构建与工程描述：`pom.xml`、`build.gradle`、`settings.gradle`、`gradle.properties`
- 常见脚本和部署文本：`.sh`、`Dockerfile`、`.dockerignore`、`.gitignore`
- 前后端混合项目可选：`.js`、`.ts`、`.vue`、`.css`、`.html`

文件类型策略应可配置，不为某个项目硬编码。

### 默认排除

| 类别 | 默认排除 |
|---|---|
| VCS | `.git`、`.svn`、`.hg` |
| Java 产物 | `target`、`build`、`out`、`.gradle` |
| Node 产物 | `node_modules`、`dist`、`coverage` |
| IDE/系统 | `.idea`、`.vscode`、`.DS_Store`、`Thumbs.db`、`Desktop.ini` |
| 二进制产物 | `.class`、`.jar`、`.war`、`.ear`、`.zip`、`.tar`、`.gz`、`.7z` |
| 临时文件 | `*.tmp`、`*.temp`、`*.swp`、`*.bak`、`*.log` |
| 密钥文件 | `.env`、`.env.*`、`*.pem`、`*.key`、`*.p12`、`*.jks`、`id_rsa`、`id_dsa` |

### 密钥与敏感信息

仅排除文件名不够。Java 项目的 `.properties/.yml/.json` 可能包含数据库密码、token、私钥片段。建议新增通用敏感信息扫描：

- 对命中的敏感文件或敏感行 fail-closed：跳过、红action、或进入人工确认队列。
- 扫描规则只使用通用安全信号，不绑定业务系统名。
- 报告中展示命中数量和路径，默认不展示明文值。
- 原始镜像目录只读，staging 和数据库不应持久化明文密钥。

## Java 后端项目适配边界

| 问题 | 建议 |
|---|---|
| `.java/.xml/.yml/.properties/.json` 是否支持 | 应作为 CODE profile 的默认文本类型全量支持，并补齐 `.sql/.gradle/Dockerfile` 等常见工程文件 |
| 源码是否当普通文本导入 | 是。源码必须进入 `source_files` 和 `source_file_chunks`，用于路径、片段、引用和全文检索 |
| `.java` 是否只做普通文本 | 不是。`.java` 还应进入现有 AST 图谱抽取，支持类、方法、注解、调用、HTTP mapping 等结构化检索 |
| 是否对每个源码文件生成 LLM 文章 | 不建议无上限全量生成。大项目应先保证 source/chunk/AST 可检索，再按变更、优先级和预算生成摘要文章 |
| 是否需要代码知识库题集验证 | 需要。文档类 eval 不能覆盖代码问答风险 |

代码知识库题集建议覆盖：

- 精确类名、方法名、配置 key、路径查询。
- Controller endpoint 到 Service/Mapper 的链路问题。
- 配置文件值、profile 差异、开关含义。
- XML mapper、SQL、properties/yml 的跨文件引用。
- 删除文件后问答不再返回旧内容。
- `.git/target/build/node_modules/密钥文件` 不进入索引。
- 引用必须落到真实源码路径或配置路径，不能只引用生成文章。

## 与现有 Git 私有仓库导入的差异

| 维度 | Git 私有仓库 | 内部镜像源 |
|---|---|---|
| 数据来源 | Lattice 通过 JGit 拉远端仓库 | 外部同步器先同步到服务器，本系统只读扫描 |
| 凭据 | 需要 `source_credentials` | 通常不需要仓库凭据，但需要本机目录权限 |
| 版本语义 | commit/branch 天然稳定 | 需要 manifest、ready marker 或外部 generation |
| 网络依赖 | 依赖 Git 网络访问 | 不依赖 Lattice 访问 Git remote |
| 安全风险 | 凭据泄漏、remoteUrl token、网络失败 | 任意目录访问、软链越界、部分同步、密钥文件入库 |
| 增量依据 | commit + manifest 可结合 | manifest/file snapshot 是主依据 |
| 删除处理 | 可从 Git tree 差异获得 | 必须从目录快照差异获得 |
| 适用场景 | Lattice 可直接访问 Git 且仓库边界清晰 | 内网隔离、统一镜像、应用无 Git 权限、多源同步 |

二者不是替代关系。Git source 继续服务“应用自己拉仓库”的场景；`INTERNAL_MIRROR` 服务“公司已在服务器准备好镜像目录”的场景。

## 风险与边界

| 风险 | 影响 | 控制 |
|---|---|---|
| 任意服务器目录读取 | 严重安全问题 | 只允许配置化 mirror root + 相对路径，不接受任意绝对路径 |
| 镜像同步中被扫描 | manifest 混合两个版本 | ready marker、原子切换、两次快照一致性校验 |
| 大项目扫描成本高 | CPU/IO/LLM 成本失控 | 文件数、总字节、单文件大小、深度、定时错峰、预算控制 |
| 产物目录入库 | 噪声、成本、误召回 | 默认黑名单 + source 级扩展排除 |
| 密钥入库 | 高危泄漏 | 密钥文件排除 + 内容扫描 + redaction/quarantine |
| 删除不同步 | 问答返回过期代码 | 文件级 manifest + stale source_file/artifact 清理 |
| 只做文本不做 AST | 代码结构问答弱 | `.java` 同时进入 source chunk 与 AST graph |
| 只做 AST 不保留文本 | 引用和配置问答弱 | 所有支持文件先保留 source file/chunk |
| Webhook 被滥用 | 频繁扫描或越权触发 | 只接收 sourceCode，鉴权、限频、幂等、队列合并 |
| 特定项目优化 | 污染泛化能力 | 所有规则按路径结构、文件类型、安全信号和配置表达 |

## 验证计划

本轮不执行验证。后续实现后建议 agentD 按以下顺序验收：

1. 红线扫描：确认无业务项目名、题集、答案、文件名特判进入生产代码。
2. 基础测试：运行 Maven 全量测试，确认 `UPLOAD/GIT` 行为无回归。
3. API 测试：创建 `INTERNAL_MIRROR` source、validate、sync、list runs、list files。
4. 路径安全：拒绝绝对路径、`..`、越界软链、未配置 root。
5. 过滤验证：`.git/target/build/node_modules`、产物、密钥文件不进入 source files。
6. manifest 验证：相同目录二次同步 `SKIPPED_NO_CHANGE`；修改文件后 manifest 变化。
7. 删除验证：删除源码后，旧 source file、chunk、fact card、AST entity 不再可检索。
8. Java 能力：`.java` 进入全文检索和 AST 图谱；`.xml/.yml/.properties/.json` 能被 source chunk 检索和引用。
9. 稳定性：模拟同步中目录变化，确认本轮重试或失败，不产出混合快照。
10. 对照回归：Git 私有仓库导入与 Upload 导入仍按原行为工作。
11. 代码题集：使用公开合成 Java 项目题集验证 Recall@5/10、Citation Accuracy、Abstain、Hallucination。

## 给 agentA 的最小实现建议

建议只做一个最小闭环，不一次性实现所有增强：

1. 新增 `INTERNAL_MIRROR` source type，保持 `UPLOAD/GIT` 原行为不变。
2. 增加 mirror root allowlist 配置；API 只能接收 root 引用和相对项目路径。
3. 新增镜像物化服务：校验路径、按默认过滤规则扫描、计算过滤后的 manifest、复制到 staging。
4. 复用 `SourceUploadService.acceptMaterializedSource` 提交同步和编译。
5. 同步 evidence JSON 记录 rootRef、projectPath、fileCount、byteCount、manifestHash、excludedCount、scanStartedAt、scanFinishedAt。
6. MVP 只开放手动刷新；定时轮询和 webhook 单独后置。
7. 不在本轮写业务目录、项目名、题集、答案、特定文件名判断。
8. 生产可用前必须补删除 reconciliation；如果 MVP 暂不支持删除同步，API/报告必须明确标记限制，不能宣称完整持续同步。

建议 agentA 首轮不要修改 Query/Answer/Rerank/Fallback 主链。内部镜像源应先落在 source/materialization/sync 边界。

## 给 agentD 的验证建议

agentD 应使用合成内网 Java 项目 fixture，不使用真实私有项目和 hidden eval。验证报告建议命名为：

- `internal_code_mirror_source_gate_report.md`
- 或 `internal_code_mirror_source_runtime_verification_report.md`

重点验收：

- 新 source type 与旧 `SERVER_DIR` 没有语义回归。
- 不能通过 API 读取 allowlist 之外的服务器目录。
- 无变化跳过、变化触发同步、删除撤销索引。
- 过滤规则生效且不依赖具体项目名。
- Java 代码、配置、XML mapper、构建文件均可检索并给出真实 source file citation。
- Git 私有仓库凭据能力不受影响。
- Upload 一次性资料包能力不受影响。

## 最终判断

内部镜像源应是新的、受控的、长期运行的资料源概念，而不是恢复旧 `SERVER_DIR`。

推荐名称：`INTERNAL_MIRROR`。它的职责是把“公司已同步到服务器的私有代码镜像”安全、可追溯、可增量地转成 Lattice 的 source sync run 和 compile job；编译入口 `sourceDir` 继续保持为底层执行参数，不承担资料源身份。
