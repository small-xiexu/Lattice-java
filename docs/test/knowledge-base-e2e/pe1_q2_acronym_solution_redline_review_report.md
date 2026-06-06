# PE1 Q2 缩略词方案 — 红线复核报告

复核时间：2026-06-06
执行人：项目架构师 / 治理复核 Agent
前置方案：`pe1_q2_acronym_general_solution_design_report.md`（agentB，2026-06-06）
类型：只读红线复核，不修改代码，不修改数据库

---

## 1. 本轮目标

只读判断 agentB 推荐的方案 B（向 `query_rewrite_rules` DB 表插入 SL/TL/IM → Situation Lead/Technical Lead/Incident Manager 映射）是否违反 AGENTS Query 红线，给出明确的 ALLOW / BLOCK / NEEDS_REDESIGN 结论。

---

## 2. 方案 B 摘要

### 2.1 机制

利用现有 `query_rewrite_rules` DB 表，插入 3 条缩略词展开规则：

```sql
INSERT INTO lattice.query_rewrite_rules (rule_code, source_pattern, rewrite_text, scope, priority, enabled)
VALUES 
  ('acronym-expand-lead', 'SL', 'SituationLead', 'global', 100, true),
  ('acronym-expand-tech', 'TL', 'TechnicalLead', 'global', 100, true),
  ('acronym-expand-mgr', 'IM', 'IncidentManager', 'global', 100, true);
```

`QueryRewriteService` 在每次查询时自动加载 active 规则，匹配 `source_pattern`（子串匹配）后追加 `rewrite_text` 到查询文本。

### 2.2 agentB 提出的关键论点

| 论点 | 评估 |
|------|------|
| 是 DB 数据，不是 Java 代码 | 事实正确 |
| `query_rewrite_rules` 基础设施已就绪，零代码变更 | 事实正确 |
| 属于可审计的配置化规则层 | 部分成立，需进一步审查 |
| 与 `terminal-field-aliases`（YAML 配置）属同一层次 | 需审查类比是否成立 |
| AGENTS 红线只禁止 Java 主链硬编码 | **这是对红线的窄化解读** |

---

## 3. 红线逐条检查

### 3.1 AGENTS.md Query 红线原文

> 零容忍任何面向特定业务域、特定文档、特定文件名、特定术语、特定问题样式、特定样例字符串的硬编码分支、白名单、关键词特判、答案模板或兜底文案

### 3.2 六类禁止项逐条对照

| 禁止项 | SL/TL/IM 规则是否命中 | 判断 |
|--------|:---:|------|
| 1. 特定业务域 | SL/TL/IM 是事件响应（Incident Response）领域的角色缩略词 | **命中** ⚠️ |
| 2. 特定文档 | 规则不绑定 `incident-response-reference-lite.pdf` 文件名，但这三条缩略词**仅在该文档的知识域中有效** | 间接命中 |
| 3. 特定文件名 | 未绑定文件名 | 不命中 |
| 4. 特定术语 | "SL""TL""IM" 本身就是特定术语 | **命中** ⚠️ |
| 5. 特定问题样式 | "三类probe（SL/TL/IM）的职责分别是什么？" 是 PE1 Q2 的具体问法 | **命中** ⚠️ |
| 6. 特定样例字符串 | SL/TL/IM 是 PE1 Q2 问题中的样例字符串 | **命中** ⚠️ |

### 3.3 关键事实链条

```
源 PDF 内容 → 只含 "Situation Lead"、"Technical Lead"、"Messenger"、"Scribe"
              不含 "SL"、"TL"、"IM"（agentA PDF 文本提取已确认）
              ↓
Writer 输出 → 只含角色全名，不含缩略词
              ↓
全名查询 → "Situation Lead、Technical Lead..." → PASS（cov=1.0）
              ↓
缩略词查询 → "SL/TL/IM" → FAIL（NO_RELEVANT_KNOWLEDGE）
              ↓
insert query_rewrite_rules → "SL"→"SituationLead" 等 3 条规则
```

**核心问题**：缩略词（SL/TL/IM）仅出现在 eval 问题中，不出现在源文档、Writer 输出、索引或任何系统内部表示中。插入这三条规则的唯一目的是让 PE1 Q2 的特定问法通过。

### 3.4 `query_rewrite_rules` 作为 DB 数据是否仍受红线约束

**AGENTS.md Eval 使用规则明确覆盖了 SQL 数据：**

> 禁止将 hidden eval 的问题、标准答案、关键词、文件名、case id、expected citation 写入 `src/main/java`、`src/main/resources`、prompt 模板、`config/rules.yaml`、`config/synonyms.yaml`、**SQL 初始化数据**或回归脚本。

虽然 PE1 是 public eval（不是 hidden），但这条规则揭示了 AGENTS.md 的设计意图：**红线不仅约束 Java 代码，也约束 SQL 初始化和配置文件中的 eval 关键词**。

