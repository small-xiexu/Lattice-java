# Review Queue 12 篇待人工确认草稿 — 逐项分析报告

- 生成时间：2026-05-24
- 执行 Agent：agentB（只读分析）
- 代码修改：**否**
- 数据库操作：**否**（仅读取 review queue API）
- Job ID：`f851188b-f926-4dad-9a88-2bb484b7d461`
- 数据来源：`GET /api/v1/admin/compile/review-queue` 运行时数据

---

## 1. 概览

| 指标 | 值 |
|------|-----|
| Review queue 总数 | 12 |
| 涉及源文件 | 5 个（项目启动配置清单、scenarios.xlsx、卡券三期-迁移方案、模型绑定配置参考、quality-progress-and-lessons） |
| 通过审查入库 | 5 篇 |
| 总生成文章 | 17 篇 |
| LLM 审查通过率 | 29.4% |

---

## 2. 逐项分析

### ID 1 — 已验证结论

| 维度 | 内容 |
|------|------|
| **标题** | 已验证结论 |
| **来源文件** | `quality-progress-and-lessons.md` |
| **Snippet 数** | 1（单一片段） |
| **正文长度** | 10,822 字符 |
| **Issue 数** | 2 |
| **最高 Severity** | HIGH |

**Issue 详情**：

1. `false_provenance` (HIGH): 文章第 13 节末尾将 "属于安全兜底，不构成双写或新主路径" 标注为 `[→ quality-progress-and-lessons.md, 已验证结论]`，但源材料片段在该处只截断到 "属于安全兜底，不构成双" —— Writer 补全了源片段中未完整提供的内容，且标注了直接来源引用。这属于带来源标注的未核实补全。

2. `conceptual_distortion` (MEDIUM): 文章同时保留了 "当前仍未生产默认启用 LLM reviewer" 和后续 "默认 LLM 模式代码实现已完成 / runtime 验证通过" 等结论，但未说明这是源材料中按时间推进形成的阶段性结论。容易让读者误解为同一时点下的状态冲突。

**是否扩写过头**：否。核心事实来自源材料，问题在于 (a) 一处补全了截断的文本并标注了来源引用，(b) 缺少时间/阶段语境说明。

**风险等级**：MEDIUM

**建议**：**需要人工再看正文**。核心决策点是：正文中补全的 "写或新主路径" 是否与源材料原始文字一致。如果人工核实一致，可将 provenance 标注改为 `[编译]` 即可通过；如果不一致，则该段应删除。时间语境问题可通过在摘要处增加一句 "以下结论来自源材料不同时间点的记录，需注意阶段差异" 来解决。

---

### ID 2 — 踩坑记录

| 维度 | 内容 |
|------|------|
| **标题** | 踩坑记录 |
| **来源文件** | `quality-progress-and-lessons.md` |
| **Snippet 数** | 1（单一片段） |
| **正文长度** | 12,315 字符 |
| **Issue 数** | 1 |
| **最高 Severity** | MEDIUM |

**Issue 详情**：

1. `false_provenance` (MEDIUM): 文章多处将 "源片段在此处截断"、"当前可见源片段未直接给出完整结论"、"不能补写为新的精确门禁结论" 等证据缺口判断标注为 `[→ quality-progress-and-lessons.md#踩坑记录]`。这些不是源文件中的直接陈述，而是编译者基于当前材料截断状态做出的元判断。Writer 的处理方向是保守的（没有虚构具体门禁结论），但 provenance 标注不准确，应改为 `[编译]`。

**是否扩写过头**：否。Writer 的处理方向正确——明确标注了片段截断，并且声明 "不能补写"。这是一个保守的、边界清晰的产物。

**风险等级**：LOW

**建议**：**建议确认**。只需将数处 `[→ source]` 改为 `[编译]` 即可。不涉及事实性错误或概念扩写。可以 approve 后由人工修正 provenance 标注，或直接 approve 因为在 query 引用时这些标注不会造成事实误导。

---

### ID 3 — Structured Table Overview 场景用例

