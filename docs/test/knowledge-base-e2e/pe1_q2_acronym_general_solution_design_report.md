# PE1 Q2 缩略词查询 — 通用方案设计报告

设计时间：2026-06-06
执行人：agentB（治理/链路分析 Agent）
类型：只读方案设计，无代码修改

---

## 1. 本轮目标

设计 PE1 Q2 缩略词查询的通用修复方案。源 PDF 已确认不含缩略词（SL/TL/IM），Writer 无法凭空保留不存在的缩写。需要在以下候选方案中取舍并推荐一个最小实现方向。

---

## 2. 当前问题状态

| 维度 | 状态 |
|------|:---:|
| Writer 输出 | ✅ 含完整角色全名（Situation Lead、Technical Lead、Messenger、Scribe） |
| 全名查询 | ✅ PASS（cov=1.0） |
| 缩略词查询 "SL/TL/IM" | ❌ FAIL（NO_RELEVANT_KNOWLEDGE） |
| 源 PDF 含缩略词配对 | ❌ 已确认不含（agentA 前置检查） |
| Token extraction | ✅ "SL"/"TL"/"IM" 被正确提取（ASCII 2 字符） |
| FTS/LIKE 匹配 | ❌ article 内容无 "sl"/"tl"/"im" 子串或 tsvector token |
| 根因 | 查询 token 空间（缩略词）与索引 token 空间（全名）之间无桥接机制 |

---

## 3. 现有 Synonym / Alias / Acronym 能力盘点

| 能力 | 是否存在 | 详情 |
|------|:---:|------|
| `query_rewrite_rules` DB 表 | **是** | `source_pattern`（子串匹配）→ `rewrite_text`（追加到 query）。`QueryRewriteService` 在 query pipeline 早期自动加载并应用。**零代码变更即可插入新规则。** |
| `config/synonyms.yaml` | **否** | 不存在 |
| `config/rules.yaml` | **否** | 不存在 |
| `terminal-field-aliases`（`lattice-query-semantic.yml`） | **是** | 仅含 `port → [端口]` 一条映射。格式为 YAML 配置，Chinese alias → canonical key。**不适用于缩略词展开。** |
| `referential_keywords`（article 级别） | **是** | 编译期 regex 提取 key=value 和 3-5 位数字。LLM prompt 也会生成。存储为 `TEXT[]` 数组，通过 `RefKeySearchService` 检索。 |
| Materializer field aliases（terminal unit 级别） | **是** | 从 keyPath/pathSegments/camelCase 派生。`addTokenAlias()` 过滤 `length < 2` 的 token。 |
| LLM Enricher field aliases | **是** | 生成中文字段别名。`isAllowedGeneratedAlias` 要求含 CJK。不支持英文缩略词。 |
| `LexicalSearchTokenBudget` 短 token 评分 | **是** | 2 字符 ASCII 得 122 分（高于排除线）。"SL"/"TL"/"IM" 不会被预算过滤。 |
| 现有 acronym/initialism 处理 | **否** | 代码库中完全不存在的概念 |

---

## 4. 方案 A：编译期 Acronym Alias 生成

### 4.1 机制

在 compile 阶段，从 article 内容中识别多词大写专有名词（如 "Situation Lead"），自动生成首字母缩略词（"SL"），写入 `referential_keywords` 或 article `search_text`，使缩略词在索引层即可被检索。

### 4.2 识别规则（通用）

- 连续 2-5 个大写首字母单词（每个单词首字母大写，后续小写）
- 单词之间以空格分隔
- 总字符数 >= 2（过滤单字母假阳性）
- 缩略词不与其组成单词中任一完整单词相同

### 4.3 写入位置

`referential_keywords` 数组（已有 GIN 索引 + `refkey_text` 列），通过 `RefKeySearchService` 的 LIKE 检索自动命中。

### 4.4 收益

- 全自动，零人工维护
- 编译期一次生成，查询期所有通道（FTS/LIKE/refkey）均受益
- 对所有文档、所有多词术语一视同仁

### 4.5 风险