**对 "DB 数据不归红线管" 论点的驳斥：**

| 论点 | 驳斥 |
|------|------|
| "DB 数据不算主链硬编码" | AGENTS.md 红线原文说的是"硬编码分支、白名单、关键词特判、答案模板"——关键词是**特判**，不是**硬编码**。特判可以发生在任何层（Java、YAML、SQL、prompt），形式不重要，意图是禁止的 |
| "query_rewrite_rules 是通用基础设施" | 基础设施是通用的，但插入的三条规则内容（SL→SituationLead）是**特定术语映射**。基础设施的通用性不自动使规则内容通用 |
| "terminal-field-aliases 也是配置，为什么允许？" | `terminal-field-aliases` 中的 `port → [端口]` 是**语言层面的通用翻译**（英文→中文），"端口"是 port 的标准中文译名，不绑定任何业务域。SL→SituationLead 是特定领域的缩略词展开，无法等价类比 |
| "运维可以自行管理，所以不算特判" | 运维可以管理的事实不改变规则内容本身是特判。运维也可以管理 Java 代码，但不代表 Java 代码中的特判被允许 |

---

## 4. 是否允许 DB query_rewrite_rules 写入具体 SL/TL/IM 规则

### 4.1 严格红线角度：**不允许**

理由：

1. **源文档不含缩略词**——这不是"桥接查询与索引之间的 token 鸿沟"，而是向系统注入源文档中不存在的知识。Writer 已经正确输出了源文档的全部角色名（Situation Lead/Technical Lead/Messenger/Scribe）。要求系统理解 "SL" 就是 "Situation Lead" 是在要求系统**拥有源文档以外的领域知识**。

2. **规则内容是指定术语特判**——三条规则使用了具体的业务术语（SL→SituationLead 等），不是通用文本转换规则。即使放在 DB 表里，内容的性质仍是术语映射。

3. **规则的目的单一**——这三条规则的唯一效果是让 PE1 Q2 缩略词查询通过。当前知识库中没有其他文档使用这些缩略词。这是 eval-driven 配置，不是系统能力建设。

4. **如果允许这三条，边界在哪里？**——如果 SL/TL/IM 可以因为出现在 eval 问题中就写入 query_rewrite_rules，那么任何 eval 失败 case 中的术语都可以通过插入 DB 规则来"修复"。这会开启一个危险的先例：所有 eval 术语缺口都通过 DB 配置补，最终 `query_rewrite_rules` 变成 eval 术语垃圾场。

### 4.2 实际工程角度：**存在更大风险的灰色地带**

`query_rewrite_rules` 表的设计意图确实是配置化 synonym。如果运维人员因为业务需要而配置缩略词展开，在操作层面是合理的。但本轮是 **AI 辅助的 eval 修复**，AI 知道 eval 的题目和答案，AI 推荐插入特定术语到 DB——这在性质上就是 eval 污染。

| 场景 | 是否允许 |
|------|:---:|
| 运维人员独立发现用户经常搜索 "SL" 但知识库只含 "Situation Lead"，主动配置映射 | 允许 |
| AI 因 eval Q2 FAIL 而推荐插入 SL→SituationLead 到 DB | **不允许**——eval 污染 |
| 系统从源文档中自动发现 "Situation Lead"→"SL" 配对并写入索引 | 允许（方案 A） |

---

## 5. 为什么这不等同于 terminal-field-aliases

agentB 报告将 `query_rewrite_rules` 类比为 `terminal-field-aliases`（`lattice-query-semantic.yml` 中的 `port → [端口]`）。但这个类比不成立：

| 维度 | `port → [端口]` | `SL → SituationLead` |
|------|------|------|
| 映射性质 | 英文→中文**通用词汇翻译** | Incident Response 领域**专有名词缩略词展开** |
| 适用范围 | 所有含 "port" 字段的文档 | 仅 Incident Response 角色文档 |
| 信息源 | 可在任何英汉词典中找到 | 仅存在于特定领域知识中 |
| 是否绑定业务域 | 否 | **是** |
| 是否绑定特定文档 | 否 | 间接是（仅 incident-response-reference-lite.pdf 涉及此映射） |
| 如果不插入此规则，其他领域是否受损 | 否 | 否 |

**结论**：`port → [端口]` 是通用语言信号；`SL → SituationLead` 是特定业务术语映射。前者通过红线，后者不通过。

---

## 6. 方案 A（编译期 acronym extraction）的红线评估

### 6.1 为什么方案 A 通过红线

| 检查项 | 判断 | 理由 |
|--------|:---:|------|
| 是否绑定业务域 | 否 | 算法对任意文档中多词大写专有名词生成首字母缩略词 |
| 是否绑定文件 | 否 | 不区分文件名或内容主题 |
| 是否绑定术语 | 否 | 不预设任何特定缩略词映射 |
| 是否绑定问题样式 | 否 | 编译期生成，不感知查询内容 |
| 是否是通用能力建设 | **是** | 系统获得"从文档自动发现缩略词"的通用能力 |