| 维度 | 内容 |
|------|------|
| **标题** | Structured Table Overview 场景用例 |
| **来源文件** | `scenarios.xlsx` |
| **Snippet 数** | 6 |
| **正文长度** | 13,823 字符 |
| **Issue 数** | 2 |
| **最高 Severity** | HIGH |

**Issue 详情**：

1. `conceptual_distortion` (HIGH): `related` 元数据中引入了精确标识符 `scenario-test-cases`，该标识符未出现在源材料中。根据 Unsupported Exact Values 检查规则，新增的精确 identifier 若无来源支撑应标为高风险。

2. `conceptual_distortion` (HIGH): 文章称 `test_steps` 字段包含 "完整的测试流程文本"，但源材料末尾的前置脚本内容是截断的（停在 `调用API 100250的`）。"完整" 这一表述越过了证据边界。

**是否扩写过头**：轻微。主要是 related 标识符和 "完整" 措辞两个具体问题。

**风险等级**：MEDIUM

**建议**：**需要人工再看正文**。两个 issue 都可以通过措辞修正解决：(1) 去掉或降级 `scenario-test-cases` 标识符；(2) 将 "完整的" 改为 "当前可见的"。正文内容本身（场景用例的表格字段和样例值）似乎来自实际数据。建议人工确认表格字段清单与源 Excel 一致后 approve。

---

### ID 4 — Structured Table Overview 步骤详情

| 维度 | 内容 |
|------|------|
| **标题** | Structured Table Overview 步骤详情 |
| **来源文件** | `scenarios.xlsx` |
| **Snippet 数** | 6 |
| **正文长度** | 9,890 字符 |
| **Issue 数** | 10 |
| **最高 Severity** | HIGH（全部 10 个） |

**Issue 详情**：

1. `false_provenance` (HIGH): 文章大量引用 `[→ scenarios.xlsx, 步骤详情]`，但提供的源材料只有 Sheet: 场景用例，**没有 Sheet: 步骤详情**。
2. `missing_referential` (HIGH): 遗漏了源材料中 "场景用例" Sheet 的完整字段清单（scenario_id, scenario_uuid, status, case_num 等）、样例行（case_num=100633）、46 个测试步骤。
3. `missing_referential` (HIGH): 遗漏了源材料中所有接口路径（/onlineLifecycle/online/card/create 等 10+ 个路径）。
4. `missing_referential` (HIGH): 遗漏了 statusCode 校验规则。
5. `missing_referential` (HIGH): 遗漏了 DB 校验规则（payment_order, payment_sub_order 等表名/记录数/状态值）。
6. `value_mismatch` (HIGH): 文章声称当前源材料仅能确认 case_num=100814，但源材料明确给出 case_num=100633，**未出现 case_num=100814**。
7. `value_mismatch` (HIGH): 文章声称源材料未直接提供 "场景用例" Sheet 的字段清单或样例行——与事实矛盾。
8. `value_mismatch` (HIGH): 文章引入 "步骤详情" 列数=15、行数=1453 等精确值，在当前源材料中没有出现。
9. `false_provenance` (HIGH): 3 个抽样核验全部失败。
10. `conceptual_distortion` (HIGH): 文章整体主题被编译为 "Structured Table Overview 步骤详情"，但当前源材料主题实际是 "场景用例" Sheet 中的一个支付场景。

**是否扩写过头**：**是，严重。** Writer 似乎编造了一个不存在的 Sheet "步骤详情" 作为文章主题，同时遗漏了实际存在的 "场景用例" Sheet 的精确知识。引入了大量无法在源材料中核验的精确值（case_num=100814、列数=15、行数=1453）。

**风险等级**：CRITICAL

**建议**：**建议驳回**。这篇文章的核心主题可能根本不在提供的源材料片段中。case_num 的实际值与源材料矛盾，说明 Writer 在 snippet 分配或主题识别上出现了严重偏差。驳回后，需要在 compile 层面重新检查 scenarios.xlsx 的 snippet 分片策略，确保 "场景用例" Sheet 的数据被正确提供给 Writer。

---

### ID 5 — Document Overview 卡券三期 迁移方案

