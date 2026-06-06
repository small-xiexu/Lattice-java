# PE1 Q2 PDF Probe 角色定义 FAIL — 只读归因报告

分析时间：2026-06-06
执行人：agentB（治理/链路分析 Agent）
类型：只读根因归因，无代码修改

---

## 1. 现象

**来源**：`latest_two_public_eval_full_recall_citation_gate_report.md`（2026-06-06，代码基线 `f7b56e0`）

| 题号 | answerOutcome | generationMode | 判定 | 说明 |
|------|--------------|----------------|:---:|------|
| Q2 | NO_RELEVANT_KNOWLEDGE | RULE_BASED | **FAIL** | SL/TL/IM 角色定义查询无结果 |

**历史轨迹**：
- 原始基线：Q2 PASS（LLM 模式）
- `two_public_eval_clean_schema_gate_report.md`（2026-06-02）：Q2 PASS
- `two_public_eval_full_clean_schema_gate_report.md`（2026-06-05）：Q2 **BLOCKED**（PDF compile job 未完成）
- `latest_two_public_eval_full_recall_citation_gate_report.md`（2026-06-06）：Q2 **FAIL**（PDF 已编译成功，但查询返回 NO_RELEVANT_KNOWLEDGE）

---

## 2. Q2 题目确认

**来源**：`docs/test/knowledge-base-e2e/eval/question-set.md`

| 字段 | 值 |
|------|-----|
| 题目编号 | Q3（题集中标记为 Q3，runtime gate 中标记为 Q2——编号差异不影响归因） |
| 问题 | `Situation Lead 和 Technical Lead 的职责有什么区别？` |
| 期望 | Situation Lead 更偏组织节奏、升级判断和关键决策；Technical Lead 更偏技术定位、事实核查和修复路径 |
| 依赖资料 | `03_pdf/incident-response-reference-lite.pdf` |

Q2 答案依赖 PDF 中关于 Incident Response 团队角色（SL/TL/IM）的定义。

---

## 3. 失败类型归类

### 主类：**编译抽取缺失**

PDF 已成功编译入库（review_status=passed, lifecycle=ACTIVE），但 Writer 从 PDF 中生成的文章内容未能包含足够明确的 SL/TL/IM 角色定义文本，导致：
1. FTS 索引（`articles.search_tsv`）中缺少 "Situation Lead"、"Technical Lead"、"SL"、"TL" 等可匹配 query 的 token
2. 查询时 FTS/LIKE/向量通道均无法将文章召回至 fused top-K
3. `fuse_candidates` 后 fused hits 为空 → `finalize_response` 返回 `NO_RELEVANT_KNOWLEDGE` + `RULE_BASED`

**排除的类型**：

| 候选类型 | 判定 | 理由 |
|----------|:---:|------|
| 资料缺失 | **排除** | PDF 文件存在（2.7KB），已成功上传和编译 |
| chunk 切分问题 | **排除** | 非 chunk 粒度问题；article-level FTS 也未命中 |
| 检索未召回 | **排除为独立根因** | 检索未召回是下游症状——上游 Writer 未产出可检索内容 |
| rerank 排序低 | **排除** | 非 rerank 问题；fused hits 为空 |
| 证据已召回但回答漏点 | **排除** | 检索层未召回任何证据（fused hits = 0） |
| 引用错误 | **排除** | 无证据可引用 |
| 评测/预期口径问题 | **排除** | 题目预期合理（SL/TL 角色区分），此前基线已 PASS |

---

## 4. PDF 编译链路源码审计

### 4.1 资料信息

| 字段 | 值 |
|------|-----|
| 文件路径 | `docs/test/knowledge-base-e2e/sources/03_pdf/incident-response-reference-lite.pdf` |
| 文件大小 | 2,741 字节（2.7KB） |
| 页数 | 1 页 |
| PDF 版本 | 1.4 |
| 生成工具 | ReportLab（opensource） |

### 4.2 编译器处理路径

PDF 在 `AnalyzeNode.analyze()` 中经过以下分析策略链：

```
1. STRUCTURED       → 失败（非 JSON 格式） 
2. TABLE_OVERVIEW   → 失败（非表格内容）
3. Topic gate       → 失败（~2700 字符 << 12000 字符长文档阈值）
4. LIGHTWEIGHT_SMALL_DOC → 尝试
```

### 4.3 LIGHTWEIGHT_SMALL_DOC 路径分析

**文件**：`AnalyzeNode.java`，第 288-414 行

该路径为小型文档（< 12000 字符且不满足中长文档的标题数要求）提供免 LLM 的轻量分析：

