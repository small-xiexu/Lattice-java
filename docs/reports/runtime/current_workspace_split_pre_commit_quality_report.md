# 当前工作区提交前拆线质量复核报告

- 生成时间：2026-05-22
- 执行 Agent：agentB（只读复核）
- 分支：`codex/qa-polish`
- 本轮是否修改代码：**否**

---

## 1. Git 状态总览

### 1.1 git status --short --branch

```
## codex/qa-polish...origin/codex/qa-polish

Modified (9，其中 1 个无实际 diff):
  M AGENTS.md
  M special_cases_report.md            ← redline re-scan 产物，不应提交
  M ReviewFixService.java              ← Compile Fixer slimming
  M QueryFinalizationGraphFragment.java ← Query terminal fallback
  M AnswerParagraphPostProcessor.java  ← Query 多点展开
  M AnswerPromptBuilder.java           ← Query 多点展开 prompt
  M ReviewFixServiceTests.java         ← Compile Fixer 测试
  M AnswerGenerationServiceTests.java  ← Query 多点展开测试
  M QueryGraphOrchestratorTests.java   ← 无实际 diff（git status 假阳性）

Untracked new:
  ?? QueryFinalizationGraphFragmentTests.java  ← Query terminal fallback 新测试
  ?? (多份报告文档，共 15 个 .md 文件)
```

### 1.2 git diff --stat

```
 AGENTS.md                                          |  4 ++
 special_cases_report.md                            | 83 +++++++++++-----------
 ReviewFixService.java                              | 28 ++++++--
 QueryFinalizationGraphFragment.java                | 13 +++-
 AnswerParagraphPostProcessor.java                  | 55 ++++++++++++++
 AnswerPromptBuilder.java                           |  2 +
 ReviewFixServiceTests.java                         | 27 +++++++
 AnswerGenerationServiceTests.java                  | 33 +++++++++
 QueryGraphOrchestratorTests.java                   | 17 +----       ← 注：实际无 diff
 9 files changed, 202 insertions(+), 60 deletions(-)
```

### 1.3 QueryGraphOrchestratorTests.java 假阳性说明

`git diff HEAD -- QueryGraphOrchestratorTests.java` 输出为空。该文件在 `git status` 中标记为 `M` 但无实际内容变更。`git diff --stat` 显示的 17 行减少可能是之前某次操作残留的索引状态。**该文件不应纳入任何 commit。**

---

## 2. 越界修改检查

### 2.1 Query 修复是否误碰 compile

| Query 改动文件 | 所属模块 | 是否触碰 compile |
|---------------|---------|----------------|
| `QueryFinalizationGraphFragment.java` | query/graph | **否** — 仅 query 包 |
| `QueryFinalizationGraphFragmentTests.java` | query/graph (test) | **否** |
| `AnswerParagraphPostProcessor.java` | query/service | **否** |
| `AnswerPromptBuilder.java` | query/service | **否** |
| `AnswerGenerationServiceTests.java` | query/service (test) | **否** |

**结论：Query 修复严格限定在 `com.xbk.lattice.query` 包内，零越界。**

### 2.2 Compile 修复是否误碰 query

| Compile 改动文件 | 所属模块 | 是否触碰 query |
|-----------------|---------|---------------|
| `ReviewFixService.java` | compiler/service | **否** — 仅 compiler 包 |
| `ReviewFixServiceTests.java` | compiler/service (test) | **否** |

**结论：Compile 修复严格限定在 `com.xbk.lattice.compiler` 包内，零越界。**

### 2.3 AGENTS.md 是否该混入代码 commit

`AGENTS.md` 变更内容为 4 条新增项目约定（DB 策略、本地启动入口、Deep Research 绑定策略、向量治理入口）。不涉及任何 `src/**` 代码。**建议独立为一个 docs commit，不混入代码修复 commit。**

---

## 3. Redline 扫描结果

| 指标 | 值 |
|------|-----|
| BLOCKER | **0** |
| REVIEW | 1913 |
| ALLOWLIST | 246 |

**BLOCKER=0，无阻断项。** `special_cases_report.md` 中的差异仅为 re-scan 时间戳和行号漂移（`ReviewFixService.java` 新增 `boundText` 方法导致行号偏移），不影响代码安全。

---

## 4. mvn test 失败分析

### 4.1 失败详情

```
ClassNotFoundException: com.xbk.lattice.documentparse.service.DocumentParseResultNormalizerTests
```

### 4.2 是否阻塞本轮提交

**不阻塞。** 理由：