| 维度 | 内容 |
|------|------|
| **标题** | Document Overview 卡券三期 迁移方案 |
| **来源文件** | `卡券三期-迁移方案.md` |
| **Snippet 数** | 5 |
| **正文长度** | 7,683 字符 |
| **Issue 数** | 8 |
| **最高 Severity** | HIGH（全部 8 个） |

**Issue 详情**：

1. `false_provenance` (HIGH): 文章引用 "文档开头元数据块"、"版本更新说明/v1.3/v1.4/v1.5" 等来源位置，但**源材料样本中仅包含目录**，不包含文档元数据块或版本更新说明。
2. `value_mismatch` (HIGH): 引入大量具体值（2026-05-07、2026-04-13、30 天、24 小时、264,829、496,578、17 个接口、8 个定时任务、6 个 MQ 队列、ApiServiceImpl.java:586-590 等），均无法在源材料中核验。
3. `false_provenance` (HIGH): 声称 v1.5 将 businessTypeCode=26 从 "无流量" 修正为 "有流量"，给出具体字段和索引名，源材料不包含这些内容。
4. `false_provenance` (HIGH): 声称 v1.4 对 SVC 场景进行了源码复核，明确列出 1301、1302、3401 等分支和队列信息，源材料不包含。
5. `false_provenance` (HIGH): 声称 v1.3 新增退款链路，列出一系列具体接口/方法名，源材料目录只有标题不含这些接口。
6. `missing_referential` (HIGH): 遗漏了目录中的明确范围 "5A–5I"。
7. `missing_referential` (HIGH): 遗漏了 "附录：FC fulfillDeal 完整分支梳理" 和 "附录C：生产日志验证样本" 等精确章节名。
8. `conceptual_distortion` (HIGH): 将大量未在源样本中直接提供的异常/退款/流量复核结论写成确定事实。

**关键发现**：Writer **只收到了 5 个 snippet**，而原文档大小为 143KB。这 5 个 snippet 很可能只是文档目录 + 少量前言。Writer 从目录推导出了大量正文级别的结论（代码行号、具体数值、版本差异），这是典型的 "目录推导正文" 错误。

**是否扩写过头**：**是，极其严重。** 这是 12 篇中问题最严重的一篇。Writer 在仅有目录片段的情况下，编造了版本更新历史、代码行号、生产流量数据、接口路径、MQ 队列数等大量无法核验的精确断言。

**风险等级**：CRITICAL

**建议**：**建议驳回**。此文章一旦入库，query 将会返回大量无法核验的精确数字和代码位置，构成高危幻觉源。驳回后需要从根本上重新审视：对于 143KB 的大型技术文档，document overview 的 snippet 策略是否需要调整（例如，必须包含文档正文的至少前 N 段落，而非仅目录）。

---

### ID 6 — 重新发起 compile 任务

| 维度 | 内容 |
|------|------|
| **标题** | 重新发起 compile 任务，让新的 execution snapshot 重新生成 |
| **来源文件** | `模型绑定配置参考.md` |
| **Snippet 数** | 1（单一片段） |
| **正文长度** | 13,230 字符 |
| **Issue 数** | 2 |
| **最高 Severity** | HIGH |

**Issue 详情**：

1. `missing_referential` (HIGH): 遗漏了三个精确的 compile 角色默认超时值——writer: 180 秒、reviewer: 120 秒、fixer: 180 秒。源材料明确给出了这些默认值，且说明这些默认值只在模型 profile 没有填写 timeoutSeconds 时生效。超时秒数属于明确性/查表型知识，必须完整保留。

2. `missing_referential` (HIGH): 遗漏了配置决策的一个限制性条件——"只有在你明确接受所有命中该模型的角色共用一个超时时，才填写 timeoutSeconds"。该条件影响配置决策，应补入。

**是否扩写过头**：否。内容方向正确，只是遗漏了精确数字和一个限制性条件。

**风险等级**：MEDIUM

**建议**：**需要人工再看正文**。两个遗漏都是精确知识缺口，不涉及虚构。建议人工打开源文件 `模型绑定配置参考.md` 第 4.1 节，对比正文中关于超时的描述，补上缺失的三个默认超时值和限制性条件后 approve。

