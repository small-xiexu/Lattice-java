# fresh eval 根因分析报告

## 1. 结论摘要

- 本轮 8 个失败 case 可归为 2 个主桶：
  - 结构化 YAML exact path / sibling 字段回答失败：`FQ3`、`FQ4`、`FQ6`、`FG1`、`FG2`
  - 标题 / 弱标题 / representativeTitle 搜索失败：`FS1`、`FS2`、`FS3`
- `Citation Accuracy 2/15` 的主因不是引用格式统计口径，而是**答案 claim 与 citation 落点粒度不一致**：系统经常引用到了“同源文档中的粗粒度 summary / source 行”，但没有把 citation 绑定到真正回答问题的 terminal field 或目标 chunk。
- 最高优先级下一轮只建议修 1 个根因：**query 层对已召回 structured FACT_ENUM 的 terminal field / sibling 语义绑定不足，导致 deterministic fallback 没把终值字段带入最终答案。**
- 该优先根因属于 query 层，**可直接复用当前 fresh eval 库**做第一轮验证；不需要先清库重建。
- `FS1-FS3` 属于独立的标题/anchor materialization 与搜索身份问题，后续若处理，建议单独开下一轮；那一轮大概率需要清库重建或至少重建 article/chunk/index。

## 2. 失败 case 总览表

| Case | 验收现象 | 主要失败类型 | 关键只读证据 | 主结论 |
|---|---|---|---|---|
| FQ3 | 回答只给出设备类别汇总，没有回答 `7` | 结构化 YAML exact path 回答失败 | retrieval run `24`：`source_chunk_fts` fused rank=1，`FACT_ENUM` fused rank=2；fact card 已含 `equipment_types[1].max_borrow_days = 7` | exact field 已召回，但 fallback 未消费 terminal value |
| FQ4 | 回答只给出设备类别汇总，没有回答 `100 / 1000` | 结构化 YAML sibling 字段回答失败 | retrieval run `25`：exact `FACT_ENUM` fused rank=2；fact card 已含 `equipment_types[0].deposit_amount = 100`、`equipment_types[2].deposit_amount = 1000` | sibling 语义绑定不足，未把目标字段写进终答 |
| FQ6 | 回答给出系统名、API、damage_report_required，没有回答 `v2.3.1` | 结构化 YAML exact path 回答失败 | retrieval run `27`：exact `FACT_ENUM` fused rank=2；fact card 已含 `borrowing_system.version = v2.3.1` | terminal field 已召回，但终答落到了同父级其他字段 |
| FS1 | 搜索命中目标文档，但前列被同一 article 的多个 chunk / article 候选占位 | sourceTitle 搜索排序/身份稳定性不足 | run `34`：`sourceTitle` 已进入 `articles.search_text`；前列同时出现文档级 article 与多个 chunk 级 ARTICLE 命中 | 不是“搜不到”，是主条目身份不够稳定 |
| FS2 | Top5 命中整篇 article，不是目标弱标题条目 | 弱标题 / anchor 搜索失败 | `lab-safety-management-handbook` 的 `titleProfile.anchorTitle=校园实验室安全管理手册`；`## 化学品分类存储` 只存在于正文 chunk0 | 目标弱标题未作为独立 title/anchor identity 物化 |
| FS3 | Top5 命中整篇 article，不是 representativeTitle 自身条目 | representativeTitle 搜索失败 | `titleProfile.representativeTitle=校园实验室安全管理手册`；“实验室化学品分级存储管理规范”只出现在正文/关键词 | representativeTitle 未作为标题画像落库 |
| FG1 | 回答落到 return_policy，没回答 `20 / 5` | 结构化 YAML sibling 字段回答失败 | retrieval run `40`：exact `FACT_ENUM` fused rank=2；fact card 已含 `equipment_types[1].late_fee_per_day = 20`、`equipment_types[0].late_fee_per_day = 5` | sibling 目标字段存在，但 fallback 未绑定 |
| FG2 | 回答落到 return_policy，没回答 `50` | 结构化 YAML exact path 回答失败 | retrieval run `41`：exact `FACT_ENUM` fused rank=2；fact card 已含 `borrowing_system.max_concurrent_requests = 50` | terminal field 已召回，但被无关同文档数值/布尔字段抢占 |

## 3. 主类归因

### 3.1 结构化 YAML exact path / sibling 字段回答失败

#### 3.1.1 先验事实