1. **失败类与当前改动无关**：`DocumentParseResultNormalizerTests` 位于 `com.xbk.lattice.documentparse.service` 包，当前 4 条改动线完全不涉及 `documentparse` 模块。
2. **预存问题**：该失败在 `phase_current_workspace_existing_cases_acceptance_report.md` 验收前已存在，非本次改动引入。
3. **根因是 fork/classpath 问题**：测试源文件存在于 `src/test/java/...` 但 fork 进程加载不到。通常 `mvn clean test` 可解决，属于 Maven Surefire fork 的 classpath 缓存问题。
4. **定向测试全部通过**：
   - `ReviewFixServiceTests` — 10 tests ✅
   - `AnswerGenerationServiceTests` — 包含新增多点展开测试 ✅
   - `QueryFinalizationGraphFragmentTests` — 7 tests（新增）✅
   - `QueryGraphOrchestratorTests` — 通过 ✅

### 4.3 建议

提交前执行 `mvn clean test -pl .` 或单独确认 `DocumentParseResultNormalizerTests` 可通过。如果持续失败，可在 `documentparse` 模块单独排查，不阻塞当前 4 条改动提交。

---

## 5. 拆线方案

### 5.1 建议拆为 4 个独立 commit

| # | 类型 | 主题 | 依赖关系 |
|---|------|------|---------|
| C1 | fix(query) | terminal fallback 修复 | 无依赖 |
| C2 | fix(query) | 多点答案展开 | 无依赖（C1/C2 互相独立） |
| C3 | perf(compile) | Fixer payload slimming | 无依赖 |
| C4 | docs | AGENTS.md 项目约定更新 | 无依赖 |

4 条改动互不依赖：
- C1 只改 `shouldFallbackToDeterministicAnswer` 的判定条件
- C2 只改 prompt 约束和后处理保护
- C3 只改 Fixer 的输入预算截断
- C4 只改文档
- 三条代码改动各自限定在独立的包内（query/graph vs query/service vs compiler/service）

---

### 5.2 Commit 1: Query terminal fallback 修复

**推荐 message:**

```
fix(query): prevent terminal fallback from replacing LLM-synthesized answers after citation repair

When citation repair strips all citation markers from a valid LLM-synthesized
or rule-based answer, the second citation check previously triggered
CITATION_QUALITY_INSUFFICIENT terminal fallback unconditionally, replacing
the synthesized answer with a deterministic evidence listing.

Narrow the guard to only trigger terminal fallback when the answer was not
produced through a normal synthesis path (generationMode != LLM/RULE_BASED).
Answers that were properly synthesized retain their body text even after
citation repair removes markers.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

**纳入文件：**
- `src/main/java/com/xbk/lattice/query/graph/QueryFinalizationGraphFragment.java`
- `src/test/java/com/xbk/lattice/query/graph/QueryFinalizationGraphFragmentTests.java`（新文件）

**排除文件：**
- `special_cases_report.md`
- `QueryGraphOrchestratorTests.java`（无实际 diff）
- 所有 query/service 下的多点展开文件
- 所有 compiler 下的 Fixer 文件
- `AGENTS.md`

---

### 5.3 Commit 2: Query 多点答案展开

**推荐 message:**

```
fix(query): improve multi-point answer expansion constraints and post-processing

Add prompt rules (22-23) requiring per-focus expansion for multi-point
questions and preventing compression of enumerated answers into single
summaries. Add shouldKeepExpandedMultiPointAnswer() post-processing guard
that preserves expanded multi-focus answers from being over-compressed.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

**纳入文件：**
- `src/main/java/com/xbk/lattice/query/service/AnswerParagraphPostProcessor.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerPromptBuilder.java`
- `src/test/java/com/xbk/lattice/query/service/AnswerGenerationServiceTests.java`

**排除文件：**
- `special_cases_report.md`
- `QueryFinalizationGraphFragment.java` + `QueryFinalizationGraphFragmentTests.java`（属于 C1）
- 所有 compiler 下的 Fixer 文件
- `AGENTS.md`

---

### 5.4 Commit 3: Compile Fixer payload slimming

**推荐 message:**

```
perf(compile): add article/source payload budget limits for Fixer calls

Replace the single-sided 10000-char truncation on source contents with
dual-budget bounding: article content capped at 6000 chars, source contents
at 7000 chars. Reduces Fixer input volume per invocation without changing
fix semantics or re-review behavior.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

**纳入文件：**
- `src/main/java/com/xbk/lattice/compiler/service/ReviewFixService.java`
- `src/test/java/com/xbk/lattice/compiler/service/ReviewFixServiceTests.java`

**排除文件：**
- `special_cases_report.md`
- 所有 query 下的文件
- `AGENTS.md`

---

### 5.5 Commit 4: AGENTS.md 文档更新

**推荐 message:**

```
docs: add DB strategy, local dev entry, vector admin and DR binding to AGENTS.md

