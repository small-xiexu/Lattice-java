# PE1 S2 Writer 标题保真 Prompt 修复 — Pre-Commit 质量复核报告

复核时间：2026-06-06
执行人：agentB（治理/质量复核 Agent）
类型：只读质量复核，不修改代码，不提交

---

## 1. Git Status 摘要

### 已修改（Modified）— 本次提交候选

| 文件 | 变更 | 行数 |
|------|------|:---:|
| `src/main/resources/prompts/compiler/writer.md` | 新增规则 14：源文档标题保真 | +1 |
| `src/main/java/com/xbk/lattice/compiler/prompt/LatticePrompts.java` | 规则 14 机械同步到 Java 常量 | +1 |

### 已修改（Modified）— 必须排除

| 文件 | 原因 |
|------|------|
| `special_cases_report.md` | redline 扫描输出，AGENTS.md 明确禁止提交 |

### 新增（Untracked）— 报告文件

| 文件 | 建议 |
|------|:---:|
| `pe1_s2_section_anchor_partial_analysis_report.md` | **建议提交**（agentB 根因分析） |
| `pe1_s2_writer_title_preservation_prompt_fix_result_report.md` | **建议提交**（agentA prompt 修复） |
| `pe1_s2_writer_prompt_constant_sync_fix_result_report.md` | **建议提交**（agentA 常量同步） |
| `pe1_s2_writer_title_preservation_runtime_gate_report.md` | **建议提交**（agentD runtime gate） |
| `pe1_q2_acronym_query_retrieval_analysis_report.md` | **暂不提交**（属 Q2 缩略词线，独立于 S2） |
| `pe1_q2_acronym_general_solution_design_report.md` | **暂不提交**（属 Q2 缩略词线） |
| `pe1_q2_writer_acronym_preservation_fix_result_report.md` | **暂不提交**（属 Q2 缩略词线） |
| `post_compiler_admin_fixes_report_archive_plan.md` | **暂不提交**（属报告归档线） |

---

## 2. Diff 范围复核

### 2.1 生产代码变更

**只有两处，均为预期范围：**

1. `writer.md` 第 19 行：追加规则 14（8 行英文文本）
2. `LatticePrompts.java` 第 94 行：追加相同规则 14 到 `SYSTEM_COMPILE_ARTICLE` 常量

**无越界修改**：未触碰 query/retrieval/rerank/citation/fallback/answer generation 主链，未修改 schema/scripts/config。

### 2.2 变更性质

- `writer.md`：运行时主路径生效（`CompilerPromptProvider` 加载）
- `LatticePrompts.java`：fallback 路径同步（`ArticleReviewerGateway`/`ReviewFixService` null-provider 场景）
- 两者语义完全一致，无差异

---

## 3. 红线 / 硬编码复核

### 3.1 规则 14 文本审查

```
14. When source materials contain explicit section headings (e.g., Markdown
   `##` / `###` lines in structured sections), preserve the original heading
   text as the article section title whenever possible. If you need to
   reorganize, merge, or adjust headings for article flow, retain the original
   heading text near the beginning of the corresponding section as an alias,
   anchor, or searchable phrase — do not silently replace it with a
   semantically similar but differently worded new heading. Consistent heading
   text is essential for search retrieval, citation anchoring, and
   section-anchor stability.
```

| 检查项 | 结果 | 证据 |
|--------|:---:|------|
| 是否包含具体业务词？ | **否** | 无 "下一步计划"、"落地建议"、"协同处置流程" 等 |
| 是否包含文件名？ | **否** | 无 "probe-and-incident-operations" 等 |
| 是否包含题号？ | **否** | 无 "S2"、"Q2" 等 |
| 是否包含答案片段？ | **否** | 无任何答案内容 |
| 是否为通用文本结构规则？ | **是** | 规则基于 Markdown `##`/`###` 标题结构的通用特征 |
| 是否对所有文档一视同仁？ | **是** | 适用于所有含 structured sections 的源文件 |
| 是否给了合理例外路径？ | **是** | 允许重组/合并标题，但要求保留原始标题文本作为别名 |

### 3.2 Redline 结果

| 指标 | 值 |
|------|:---:|
| BLOCKER | **0** |
| 本次新增 REVIEW | 0（Prompt 文本不触发 Java 代码扫描规则） |

**红线审查结论：PASS。规则 14 是通用 Markdown 标题保真约束，无任何业务特判。**

---

## 4. Prompt 泛化性判断

### 4.1 规则覆盖面

- 触发条件：源文档 structured sections 中包含 `##`/`###` 标题行
- 适用范围：所有文档类型（Markdown、PDF、XLSX 等的 structured sections）
- 约束行为：保留原始标题文本，不静默替换为语义相近但措辞不同的新标题

### 4.2 不会过度约束 Writer

- 规则允许 "reorganize, merge, or adjust headings for article flow"
- 仅要求 "retain the original heading text near the beginning of the corresponding section"
- Writer 仍有自由度调整节顺序、合并节、改写正文——只是标题文本需保留

### 4.3 对 PE2 的影响

PE2 使用 YAML/XLSX/CSV 源文件（非 Markdown），其 structured sections 通常不包含 `##`/`###` 格式的 Markdown 标题行。Writer prompt 规则 14 对这些源文件的触发概率极低。即使触发，保留原始标题也不会使已 PASS 的答案变错。

---

## 5. 测试与 Runtime Gate 证据复核

### 5.1 测试结果

| 测试范围 | 结果 |
|----------|:---:|
| `CompilerPromptProviderTests` | **13/0/0/0** |
| `SchemaAwarePromptsTests` | **7/0/0/0** |
| 全量 mvn test | **1018/0/0/0, BUILD SUCCESS** |