- `equipment-borrowing-policy.yaml` 当前库中已成功生成精确结构化 fact card：
  - `fact-card:2:0:fact_enum:41aa37638b50706c`
  - evidence text 含以下 exact fieldPath：
    - `borrowing_system.version = v2.3.1`
    - `borrowing_system.max_concurrent_requests = 50`
    - `equipment_types[1].max_borrow_days = 7`
    - `equipment_types[0].deposit_amount = 100`
    - `equipment_types[2].deposit_amount = 1000`
    - `equipment_types[1].late_fee_per_day = 20`
    - `equipment_types[0].late_fee_per_day = 5`
- 对应 retrieval run `24/25/27/40/41` 全部满足：
  - `coverage_status=covered`
  - `fact_card_hit_count=2`
  - fused rank 结构稳定为：
    - rank 1: `source_chunk_fts`
    - rank 2: exact `FACT_ENUM`
    - rank 3: list `FACT_ENUM`
    - rank 4: `SOURCE`
    - rank 5: `ARTICLE` / `refkey`
- 当前 query 语义配置中能确认的中文 terminal-field alias 只有 `端口 -> port`；未看到更通用的中文 terminal field 绑定机制。

#### 3.1.2 FQ3 / FQ4 / FQ6 / FG1 / FG2 判定表

| Case | 结构化 fact card 是否已召回 | 是否是 fallback conclusion builder 没把 terminal field value 带入终答 | structured fact card 是否太粗、只召回 equipment_types 汇总 | 是否存在 sibling 排序 / 字段语义绑定不足 | 主结论 |
|---|---|---|---|---|---|
| FQ3 | 是，fused rank=2 | 是 | 否，exact path 已存在 `equipment_types[1].max_borrow_days = 7` | 是 | 主因是 terminal field 绑定不足，不是召回缺失 |
| FQ4 | 是，fused rank=2 | 是 | 否，exact path 已存在两个押金字段 | 是 | 主因是 sibling 字段绑定不足 |
| FQ6 | 是，fused rank=2 | 是 | 否，exact path 已存在 `borrowing_system.version = v2.3.1` | 是 | 主因是 terminal field 绑定不足 |
| FG1 | 是，fused rank=2 | 是 | 否，exact path 已存在两个逾期罚金字段 | 是 | 主因是 sibling 字段绑定不足 |
| FG2 | 是，fused rank=2 | 是 | 否，exact path 已存在 `borrowing_system.max_concurrent_requests = 50` | 是 | 主因是 terminal field 绑定不足 |

#### 3.1.3 文字链路

1. YAML 已编译出 exact `fieldPath` fact card，且字段值是对的。
2. query 检索阶段没有漏召回；目标 exact `FACT_ENUM` 稳定进入 fused Top3。
3. deterministic fallback 仍以 `source_chunk_fts` 为主入口，exact fact card 只作为补充。
4. 当问题需要“精密仪器 + 最长借用天数 / 押金 / 逾期罚金”或“borrowing_system + version / max_concurrent_requests”这类**父路径 + terminal field**联合约束时，当前 query 层没有稳定把问题焦点绑定到 exact `fieldPath` 候选。
5. 结果不是“没证据”，而是 fallback 产出退化成：
   - 粗粒度汇总描述
   - 同一父级中的无关 sibling
   - 或 `return_policy` 一类同文档但不回答问题的字段
6. citation 随后也只能落到这些粗粒度 SOURCE/ARTICLE 行，因此 Answer Accuracy 与 Citation Accuracy 同时失败。

#### 3.1.4 关键判断

- 这 5 个 case **不是**“structured fact card 粒度太粗”。
  - 关键反证：当前库中的 exact `FACT_ENUM` 已直接保存具体 `fieldPath = value`，并且稳定进入 fused rank 2。
- 这 5 个 case **也不是**“retrieval 没召回”。
  - 关键反证：`coverage_status=covered`，且 Top3 一直含 exact fact card。
- 这 5 个 case 的主问题是：
  - **question-side terminal field / sibling 语义绑定不足**
  - 导致 **fallback conclusion builder 没把已召回的 terminal field value 写进最终答案**

### 3.2 标题 / 弱标题 / representativeTitle 搜索失败

#### 3.2.1 先验事实

- `lab-safety-management-handbook` 当前 article metadata：
  - `analysisMode=LIGHTWEIGHT_SMALL_DOC`
  - `titleProfile.sourceTitle=校园实验室安全管理手册`
  - `titleProfile.anchorTitle=校园实验室安全管理手册`
  - `titleProfile.representativeTitle=校园实验室安全管理手册`