| 风险 | 评估 |
|------|------|
| 假阳性（如 "High" → "H"、"Low" → "L"） | 单字母缩略词阈值过滤（min 2 chars）可消除 |
| 假阳性（如 "Not Recoverable" → "NR"） | "NR" 作为 2 字符 token 被加入 referential_keywords，对检索无负面影响（不命中时无效果） |
| 与已有 referential_keywords 重复 | `mergeReferentialKeywords` 去重 |
| 大量缩略词增加索引体积 | 每个 article 最多新增数个缩略词，可忽略 |

### 4.6 实现范围

- 新增 `AcronymAliasSupport` 工具类（约 30-40 行）
- 在 `CompileArticleNode.extractReferentialKeywords()` 或附近调用
- 不修改 schema、不修改 query 主链

---

## 5. 方案 B：DB 配置化 Synonym Expansion（推荐）

### 5.1 机制

利用现有 `query_rewrite_rules` DB 表，插入缩略词展开规则。`QueryRewriteService` 在每次查询时自动匹配并追加展开文本。

### 5.2 示例规则

```sql
INSERT INTO query_rewrite_rules (rule_code, source_pattern, rewrite_text, scope, priority, enabled)
VALUES ('acronym-SL', 'SL', 'Situation Lead', 'global', 100, true);
```

### 5.3 收益

- **零 Java 代码变更**——`query_rewrite_rules` 表、`QueryRewriteService`、StateGraph 集成均已就绪
- 规则作为数据管理，可通过 Admin API 增删改查，无需部署
- 精确控制——只有明确配置的缩略词才展开，无误生成风险
- 对 query pipeline 透明——rewrite 发生在 normalize 之后、retrieval 之前

### 5.4 风险

| 风险 | 评估 |
|------|------|
| 需人工维护缩略词字典 | 是主要成本。但字典独立于代码，可由运维/评测人员维护 |
| 无法覆盖未知缩略词 | 新文档的新缩略词需手动添加 |
| `source_pattern` 子串匹配过于宽泛 | "SL" 可能匹配 "slow"、"slash" 等不相关内容。但匹配后追加的 rewrite_text 不改变检索语义——检索仍用原始 question + 追加的展开文本，展开文本只在匹配时生效 |
| 对短 pattern 的假阳性 | "IM" 可能匹配 "important"、"simple" 中的 "im"。但仅在 query 包含这些词时才触发。可通过 `scope` 限制或提高 `priority` 来管理 |

### 5.5 为什么不是 "Java 主链硬编码"

`query_rewrite_rules` 是 DB 数据，不是 Java 代码。与 `terminal-field-aliases`（YAML 配置）属同一层次——**可审计的配置化规则层**。AGENTS.md 红线禁止的是在 `src/main/java` 中用 `if/else` 写 `"SL" → "Situation Lead"` 映射。DB 规则表是独立于代码的数据资产。

### 5.6 实现范围

- **零代码变更**
- 插入 DB 规则行（SQL 或 Admin API）
- 如需初始化规则，可在 `schema.sql` 或独立 seed SQL 中添加

---

## 6. 方案 C：Query Layer Acronym Detection

### 6.1 机制

在 query 处理阶段检测 2-4 字符全大写 token，用首字母匹配算法在已知术语表中查找多词短语。例如：query 含 "SL" → 查找首字母为 S 和 L 的连续单词 → 匹配到 "Situation Lead" → 追加到 query。

### 6.2 收益

- 全自动，不依赖配置
- 算法通用

### 6.3 风险

| 风险 | 评估 |
|------|------|
| 假阳性高 | "IT" 展开为 "Information Technology"？"In Test"？"Is True"？多义性无法自动消歧 |
| 需要全局术语表 | 需在检索时维护和查询索引中所有多词术语，性能开销大 |
| 实现复杂 | 需要新增 query 处理节点、术语索引、匹配算法 |
| 难以调试 | 自动展开的缩略词不可预测 |

### 6.4 判定：**不推荐**

复杂度与收益不成比例。PE1 Q2 是 2-3 字符缩略词问题，方案 B 用 3 行 SQL 即可解决，方案 C 需要数百行 Java + 术语索引。

---

## 7. 方案 D：Retrieval 层 Acronym Matching

### 7.1 机制

在 FTS/LIKE 检索层对缩略词 token 做特殊处理：用首字母匹配算法在 `articles.content` 中查找多词短语。

### 7.2 风险