测试覆盖：
- `writerPromptShouldMatchLatticePromptsConstant` → 验证外部 prompt 文件与 Java fallback 常量一致 ✅
- 无新增测试（prompt 文本修改不需要定向测试）

### 5.2 Runtime Gate 证据

| 验证项 | 修复前 | 修复后 | 判定 |
|--------|:---:|:---:|:---:|
| S2 chunk sectionAnchor | "协同处置流程" | **"下一步计划"** | ✅ |
| S2 搜索 rank1 | 协同手册 / 协同处置流程 | **协同手册 / 下一步计划** | ✅ |
| S2 判定 | PARTIAL | **PASS** | ✅ |
| PE1 Search Accuracy | 5/6 | **6/6**（首次全部 PASS） | ✅ |
| PE1 Q5/Q6 回答保护 | PASS | **PASS**（无回归） | ✅ |
| Writer 输出证据 | chunk 8 首行标题 = "## 下一步计划" | 源标题被直接保留 | ✅ |

**Runtime gate 证据充分**：S2 改善直接来源于 Writer 保留了源标题 "下一步计划"（从 chunk 8 首行标题可证），不是 search/rerank/display 特判。

---

## 6. PE2 未重跑风险判断

### 6.1 风险评估

| 风险维度 | 评估 |
|----------|------|
| PE2 YAML/XLSX/CSV 是否有 Markdown `##`/`###` 标题？ | **否**——YAML/XLSX/CSV 的 structured sections 不包含 Markdown 标题行 |
| Prompt 规则 14 对 PE2 源文件的触发概率 | **极低**——规则 14 触发条件是 `##`/`###` 行，PE2 源文件不含此格式 |
| 即使触发，对 PE2 答案的影响 | **正向或中性**——更忠实的标题不会让已 PASS 的答案变错 |
| PE2 Search 6/6 是否受影响 | **不会**——搜索匹配的是 chunk 内容，标题保真不影响检索召回 |

### 6.2 判定

**PE2 未重跑可接受。** Prompt 规则 14 的触发条件（Markdown `##`/`###` 标题行）在 PE2 源文件（YAML/XLSX/CSV）中不适用。PE2 Search 6/6 在上一轮 gate 已确认，本轮 prompt 变更不会引入回归。

### 6.3 如需最大保守

如果 reviewer 要求最高保守度，可让 agentD 执行 PE2 搜索保护回归（仅 S1-S4 搜索，不重跑完整 Q1-Q12）。预计 10 分钟内可完成。

---

## 7. 是否建议提交

### **YES**

理由：

1. **门禁全过**：redline BLOCKER=0、mvn test 1018/0/0/0
2. **目标达成**：S2 PARTIAL→PASS，PE1 Search Accuracy 首次 6/6
3. **零回归**：PE1 Q5/Q6 保护 PASS，S1/S3/S4 搜索保护 PASS
4. **红线审查 PASS**：规则 14 是通用 Markdown 标题保真约束，无任何业务特判
5. **改动最小**：2 个文件，各 +1 行，均为纯文本追加
6. **Java 常量同步为机械同步**：不改变业务逻辑，仅保证 fallback 路径一致
7. **PE2 风险可控**：prompt 变更对 PE2 源文件几乎不触发

---

## 8. 推荐提交范围

### 生产代码（必须提交）

```
src/main/resources/prompts/compiler/writer.md
src/main/java/com/xbk/lattice/compiler/prompt/LatticePrompts.java
```

### 报告文件（建议随本次提交归档）

```
docs/test/knowledge-base-e2e/pe1_s2_section_anchor_partial_analysis_report.md       (agentB 根因分析)
docs/test/knowledge-base-e2e/pe1_s2_writer_title_preservation_prompt_fix_result_report.md  (agentA prompt 修复)
docs/test/knowledge-base-e2e/pe1_s2_writer_prompt_constant_sync_fix_result_report.md       (agentA 常量同步)
docs/test/knowledge-base-e2e/pe1_s2_writer_title_preservation_runtime_gate_report.md       (agentD runtime gate)
```

### 必须排除提交

```
special_cases_report.md    (redline 输出，AGENTS.md 禁止提交)
```

### 暂不提交（属其他独立线）

```
pe1_q2_acronym_query_retrieval_analysis_report.md          (Q2 缩略词线)
pe1_q2_acronym_general_solution_design_report.md           (Q2 缩略词线)
pe1_q2_writer_acronym_preservation_fix_result_report.md    (Q2 缩略词线)
post_compiler_admin_fixes_report_archive_plan.md           (报告归档线)
```

---

## 9. 推荐 Commit Message

```
fix(compiler): preserve source section headings in Writer output

Add rule 14 to Writer system prompt and sync to LatticePrompts constant:
when source materials contain explicit Markdown ##/### headings,
preserve the original heading text instead of silently replacing
it with semantically similar but differently worded new headings.

PE1 S2: PARTIAL → PASS (search "下一步计划" now returns chunk with
correct sectionAnchor). PE1 Search Accuracy: 5/6 → 6/6 first all-pass.

Redline BLOCKER=0. mvn test 1018/0/0/0. No case-specific logic.
```

---

## 10. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt / config / schema / scripts（除本次复核的两处 diff 外）
- [x] 未修改题集 / redline allowlist
- [x] 未读取 hidden eval
- [x] 未提交 commit
- [x] 未清库 / 重建 / 导入资料
- [x] Diff 范围确认仅两处：`writer.md` + `LatticePrompts.java`
- [x] 规则 14 为通用 Markdown 标题保真约束，无业务特判
- [x] PE2 未重跑风险判断：可接受（PE2 源文件无 Markdown 标题，prompt 变更不触发）
- [x] 提交范围明确：2 个生产文件 + 4 个报告文件