- `articles.search_text` 已包含：
  - `title`
  - `summary`
  - `content`
  - `metadata_json`
  - `titleProfile` 三个标题字段
- 但只读 SQL 显示：
  - `## 2. 化学品分类存储`
  - “本节也可概括为‘实验室化学品分级存储管理规范’”
  都只出现在 article content / chunk0 中，没有进入独立 titleProfile 身份。
- chunk0 同时承载文档头与第 2 节内容，`ChunkHitIdentitySupport.extractSectionAnchor()` 取 chunk 内首个 heading，因此该 chunk 的 display title 仍偏向文档标题，而不是目标弱标题。

#### 3.2.2 FS1 / FS2 / FS3 判定表

| Case | 目标标题类型 | 是否进入索引 | Top5 为什么命中整篇 article 而不是目标 anchor chunk | 主要结论 |
|---|---|---|---|---|
| FS1 | `sourceTitle` | 是，明确进入 `titleProfile` 与 `search_text` | 同一文档的文档级 article 与多个 chunk 级 ARTICLE 候选同时占前列，`SOURCE` 主条目只到后位 | 不是 sourceTitle 缺失，而是主条目身份/权重不够稳定 |
| FS2 | 弱标题 `化学品分类存储` | 目标弱标题文本进入正文与 summary，但未作为独立 title/anchor identity 入索引 | chunk0 包含该标题，但 chunk 的展示标题仍是文档标题，检索结果表现为“整篇 article 命中” | 主因是弱标题 materialization 缺失，权重问题是次因 |
| FS3 | representativeTitle `实验室化学品分级存储管理规范` | 目标短语出现在正文与 referential keywords，但不在 `titleProfile.representativeTitle` | 搜索只能命中 whole article / chunk0，无法返回 representativeTitle 自身条目 | 主因是 representativeTitle 没有被标题画像化 |

#### 3.2.3 文字链路

1. Markdown 文档当前按 `LIGHTWEIGHT_SMALL_DOC` 编译为单 article。
2. `sourceTitle` 已进索引，但 `anchorTitle` / `representativeTitle` 仍停留在文档级标题。
3. `化学品分类存储` 与“实验室化学品分级存储管理规范”虽然出现在正文，但没有被物化成独立标题身份。
4. chunk0 又同时承载文档头和目标章节，导致 chunk 的 display title 继续偏向文档级标题。
5. 搜索因此命中“整篇 article / chunk0”，而不是题集要求的弱标题条目或 representativeTitle 条目。

#### 3.2.4 关键判断

- `FS1` 更像**标题搜索身份稳定性**问题，不是“sourceTitle 根本没入索引”。
- `FS2` 与 `FS3` 的主问题比“权重不足”更早一层：**目标弱标题 / representativeTitle 没被编译成可检索的独立标题身份**。

## 4. Citation Accuracy `2/15` 判断

| 判断项 | 结论 | 依据 |
|---|---|---|
| 是引用格式统计口径问题吗 | 否，最多只是次要因素 | 失败题的 `query_answer_citations` 表里已经存在大量 citation literal，且不少状态是 `VERIFIED`，不是“没记到引用” |
| 是 citation 真实支撑不足吗 | 是，主因 | `VERIFIED` 往往落在 article summary、title、source 根行、同源无关字段，而不是回答问题的 terminal field / 目标 chunk |
| 是 fallback answer 使用了证据但 citation binding 没落到 terminal field 吗 | 是，主因之一 | `FQ3/FQ4/FQ6/FG1/FG2` 的 answer audit 显示答案正文引用了同源粗粒度证据，exact fact card 没进入最终 claim 绑定 |

### 4.1 更具体的判断

- 对 fresh eval 当前口径来说，`Citation Accuracy 2/15` 的主因是**真实支撑粒度不足**，不是引用字符串格式错误。
- 当前系统的 machine verification 能验证：
  - “这行文字确实存在于同源 article/source”
- 但不能保证：
  - “这行文字就是回答用户问题所需的 terminal field / 目标 chunk”
- 因此会出现这种现象：
  - machine `citation_coverage` 仍有 `0.6 / 0.6667`
  - 但人工 `Citation Accuracy` 仍判 FAIL

### 4.2 对本轮失败桶的直接影响

- 结构化 YAML 失败桶：
  - 主要是 **claim 已错位，citation 只能跟着错位 claim 走**
- 标题搜索失败桶：
  - 主要是 **命中的载体粒度不对，citation 即使存在，也只能指向整篇 article / source，而不是目标 chunk**

## 5. 最高优先级修复建议