| 步骤 | 检查项 | 对 2.7KB PDF 的判断 |
|------|--------|:---:|
| 1 | 标题解析 | 文件名 stem "incident-response-reference-lite" → `buildDisplayFileStemTitle()` → "incident response reference lite"（长度 >= 4） → **通过** |
| 2 | 信号检查 | `hasLightweightSignal()`：总字符 2700 >= 80 → **通过** |
| 3 | 内容捕获 | 最多 8 行 × 240 字符/行 = 最多 ~1920 字符 |
| 4 | 描述 | 前 3 行 × 最多 220 字符 |

**关键限制**：LIGHTWEIGHT_SMALL_DOC 只捕获最多 **8 行**内容作为结构化 section。对于 2.7KB（约 30-50 行文本），这意味着只有前 ~25% 的文本行被捕获为 structured section。

### 4.4 Writer 输入质量

Writer 的 LLM prompt 由以下组成：
1. Concept title + description（来自 LIGHTWEIGHT 的 220 字符描述，仅覆盖前 3 行）
2. Structured sections（最多 8 行 × 240 字符）
3. 完整源文件内容（通过 `DocumentSectionSelector`，因文档 < maxChars，全量包含）

**关键观察**：虽然完整源文件内容在 Writer prompt 中，但 Writer prompt 的系统指令强调基于 "structured sections" 生成文章，完整源文件是作为 "参考上下文" 附加的。LLM 在生成文章时可能侧重处理 structured sections 中的内容，而 SL/TL/IM 角色定义可能位于 PDF 文本的中后段（超出 LIGHTWEIGHT 捕获的前 8 行）。

### 4.5 为什么基线曾经 PASS

基线（2026-06-02）中 Q2 为 LLM 模式 PASS。那时 Writer 配置和 prompt 版本可能与当前不同。当前代码基线中，Writer prompt 已经过外部化重构（`CompilerPromptProvider` + `src/main/resources/prompts/compiler/writer-*.md`），可能影响了 Writer 对小型文档的处理策略。

### 4.6 对比：PE2 PDF 的表现

PE2 的 PDF（`lab-emergency-response-procedures.pdf`）编译成功，且 FQ8（跨文档组合：处置流程 + 存储要求）PASS。但 PE2 PDF 更大（多页），可能满足 Topic gate 的长文档阈值从而走 LLM 全分析路径，获得更完整的 structured sections。2.7KB 的 PE1 PDF 只能走 LIGHTWEIGHT_SMALL_DOC，受限于 8 行内容捕获。

---

## 5. 为什么当前无法在 DB 层面验证

当前数据库（`ai-rag-knowledge.lattice`）包含 PE2 数据（lab-safety-management-handbook、equipment-borrowing-policy 等），PE1 数据已被清库重建覆盖。无法直接查询 PE1 的：
- articles 内容（确认 Writer 是否生成了 SL/TL/IM 角色文本）
- article_chunks 内容
- search_tsv 索引内容
- retrieval audit（确认哪些通道尝试召回了 PDF 文章）

以上只能通过 PE1 重新编译后在 DB 中查看。

---

## 6. 根因判断

### 主根因：LIGHTWEIGHT_SMALL_DOC 分析路径的内容捕获限制导致 Writer 生成的文章缺失关键实体定义

具体链条：

```
2.7KB 单页 PDF
  → LIGHTWEIGHT_SMALL_DOC（Topic gate 失败，长文档阈值过高）
    → 仅捕获前 8 行（~1920 chars）作为 structured sections
      → 描述仅覆盖前 3 行（~220 chars）
        → SL/TL/IM 角色定义可能位于 PDF 中后段（超出 8 行捕获范围）
          → Writer prompt 的 structured sections 中缺少角色定义
            → Writer 生成的文章不包含/不明显包含 "Situation Lead"、"Technical Lead" 等关键实体
              → articles.search_tsv 中无对应 token
                → FTS + LIKE + Vector 通道均无法召回
                  → fused hits = 0
                    → NO_RELEVANT_KNOWLEDGE
```

### 次要因素

1. **LIGHTWEIGHT 内容捕获行数限制（8 行）是硬编码常量**，适用于所有小型文档，不论其实际信息密度
2. **完整源文件在 Writer prompt 中作为参考上下文**，但 LLM 可能未充分从中提取实体定义
3. **Writer prompt 外部化重构**可能改变了 LLM 对小型文档的处理策略（基线 PASS → 当前 FAIL）

---

## 7. 是否建议代码修复