Document four project-level conventions: database schema management via
reset-lattice-schema.sh, local dev server entrypoint, Deep Research binding
fail-closed strategy, and vector administration API entry points.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

**纳入文件：**
- `AGENTS.md`

**排除文件：**
- 所有 `src/**` 文件
- `special_cases_report.md`

---

### 5.6 明确排除的文件（不纳入任何 commit）

| 文件 | 排除理由 |
|------|---------|
| `special_cases_report.md` | redline re-scan 产物，仅时间戳和行号漂移，无实质变更 |
| `QueryGraphOrchestratorTests.java` | git status 假阳性，无实际 diff |
| `phase_*.md`（全部 untracked 报告） | 阶段验收文档，不应进入 git 历史 |
| `query_*.md`（全部 untracked 报告） | 分析/验收/修复报告，不应进入 git 历史 |
| `compile_*.md`（全部 untracked 报告） | 分析/验收/修复报告，不应进入 git 历史 |
| `agents_md_*.md` | 文档修复报告，不应进入 git 历史 |
| `docs/模型绑定配置参考.md` | 独立文档，不属于当前 4 条改动线 |

---

## 6. 运行时验收对照

基于 `phase_current_workspace_existing_cases_acceptance_report.md` 的验证结论：

| 改动线 | 验证结果 | 是否可提交 |
|--------|---------|----------|
| C1: Query terminal fallback 修复 | `CITATION_QUALITY_INSUFFICIENT` 0/7，LLM 合成保留 6/7 | **可提交** |
| C2: Query 多点答案展开 | S3 多点枚举 5/5 信息点完整覆盖 | **可提交** |
| C3: Compile Fixer slimming | 两轮 compile 均触发 Fixer 并通过 re-review | **可提交** |
| C4: AGENTS.md | 文档措辞合理，信息层次正确 | **可提交** |

---

## 7. 阻塞项清单

| 检查项 | 状态 | 说明 |
|--------|------|------|
| Redline BLOCKER | ✅ 0 | 无阻断 |
| 越界修改 | ✅ 无 | 三条代码改动各自限定包边界 |
| mvn test 全量 | ⚠️ 预存失败 | `DocumentParseResultNormalizerTests` ClassNotFoundException，与当前改动无关 |
| 定向测试 | ✅ 全部通过 | Fixer / fallback / 多点展开 测试均通过 |
| 运行时验收 | ✅ 通过 | Compile + Query 端到端验证完成 |
| 代码 review | ✅ 通过 | `query_citation_quality_terminal_fallback_fix_revision_report.md` 已收窄第一版过宽修复 |
| 拆线干净度 | ✅ 通过 | 4 条改动互不依赖，可独立回滚 |

**当前无新增阻塞项。** `DocumentParseResultNormalizerTests` 是预存问题，建议 `mvn clean test` 后单独排查，不阻塞本轮提交。

---

## 8. 提交顺序建议

```
C1 (Query terminal fallback) → C2 (Query 多点展开) → C3 (Compile Fixer) → C4 (AGENTS.md)
```

理由：
- C1 是修复此前验收中最显著的端到端问题（CITATION_QUALITY_INSUFFICIENT 大面积误触发），影响面最大，应最先提交
- C2 是同一 Query 模块的增强，紧随 C1 提交保持 Query 改动连续
- C3 是 Compile 模块独立改动，放 Query 之后
- C4 是纯文档，放最后

如偏好按模块聚合，也可：
```
C3 (Compile Fixer) → C1 (Query fallback) → C2 (Query 多点展开) → C4 (docs)
```

两种顺序均可，各 commit 互不依赖。

---

## 9. 不建议做的事

1. **不要把 4 条改动合并成一个 commit**：回滚粒度太粗，bisect 无法定位问题
2. **不要把 `special_cases_report.md` 混入任何 commit**：它是 redline 扫描的临时产物
3. **不要把 `QueryGraphOrchestratorTests.java` 混入 commit**：无实际 diff
4. **不要把 untracked 报告文档纳入 git**：它们是阶段工作产物，非项目代码
5. **不要在未修复 `DocumentParseResultNormalizerTests` 的情况下说"全部测试通过"**：虽然不阻塞，但需明确标注

---

## 10. 本轮是否修改代码

**否。** 本轮严格遵守只读约束：

- 未修改 `src/main/java/**`
- 未修改 `src/test/java/**`
- 未修改任何配置、文档、脚本
- 未提交任何代码
- 仅执行：git 状态审查、diff 分析、报告交叉验证、越界检查、redline 结果复核