### 6.2 方案 A 对 Q2 的局限性

即使实现方案 A，Q2 的 "IM/Incident Manager" 缩略词也无法解决——因为源 PDF 不使用 "Incident Manager"（使用 "Messenger"）。但这不是方案 A 的缺陷，而是 **Q2 问题本身的评测口径偏差**——问题使用了源文档不存在的术语。

---

## 7. 推荐结论

### **NEEDS_REDESIGN**

不接受方案 B（向 `query_rewrite_rules` 插入 SL/TL/IM 规则）作为当前唯一修复路径。

---

## 8. 下一步唯一最小动作

### 8.1 立即动作（不改代码）

**将 Q2 缩略词 FAIL 标记为评测口径问题**，不视为系统缺陷。

理由：
1. 源 PDF 不含缩略词 SL/TL/IM（agentA 已确认）
2. 全名查询 PASS（cov=1.0）
3. Writer 已正确输出全部角色定义
4. lightweight small doc 修复已让 Writer 输出了完整的角色定义表格
5. 系统在"给定正确查询词"时工作正常
6. "SL/TL/IM" 是评测问题的缩略词偏好，不是源文档中的信息

### 8.2 Q2 当前状态口径

| 项目 | 状态 |
|------|:---:|
| Q2 全名查询 | **PASS**（cov=1.0） |
| Q2 缩略词查询 | **已知限制** — 源文档不含缩略词，系统无桥接机制。标记为评测口径差异，不视为系统 FAIL |
| lightweight small doc 修复 | 已验证通过，建议提交 |
| Writer 角色定义输出 | 正确 |

### 8.3 后续能力建设（方案 A，独立轮次）

如果后续确实需要缩略词展开能力，按以下路径：

1. **实现编译期 acronym alias 生成**（方案 A）：
   - 从 article content 中识别多词大写专有名词
   - 自动生成首字母缩略词 alias
   - 写入 `referential_keywords` 或 article 检索字段
2. **在方案 A 就绪后**，如果仍有无法自动发现的合法缩略词（例如来自外部用户约定、行业标准缩写），可以通过 `query_rewrite_rules` 作为补充——但必须在有通用自动发现能力的前提下，作为运维补充手段，而非 eval 修复捷径。

### 8.4 当前可以提交的内容

lightweight small doc 修复（`lightweightMaxContentLines: 8→24`）是通用编译能力改善，已通过 runtime gate 验证，不违反任何红线，可以提交。Q2 全名查询 PASS 是该修复的直接成果。

---

## 9. 如果不采纳 NEEDS_REDESIGN，ALLOW 的前提条件

如果团队仍决定走方案 B（DB 规则），以下条件必须同时满足才能视为不违反红线精神：

### 9.1 必要条件

1. **规模化**：不能只插入 SL/TL/IM 三条规则。必须配套一个完整的缩略词字典（至少覆盖常见 IT/工程/管理领域的标准缩略词），使这三条规则不显得突出。
2. **来源可审计**：每条规则的 `source_pattern` → `rewrite_text` 映射必须有独立于 eval 题目的来源依据（如行业标准术语表、公开的缩写词典）。
3. **禁止特定领域独占**：不能只有 Incident Response 领域的缩略词，必须覆盖多个领域。
4. **运维驱动，非 AI 驱动**：规则的添加由运维人员基于业务需求决策，不由 AI 基于 eval 失败推荐。

### 9.2 如果只插入 SL/TL/IM 三条

**明确 BLOCK**。三条规则的唯一效果是让 PE1 Q2 通过，构成 eval 污染。

### 9.3 即使满足 ALLOW 条件，仍不推荐

因为方案 A（编译期自动发现）在长期是更优解，且不依赖人工维护。当前应先走方案 A，在方案 A 无法覆盖的场景（如行业约定缩写而非首字母缩写）再补充 DB 规则。

---

## 10. 明确声明

- [x] 本轮未修改任何代码
- [x] 本轮未修改任何数据库记录
- [x] 本轮未修改任何配置文件
- [x] 本轮未修改任何 prompt
- [x] 本轮未运行 hidden eval
- [x] 本轮未读取 hidden eval 内容
- [x] 本轮未提交 commit
- [x] 本轮未清库或重建索引
- [x] 所有结论基于已读取的 8 份报告 + AGENTS.md 红线原文 + 源 PDF 内容确认报告
- [x] 对方案 B 的 BLOCK 判断基于红线原文的"特定术语 + 特定问题样式 + 特定样例字符串"三重命中
- [x] 推荐的 NEEDS_REDESIGN 给出了明确的下一步动作（标记评测口径 + 轻量修复提交 + 长期方案 A）