### 5.1 下一轮只建议修 1 个根因

**优先根因：query 层对已召回 structured FACT_ENUM 的 terminal field / sibling 语义绑定不足，导致 deterministic fallback 没把终值字段写入最终答案。**

### 5.2 为什么这是通用能力修复，不是 case 特判

- 该问题的共同形态是：
  - 结构化文档使用英文或 snake_case field path
  - 用户问题使用自然语言字段描述
  - exact fact card 已召回
  - 但 fallback 没把 exact terminal value 消费进终答
- 这类失效并不依赖本轮资料包的业务域；任何 YAML / JSON / structured markdown 只要出现“父路径实体 + 终值字段 + sibling 干扰”都可能复现。
- 修复目标应是：
  - 让 query 层更好地消费现有 `keyPath / parentPath / pathSegments / displayText`
  - 而不是为 `FQ3/FQ4/FQ6/FG1/FG2` 写题面、文件名、case id、业务词、字段词白名单

### 5.3 建议交给 agentA 的允许修改范围

- 允许修改：
  - `src/main/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelector.java`
  - `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.java`
  - `src/main/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilder.java`
  - `src/main/java/com/xbk/lattice/query/service/AnswerGenerationExactLookupGroundingSupport.java`
  - 如确有必要，可最小修改 `src/main/resources/config/lattice-query-semantic.yml`
- 允许修改的目标边界：
  - 只修“exact fact card 已召回后，如何把 terminal field / sibling 对齐到最终答案”
  - 不扩到 title search / compile / chunking / rerank / citation validator

### 5.4 明确禁止修改范围

- 禁止修改：
  - `src/main/java/com/xbk/lattice/compiler/**`
  - `src/main/java/com/xbk/lattice/infra/persistence/**`
  - `src/main/java/com/xbk/lattice/query/service/RrfFusionService.java`
  - `src/main/java/com/xbk/lattice/query/service/ArticleChunkFtsSearchService.java`
  - `src/main/resources/db/**`
  - `scripts/**`
  - `docs/模型绑定配置参考.md`
  - `special_cases_report.md`
  - fresh eval 题集、资料包、验收脚本、allowlist、prompt、模型绑定
- 本轮禁止做的事：
  - 不准顺手一起修 `FS1-FS3`
  - 不准把 fresh eval 字段词、文件名、case id、答案值写进 Java / prompt / 配置
  - 不准修改 `src/test/java/**`，除非用户单独放行

## 6. 是否需要重新入库验证

### 6.1 对本报告推荐的下一轮修复

- 推荐修复点属于 **query 层**
- **可以复用当前 fresh eval 库**
- 原因：
  - exact fact card 已在库中
  - retrieval run 已证明 `FACT_ENUM` 稳定召回
  - 当前缺口在“如何消费已有证据”，不在“证据是否存在”

### 6.2 对 `FS1-FS3` 后续独立修复

- `FS1-FS3` 更偏 **编译 / 标题画像 / chunk materialization / 搜索身份** 层
- 如果后续进入这一桶：
  - 建议清库重建，或至少重建 articles / chunks / vector / search index
- 原因：
  - 当前库里目标弱标题和 representativeTitle 没有被物化为独立可检索身份
  - 只改 query 排序通常无法凭空补出缺失的标题结构

## 7. 红线风险判断

### 7.1 当前状态

- 本轮未发现为 fresh eval 新增的 case 特判、业务词特判、文件名特判、题面特判。
- 当前失败体现为通用能力缺口，不是 public eval 注入式硬编码。

### 7.2 下一轮最高风险点

- 最危险的错误修法是直接往 query 里塞：
  - 题面词
  - 文件名
  - case id
  - `精密仪器 / 常规设备 / 大型设备`
  - 本轮 fresh eval 可见字段词或答案值
- 另一个高风险点是把：
  - structured terminal-field 修复
  - 标题/anchor 搜索修复
  在同一轮一起做，导致无法归因。

### 7.3 红线结论

- 当前可以进入 `agentA` 最小修复轮。
- 但必须严格限定为**一个 query 层根因**，不能扩散到 compile/index/title/search。

## 8. 建议下一步交给哪个 Agent

- **下一步建议交给 `agentA`**
- 任务类型：
  - 只修 query 层的 structured exact-lookup terminal field / sibling 绑定
- 完成后再交给 `agentD`：
  - 先在**当前库**复验 `FQ3/FQ4/FQ6/FG1/FG2`
  - 再跑完整 fresh eval，确认剩余失败是否只剩 `FS1-FS3`