---

### ID 7 — Lattice Java 项目启动配置清单

| 维度 | 内容 |
|------|------|
| **标题** | Lattice Java 项目启动配置清单 |
| **来源文件** | `项目启动配置清单.md` |
| **Snippet 数** | 1（单一片段） |
| **正文长度** | 9,086 字符 |
| **Issue 数** | 1 |
| **最高 Severity** | HIGH |

**Issue 详情**：

1. `conceptual_distortion` (HIGH): front matter 的 `related` 字段新增了 `lattice-java-local-development`、`lattice-database-schema`、`lattice-docker-compose` 三个精确知识库标识。这些标识符未出现在提供的源材料中。

**是否扩写过头**：轻微。问题集中在 metadata 的 related 字段，不涉及正文事实。

**风险等级**：LOW-MEDIUM

**建议**：**需要人工再看正文**。主要确认正文内容本身（启动必配项、默认配置值、启动步骤）是否与源材料一致。如果正文正确，只需清理 related 字段中的三个虚构标识符即可 approve。

---

### ID 8 — 启动 Spring Boot

| 维度 | 内容 |
|------|------|
| **标题** | 启动 Spring Boot |
| **来源文件** | `项目启动配置清单.md` |
| **Snippet 数** | 1（单一片段） |
| **正文长度** | 8,796 字符 |
| **Issue 数** | 2 |
| **最高 Severity** | MEDIUM |

**Issue 详情**：

1. `false_provenance` (MEDIUM): 文章将末节标题写为 "第 5 步：启动 Spring Boot"，并称 "源材料在'从零启动的实际操作过程'中明确列出第 5 步标题"。源材料中对应标题只有 "### 第 5 步"，没有 "启动 Spring Boot" 这个标题后缀。虽然 "启动 Spring Boot" 可由其他章节支持，但不能作为第 5 步标题的直接来源。