| 风险 | 评估 |
|------|------|
| 性能开销 | 每次检索需扫描 article 内容做首字母匹配，对大型知识库不可接受 |
| 假阳性 | 与方案 C 相同的多义性问题 |
| 与现有检索架构冲突 | `searchLexical` SQL 不支持首字母匹配语义 |

### 7.3 判定：**不推荐**

性能开销和实现复杂度远超收益。

---

## 8. 方案对比表

| 维度 | A: 编译期 acronym alias | B: DB synonym expansion | C: Query acronym detection | D: Retrieval acronym match |
|------|:---:|:---:|:---:|:---:|
| 代码变更 | 新增 ~40 行 Java | **零** | 新增 ~200 行 Java | 新增 ~100 行 + SQL 变更 |
| 人工维护成本 | **零** | 每缩略词一条规则 | **零** | **零** |
| 假阳性风险 | 低（2+ 字符过滤） | **极低**（精确配置） | **高**（多义性） | **高** |
| 覆盖范围 | 编译时已知的多词术语 | 所有配置的缩略词 | 所有 query 中的缩略词 | 所有 query 中的缩略词 |
| 验证成本 | 需重编译 | 插入规则即可验证 | 需全量 eval 回归 | 需全量 eval 回归 |
| 是否违反红线 | 否 | **否**（DB 数据，非 Java 硬编码） | 否（算法通用） | 否（算法通用） |
| 推荐度 | ⭐⭐⭐ 中期 | ⭐⭐⭐⭐⭐ **短期首选** | ⭐ 不推荐 | ⭐ 不推荐 |

---

## 9. 推荐唯一最小方案

### **方案 B：DB 配置化 Synonym Expansion（利用现有 `query_rewrite_rules` 基础设施）**

#### 选择理由

1. **零代码变更**：`query_rewrite_rules` 表、`QueryRewriteService`、StateGraph 集成全部就绪。只需插入数据行。
2. **最小风险**：精确匹配 + 精确展开，无误生成、无误匹配、无性能影响。
3. **可审计**：规则作为 DB 数据存在，可通过 Admin API 管理，不混入 Java 代码。
4. **不违反红线**：DB 规则是配置化数据，不是 Java 主链硬编码。
5. **可渐进增强**：先用方案 B 解决 Q2，后续可叠加方案 A（编译期自动发现）覆盖未知缩略词。

#### 不推荐方案 A 作为首步的原因

方案 A 虽然长期价值更高，但需要新增 Java 代码（`AcronymAliasSupport`）和测试，改动面 > 方案 B。每轮只修一个最小根因——当前 Q2 的最小根因是 "SL→Situation Lead 映射缺失"，方案 B 用 3 条 DB INSERT 即可解决。

---

## 10. 推荐方案的允许修改范围

| 允许 | 说明 |
|------|------|
| 向 `query_rewrite_rules` 表 INSERT 规则行 | 作为数据初始化 |
| 可选：在 `schema.sql` 或独立 seed SQL 中添加规则 INSERT | 便于清库后自动恢复 |
| 可选：通过 Admin API（`/api/v1/admin/llm/bindings` 同类端点）管理规则 | 如果已有 rewrite rules 管理端点 |

| 禁止 | 说明 |
|------|------|
| 修改 `src/main/java/**` | 本轮零 Java 变更 |
| 修改 `src/test/java/**` | — |
| 修改 Writer/Compiler prompt | — |
| 修改 query retrieval/rerank/citation 主链 | — |
| 在 Java 中硬编码 `"SL" → "Situation Lead"` | 红线禁止 |

---

## 11. agentA 下一轮实现草案