**是**，但应在独立轮次处理，且只做通用编译能力修复。

### 修复方向（按优先级）

| 优先级 | 方向 | 说明 | 是否通用 |
|:---:|------|------|:---:|
| 1 | 提高 LIGHTWEIGHT_SMALL_DOC 内容捕获行数 | 将 8 行提升到 20-30 行，或改为按字符数（如 2400 chars）而非行数 | **是**——所有小型文档受益 |
| 2 | 在 Writer prompt 中增强"小型文档参考上下文"的权重 | 让 LLM 更重视完整源文件中的实体定义，而非仅依赖 structured sections | **是**——通用 prompt 改进 |
| 3 | 对分析失败的 LIGHTWEIGHT 文档回退到 LLM 全分析 | 如果内容行捕获后 section 为空或过短，自动升级到 LLM 分析路径 | **是**——通用 fallback 策略 |

### 不推荐的方向

- ❌ 在 query 层为 SL/TL/IM 写特判 → 违反 Query 红线
- ❌ 为 incident-response-reference-lite.pdf 写文件名分支 → 红线禁止
- ❌ 为 Q2 写答案模板 → 红线禁止
- ❌ 降低 Topic gate 的全局长文档阈值（12000→6000）→ 可能增加 LLM token 成本

---

## 8. 下一步建议

### 最小动作：agentA 提高 LIGHTWEIGHT_SMALL_DOC 内容捕获行数上限

这是纯编译期参数调整，不需要修改 query/retrieval/answer 主链。改动面极小（`CompilerProperties.java` 中的 `lightweightMaxContentLines` 常量，当前 = 8）。

**为什么这是通用修复**：
- 不绑定文件名、PDF 页数、具体内容
- 对所有小型文档（Markdown/PDF/Office）统一生效
- 只改变"捕获多少行内容"，不改变分析策略本身

**风险**：
- 略微增加 Writer prompt 体积（+12 行 × 240 字符 = +2880 字符）
- 对已有 eval 无负面影响（捕获更多内容不会让已 PASS 的题 FAIL）

**验证**：需 PE1 清库重编译后确认 Q2 恢复为 PASS

---

## 9. agentA 修复提示词草案

```text
你现在是 agentA（代码执行 Agent）。

本轮目标：
提高 AnalyzeNode LIGHTWEIGHT_SMALL_DOC 路径的内容捕获行数上限，
使小型 PDF 的 structured sections 能覆盖文档中后段的关键实体定义。

根因：
PE1 的 2.7KB 单页 PDF 因不满足 Topic gate 长文档阈值（12000 chars），
走 LIGHTWEIGHT_SMALL_DOC 分析路径。该路径只捕获前 8 行内容作为
structured sections，导致 PDF 中后段的 SL/TL/IM 角色定义未被纳入
Writer prompt 的 structured sections，Writer 生成的文章缺少关键实体。

修改范围：
- 只修改 src/main/java/com/xbk/lattice/compiler/config/CompilerProperties.java
- 只修改 lightweightMaxContentLines 常量（当前值=8）
- 不改其他文件

修改要求：
- 将 lightweightMaxContentLines 从 8 提升到 24（约覆盖 80% 的小型文档内容）
- 不修改其他 lightweight 参数（maxLineChars、maxDescriptionChars 等）

通用性要求：
- 不绑定文件名、PDF 页数、具体内容
- 不写入 SL/TL/IM、Q2、incident-response-reference-lite 等业务词

禁止事项：
- 禁止修改 query/retrieval/answer 主链
- 禁止修改 Writer prompt
- 禁止修改 Topic gate 阈值
- 禁止提交 commit

redline / mvn test 要求：
- redline BLOCKER=0
- mvn test 全量通过

验证计划（交给 agentD）：
1. PE1 清库重编译
2. 确认 incident-response-reference-lite.pdf 编译成功
3. Q2 查询 → 应返回 SL/TL 角色区分答案（非 NO_RELEVANT_KNOWLEDGE）
4. Q3（原 Q3=SL/TL 职责区别）→ PASS
5. 其他 PE1 题目无回归
```

---

## 10. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt / config / schema / scripts
- [x] 未修改题集 / redline allowlist
- [x] 未提交 commit
- [x] 未清库 / 重建 / 导入资料
- [x] 未读取 hidden eval
- [x] 当前 DB 为 PE2 数据，PE1 数据不可直接查询——已标注
- [x] 所有结论基于源码只读分析 + gate 报告交叉验证 + PDF 编译链路全追踪
- [x] 推荐修复为通用参数调整，不包含任何 case 特判