2. `conceptual_distortion` (LOW): 文章最后的启动命令代码块以 ` ```bash ` 开始但未闭合，可能导致渲染异常。

**是否扩写过头**：否。两个 issue 都是格式/标注级别问题，不涉及事实性错误。

**风险等级**：LOW

**建议**：**建议确认**。两个 issue 都可以快速修正：(1) 将 "第 5 步：启动 Spring Boot" 改为 "第 5 步"（标题后缀移除），(2) 补上缺失的代码块闭合标记。正文内容无事实性风险。

---

### ID 9 — 用 mvn -s ... 启动

| 维度 | 内容 |
|------|------|
| **标题** | 用 `mvn -s .codex/maven-settings.xml spring-boot:run` 启动 |
| **来源文件** | `项目启动配置清单.md` |
| **Snippet 数** | 1（单一片段） |
| **正文长度** | 7,884 字符 |
| **Issue 数** | 1 |
| **最高 Severity** | HIGH |

**Issue 详情**：

1. `missing_referential` (HIGH): 源材料 §1 "基线来源" 明确列出 4 个基线文件：`docker-compose.yml`、`src/main/resources/application.yml`、`src/main/resources/db/schema.sql`、`pom.xml`。文章虽分散提到了前三项，但没有完整枚举该基线来源列表，且遗漏了 `pom.xml`。这些属于明确性知识，应原样保留并穷举。

**是否扩写过头**：否。问题只是遗漏了 `pom.xml` 在基线来源枚举中。

**风险等级**：LOW-MEDIUM

**建议**：**需要人工再看正文**。主要确认正文中是否在某个位置列出了完整的 4 个基线来源（而非仅 3 个）。如果正文的 "适用范围" 或 "基线来源" 节中已包含 `pom.xml`（可能 Reviewer 未在 snippet 中看到），则可直接 approve；如果确实遗漏，补充 `pom.xml` 后 approve。

---

### ID 10 — Lattice Java 项目启动配置清单（健康检查主题）

| 维度 | 内容 |
|------|------|
| **标题** | Lattice Java 项目启动配置清单 |
| **来源文件** | `项目启动配置清单.md` |
| **Snippet 数** | 1（单一片段） |
| **正文长度** | 6,305 字符 |
| **Issue 数** | 3 |
| **最高 Severity** | HIGH |

**Issue 详情**：

1. `missing_referential` (HIGH): 完全遗漏了源文件 "3.3 可选增强项" 中的 3 个明确配置项：`LATTICE_LLM_REVIEW_ENABLED=true`、`LATTICE_QUERY_SEARCH_VECTOR_ENABLED=true`、`LATTICE_QUERY_SEARCH_FTS_CONFIG`。这些属于明确性配置知识，不能因其不是启动前置条件而省略。

2. `false_provenance` (HIGH): 多处将 "源材料未提供 Spring Boot Actuator 健康检查端点、健康检查成功响应或健康检查端口" 等缺失性判断标注为 `[→ 项目启动配置清单.md]`。源文件并未直接陈述这些否定性结论；这属于编译者基于材料缺失做出的判断，不应使用直接来源标注 `[→]`。

3. `false_provenance` (HIGH): "源材料未直接提供以下内容，因此本文不再保留这些断言" 列表中包含 "健康检查失败时的响应体、错误码、排障流程或状态迁移规则"、"PostgreSQL 表清单校验命令" 等条目，均标注 `[→]`。这些具体缺失项本身并非源文件直接列出的内容，直接来源标注不准确。

**是否扩写过头**：**是，存在"否定式扩写"模式。** Writer 通过声明 "源材料未提供 X" 的方式，将 "健康检查失败时的响应体"、"错误码"、"排障流程"、"状态迁移规则"、"PostgreSQL 表清单校验命令" 等精确术语引入知识正文。虽然以否定形式出现，但这些术语在源材料中从未被讨论——Writer 自行引入了它们。

**风险等级**：HIGH

**建议**：**需要人工再看正文**。核心问题：
- 遗漏了 3.3 节的可选增强项（这是源材料的明确内容，必须补上）
- "源材料未提供" 列表中的精确术语（错误码、状态迁移规则等）需要清理——只保留源材料确实讨论过但明确说 "不在此处提供" 的内容，删除 Writer 自行引入的概念
- 所有 `[→ source]` 标注在 "源材料未提供" 类陈述上应改为 `[编译]`

---

### ID 11 — 显式开启向量检索

| 维度 | 内容 |
|------|------|
| **标题** | 显式开启向量检索 |
| **来源文件** | `项目启动配置清单.md` |
| **Snippet 数** | 1（单一片段） |
| **正文长度** | 4,019 字符 |
| **Issue 数** | 3 |
| **最高 Severity** | HIGH |

**Issue 详情**：

1. `unsupported_exact_values` (HIGH): 文章引入了多个源材料中不存在的精确标识符——`/admin/ask`（前端页面路径）、`article_vector_index`（数据库索引名）、`vector` 扩展（PostgreSQL 扩展名）、`知识输入系统一体化`（smoke 测试名）、`smoke`（测试类型名）、`PostgreSQL` 数据库名、`Docker` 容器名。虽然以 "源材料未直接提供/不能写出" 的形式出现，但根据审查规则，文章不应新增未经来源支持的具体路径、索引名、扩展名或系统名。

2. `unsupported_exact_values` (MEDIUM): 文章给出了 `export LATTICE_QUERY_SEARCH_VECTOR_ENABLED=true`。源材料只提供了配置项和值，没有提供 shell `export` 命令形式。该命令化写法属于新增的具体操作步骤。

3. `unsupported_exact_values` (HIGH): metadata 中的 `depends_on`、`related` 引入了多个精确知识页标识（如 `项目启动配置清单-项目启动基础配置`、`项目启动配置清单-数据库初始化` 等），源材料片段没有支持这些知识页关系。

**是否扩写过头**：**是，存在严重的"否定式扩写"模式。** 这篇文章是 "否定式扩写" 的典型：Writer 试图通过声明 "源材料未提供 X" 来划清证据边界，但 X 列表本身（`/admin/ask`、`article_vector_index`、`vector` 扩展、`知识输入系统一体化`等）全部来自 Writer 自身知识或编译上下文，不是源材料的 referential knowledge。这造成了悖论——以否定形式向知识库注入了原本不存在的外部概念。

**风险等级**：HIGH

**建议**：**需要人工再看正文**。需要评估：
- 如果 "源材料未提供" 列表中的术语（`/admin/ask`、`article_vector_index` 等）确实来自项目其他文档，则这些关系应在 compile 时通过跨文档引用机制建立，而非在当前文章中列举
- 如果这些术语是 Writer 自行引入的，则应全部删除
- 建议的修正方向：将 "源材料未提供 X, Y, Z" 改为更通用的 "源材料未提供数据库初始化命令、索引创建规则或页面验收入口"

---

### ID 12 — 项目本地启动配置清单

| 维度 | 内容 |
|------|------|
| **标题** | 项目本地启动配置清单 |
| **来源文件** | `项目启动配置清单.md` |
| **Snippet 数** | 1（单一片段） |
| **正文长度** | 3,833 字符 |
| **Issue 数** | 1 |
| **最高 Severity** | HIGH |

**Issue 详情**：

1. `conceptual_distortion` (HIGH): 文章多处新增了源材料未出现的精确主题或标识作为 "未提供内容"，例如 "CLI"、"MCP"、"页面提示"、"任务状态"、"前端展示"、"接口返回"、"异常状态流转"、"数据库校验 SQL"、"状态码"、"任务展示规则"、"业务流程" 等。虽然表述为否定，但这些精确对象并非源材料的 referential knowledge，属于文章自行引入的具体标识/范围扩展。

**是否扩写过头**：**是，与 ID 10、ID 11 完全相同的"否定式扩写"模式。** 这篇文章的 conceptId 竟然是 "项目启动配置清单-验证在存在活动-source-runs-或-wait-confirm-时-页面提示会同步变化" —— 这个 conceptId 本身就来自项目启动配置清单的 7.2 节（页面验证内容），Writer 将源材料中的页面验证步骤误识别为一个独立知识专题。这反映出 Writer 的主题识别/Topic 拆分策略在遇到 "页面验收" 相关段落时出现了方向性错误。

**风险等级**：HIGH

**建议**：**需要人工再看正文**。关键判断：
- conceptId 中包含的 "验证在存在活动 source-runs 或 WAIT_CONFIRM 时页面提示会同步变化" 本身来自源材料的 7.2 节，是该源材料的一个小节，但 Writer 将其拆成了一个独立的知识文章
- 正文中列出的 "未提供内容" 列表（CLI、MCP、前端展示、异常状态流转等）属于 Writer 的自行引入
- 建议：如果该主题确实是一个有效的知识专题，应精简正文到仅保留源材料 7.2 节中的页面验收相关事实；否则考虑合并到其他 `项目启动配置清单` 相关文章中

---

## 3. 按来源文件分组汇总

### 3.1 `项目启动配置清单.md` — 6 篇文章（IDs 7, 8, 9, 10, 11, 12）

| ID | 标题 | 建议 | 核心问题 |
|----|------|------|----------|
| 7 | Lattice Java 项目启动配置清单 | 需人工再看 | related 字段虚构标识符 |
| 8 | 启动 Spring Boot | **建议确认** | 标题后缀、代码块未闭合（均轻微） |
| 9 | 用 mvn -s ... 启动 | 需人工再看 | 遗漏 pom.xml |
| 10 | Lattice Java 项目启动配置清单 | 需人工再看 | 遗漏 3.3 节 + 否定式扩写 + false_provenance |
| 11 | 显式开启向量检索 | 需人工再看 | 严重否定式扩写（引入 /admin/ask、article_vector_index 等） |
| 12 | 项目本地启动配置清单 | 需人工再看 | 否定式扩写 + conceptId 主题偏移 |

**系统性判断**：同一来源文件 `项目启动配置清单.md` 下的 6 篇文章全部进入 review queue，**这不是单篇问题，而是系统性编译产物问题**。具体表现为：

1. **共享模式：否定式扩写** — ID 10/11/12 三篇文章都出现了同一个模式：Writer 在只有 1 个 snippet 的情况下，通过声明 "源材料未提供 CLI/MCP/前端展示/错误码/状态迁移规则/..." 来划清证据边界，但这些被否定的精确术语并非来自源材料，而是 Writer 自行引入的外部知识。**这会造成 query 时用户搜索 "CLI" 可能命中这些文章（因为文章正文中包含了 "CLI" 这个词），虽然上下文是 "源材料未提供 CLI"**。

2. **共享模式：false_provenance** — ID 10/12 都将基于材料缺失得出的否定判断标注为 `[→ 源文件]`，但实际上源文件中并没有这些否定陈述。

3. **单一 snippet 问题** — 6 篇文章全部只有 `snippetCount=1`。Writer 只能看到源文件的一个片段，却需要生成一篇完整的知识文章。这迫使其大量使用 "源材料未提供 X" 来填充证据边界章节。

### 3.2 `scenarios.xlsx` — 2 篇文章（IDs 3, 4）

| ID | 标题 | 建议 | 核心问题 |
|----|------|------|----------|
| 3 | Structured Table Overview 场景用例 | 需人工再看 | related 标识符 + "完整" 措辞 |
| 4 | Structured Table Overview 步骤详情 | **建议驳回** | 10 个 HIGH issue，主题偏移到不存在的 Sheet，case_num 矛盾 |

**系统性判断**：ID 4 的问题严重程度远超 ID 3，说明 scenarios.xlsx 的多 Sheet 结构在 compile 过程中出现了严重的 snippet 分配偏差——Writer 被分配了 "步骤详情" 主题但实际上 snippets 对应的是 "场景用例" Sheet 的数据。这可能是 Excel 的多 Sheet → snippet 映射逻辑存在缺陷。

### 3.3 `卡券三期-迁移方案.md` — 1 篇文章（ID 5）

| ID | 标题 | 建议 | 核心问题 |
|----|------|------|----------|
| 5 | Document Overview 卡券三期 迁移方案 | **建议驳回** | 8 个 HIGH issue，从目录推导出正文级别的代码行号和业务数据 |

**系统性判断**：Writer 只收到 5 个 snippet（对于一个 143KB 的文档，这 5 个 snippet 很可能只包含目录 + 少量前言），但仍然生成了包含代码行号、具体数字、版本差异的 document overview。这暴露了 document overview 生成逻辑在 snippet 不足时缺少 "证据不足则不生成概览" 的安全底座。

### 3.4 `模型绑定配置参考.md` — 1 篇文章（ID 6）

| ID | 标题 | 建议 | 核心问题 |
|----|------|------|----------|
| 6 | 重新发起 compile 任务 | 需人工再看 | 遗漏 3 个默认超时值 + 1 个限制性条件 |

### 3.5 `quality-progress-and-lessons.md` — 2 篇文章（IDs 1, 2）

| ID | 标题 | 建议 | 核心问题 |
|----|------|------|----------|
| 1 | 已验证结论 | 需人工再看 | 补全截断文本 + 缺少时间语境 |
| 2 | 踩坑记录 | **建议确认** | provenance 标注不准确（保守处理，无虚构） |

---

## 4. 按问题类型分组汇总

### 4.1 系统性错误模式

| 模式 | 涉及文章 | 严重程度 | 根因推测 |
|------|----------|----------|----------|
| **否定式扩写** | ID 10, 11, 12 | HIGH | Writer 在 snippet 不足时，用 "源材料未提供 X" 填充证据边界，但 X 列表来自 Writer 自身知识而非源材料 |
| **目录推导正文** | ID 5 | CRITICAL | 143KB 文档仅分配 5 个 snippet，Writer 从目录推演正文级别的数据 |
| **主题偏移/Sheet 错配** | ID 4 | CRITICAL | Excel 多 Sheet 的 snippet 映射逻辑可能将错误的 Sheet 内容分配给 Writer |
| **false_provenance** | ID 1, 2, 5, 10 | MEDIUM-HIGH | 将编译者的元判断标注为 `[→ 源文件]` 直接引用 |
| **单一 snippet 不足** | ID 1, 2, 6, 7, 8, 9, 10, 11, 12 | 潜在 | 10/12 篇文章只有 1 个 snippet，Writer 信息严重不足 |

### 4.2 Issue 类别分布

| Issue Category | 出现次数 | 涉及文章数 |
|----------------|----------|------------|
| `false_provenance` | 14 | 6 (IDs 1, 2, 4, 5, 8, 10) |
| `missing_referential` | 11 | 4 (IDs 4, 5, 6, 9, 10) |
| `value_mismatch` | 4 | 2 (IDs 4, 5) |
| `conceptual_distortion` | 8 | 7 (IDs 1, 3, 4, 5, 7, 8, 12) |
| `unsupported_exact_values` | 3 | 1 (ID 11) |

---

## 5. 建议汇总

| 建议 | 数量 | ID 列表 |
|------|------|---------|
| **建议确认** | 2 | 8（启动 Spring Boot）、2（踩坑记录） |
| **建议驳回** | 2 | 4（步骤详情）、5（卡券三期 Overview） |
| **需要人工再看正文** | 8 | 1, 3, 6, 7, 9, 10, 11, 12 |

---

## 6. 是否建议暂停人工逐篇确认，转而先修系统

**是，强烈建议暂停。**

核心理由：

1. **snippet 不足是系统性根因**。12 篇文章中 10 篇只有 `snippetCount=1`。Writer 在信息严重不足的情况下被迫大量使用 "源材料未提供 X" 的否定式声明来填充文章结构。这导致了一种悖论：Writer 试图通过否定来证明自己的严谨，但否定列表本身引入了源材料中不存在的精确概念（CLI、MCP、/admin/ask、article_vector_index 等）。

2. **同一来源 6 篇全部进 review queue**。`项目启动配置清单.md` 产生了 6 篇待确认文章，其中 3 篇有相同的 "否定式扩写" 模式。这意味着即使人工逐篇修正了这 6 篇，下次 compile 同样来源的文档时仍会产生相同模式的产物。

3. **2 篇驳回文章暴露了 deeper issue**。ID 4（scenarios.xlsx）的 Sheet 错配和 ID 5（卡券三期-迁移方案）的目录推导正文，都不是人工修正单篇文章能解决的——它们反映的是 Excel 多 Sheet snippet 分配策略和长文档 document overview 生成逻辑需要在 compile pipeline 层面修复。

4. **当前即使全部 12 篇都 approve，也只有 5+12=17 篇文章入库**。但 query regression 已经表明：在只有 5 篇入库的情况下，22 题中有 20 题通过 (90.9%)。入库更多文章可能带来边际收益，但也会引入噪音——特别是 ID 5（卡券三期 document overview）包含了大量无法核验的精确数字和代码位置。

**建议的修复优先级（先修系统，再回来确认草稿）**：

| 优先级 | 修复项 | 预期效果 |
|--------|--------|----------|
| P0 | 检查 Writer snippet 分配逻辑：为什么大部分文章只收到 1 个 snippet | 提高 snippet 覆盖率，减少 Writer 信息不足导致的 "否定式扩写" |
| P0 | 为 document overview 增加 "证据不足不生成" 安全底座 | 防止从目录推导正文（ID 5 类问题） |
| P1 | Reviewer 增加对 "否定式扩写" 的专项检测 | 当前 Reviewer 已检测到此问题（ID 11 的 unsupported_exact_values），但需要确认规则是否对所有类似模式生效 |
| P1 | Excel 多 Sheet snippet 映射逻辑修复 | 防止 Sheet 错配（ID 4 类问题） |
| P2 | 修复 false_provenance 标注：基于材料缺失的判断不应标注 `[→]` | 减少 provenance 层面的噪音 |
| P2 | 提高 Writer 对 `项目启动配置清单.md` 的主题识别精度 | 减少同一来源的碎片化拆分 |

**修复后再回来看这 12 篇**：
- 2 篇建议确认的（ID 2, 8）可以在当前状态下直接 approve
- 2 篇建议驳回的（ID 4, 5）应在 snippet 策略修复后重新 compile
- 8 篇需人工再看的可以在 snippet 策略修复后重新 compile，很可能修复后大部分都能通过自动审查