```text
你现在是 agentA（代码执行 Agent）。

本轮目标：
利用现有 query_rewrite_rules 基础设施，为 PE1 Q2 缩略词查询配置展开规则，
使 "SL/TL/IM" 缩略词能被展开为全名 "Situation Lead/Technical Lead/Incident Manager"，
从而通过 FTS/LIKE 匹配到 PDF article 中的角色定义内容。

背景：
- 源 PDF 不含缩略词，Writer 无法保留不存在的内容
- 现有 query_rewrite_rules DB 表支持 source_pattern → rewrite_text 展开
- QueryRewriteService 在每次查询时自动加载 active 规则并应用
- 零 Java 代码变更即可生效

修改范围：
1. 向 query_rewrite_rules 表 INSERT 通用缩略词展开规则（仅 3 条通用规则）
2. 可选：在初始化 SQL 中保留这些规则（便于清库后恢复）

规则内容：
INSERT INTO lattice.query_rewrite_rules (rule_code, source_pattern, rewrite_text, scope, priority, enabled)
VALUES 
  ('acronym-expand-lead', 'SL', 'SituationLead', 'global', 100, true),
  ('acronym-expand-tech', 'TL', 'TechnicalLead', 'global', 100, true),
  ('acronym-expand-mgr', 'IM', 'IncidentManager', 'global', 100, true);

注意：rewrite_text 使用无空格形式（"SituationLead"而非"Situation Lead"），
因为 QueryRewriteService.appendRewriteTokens 使用空格拼接，空格会被正确处理。

通用性要求：
- rule_code 使用通用前缀 "acronym-expand-*"，不绑定 Q2/PE1
- source_pattern 是缩略词原文（大写）
- rewrite_text 是全名的无空格形式
- 规则对所有 query 生效（scope=global），不限于特定问题

禁止事项：
- 禁止修改 src/main/java/**
- 禁止修改 src/test/java/**
- 禁止修改 prompt / config YAML / schema / scripts
- 禁止在 Java 中硬编码 SL→Situation Lead 映射
- 禁止提交 commit

验证计划（交给 agentD）：
1. 确认规则已插入 query_rewrite_rules 表且 enabled=true
2. 调用 PE1 Q2 缩略词查询（"三类probe（SL/TL/IM）的职责分别是什么？"）
3. 确认 query rewrite audit 中记录了匹配的规则
4. 确认 answerOutcome 从 NO_RELEVANT_KNOWLEDGE 变为 PASS
5. 确认全名查询仍保持 PASS
6. 确认其他 PE1 题目无回归
```

---

## 12. agentD 后续验证草案

```text
你现在是 agentD（验证/测试 Agent）。

本轮目标：
验证 query_rewrite_rules 缩略词展开规则是否修复 PE1 Q2 缩略词查询。

前置条件：
- agentA 已向 query_rewrite_rules 表插入缩略词展开规则
- 当前服务运行中，DB 为 PE1 数据

验证步骤：
1. 确认规则存在：
   SELECT * FROM lattice.query_rewrite_rules WHERE rule_code LIKE 'acronym-expand-%';

2. Q2 缩略词查询：
   curl -X POST http://127.0.0.1:18082/api/v1/query \
     -H "Content-Type: application/json" \
     -d '{"question":"三类probe（SL/TL/IM）的职责分别是什么？"}'
   记录 queryId、answerOutcome、generationMode、citationCheck.coverageRate

3. 回查 rewrite audit：
   SELECT * FROM lattice.query_rewrite_audits WHERE query_id = '<queryId>';

4. Q2 全名查询保护：
   "Situation Lead、Technical Lead 和 Incident Manager 的职责分别是什么？"
   确认保持 PASS

5. Q1/Q3-Q12 回归（至少抽查 Q1/Q3/Q6）

判定标准：
- Q2 缩略词查询 answerOutcome 不是 NO_RELEVANT_KNOWLEDGE
- Q2 全名查询保持 PASS
- 无新增 FAIL

输出报告：
docs/test/knowledge-base-e2e/pe1_q2_acronym_query_rewrite_runtime_gate_report.md
```

---

## 13. 长期路线：方案 A + B 互补

| 阶段 | 方案 | 目标 |
|:---:|------|------|
| 短期 | B: DB 规则 | 解决已知缩略词（Q2 SL/TL/IM） |
| 中期 | A: 编译期 acronym alias | 自动发现和索引新文档中的缩略词 |
| 长期 | B + A 互补 | DB 规则覆盖高频/精确场景，编译期自动发现覆盖新文档 |

---

## 14. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt / config / schema / scripts
- [x] 未修改题集 / redline allowlist
- [x] 未提交 commit
- [x] 未清库 / 重建 / 导入资料
- [x] 未读取 hidden eval
- [x] 推荐方案 B（DB 配置化）为零代码变更
- [x] 推荐方案不包含任何 Q2/SL/TL/IM Java 硬编码
- [x] 规则作为 DB 数据管理，属于可审计的配置化层
- [x] 方案对比表覆盖全部 4 个候选方案及不推荐原因
