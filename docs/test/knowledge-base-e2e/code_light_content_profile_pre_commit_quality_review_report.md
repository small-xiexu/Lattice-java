# CODE_LIGHT ContentProfile — 提交前质量复核报告

审查时间：2026-06-07
执行人：agentB（治理/提交前质量复核 Agent）
类型：只读质量复核，不改代码，不提交

---

## 1. 结论

### **READY_TO_COMMIT**

CODE_LIGHT contentProfile 实现完整、门禁全过、runtime gate 验证通过。可以进入 `/code-commit`。

---

## 2. 代码改动摘要

| 文件 | 变更 | 行数 |
|------|------|:---:|
| `CompileExecutionRequest.java` | contentProfile 字段 + 常量 + normalize + isCodeLightProfile + hyphen 修复 | +50 |
| `CompileGraphState.java` | contentProfile Lombok 字段 | +1 |
| `CompileGraphStateKeys.java` | CONTENT_PROFILE 常量 | +1 |
| `CompileGraphStateMapper.java` | contentProfile 读写 | +2 |
| `StateGraphCompileOrchestrator.java` | contentProfile 传递 | +1 |
| `CompileJobService.java` | 从 KnowledgeSource 读取 contentProfile | +1 |
| `CompileGraphConditions.java` | CODE_LIGHT 条件路由（full + incremental） | +12 |
| `CompileGraphDefinitionFactory.java` | 注册 BuildLightweightArticlesNode + 边 | +10 |
| `BuildLightweightArticlesNode.java`（新增） | 从 source chunks 构建最小 ArticleRecord | +190 |

**9 个文件，全部在 `compiler/` 包内。零触碰 query/answer/rerank/fallback/citation/deepresearch。**

---

## 3. Runtime Gate 摘要

| 验证项 | 结果 |
|--------|:---:|
| CODE_LIGHT full compile | **SUCCEEDED** |
| writer/reviewer/fixer 出现次数 | **0** ✅ |
| article reviewStatus | 全部 **passed** ✅ |
| article content | 源码原文（非 LLM 改写） ✅ |
| 搜索 6/6 | **全部命中** ✅ |
| 问答 4/5 | FQ1/FQ3/FQ5/FG3 PASS，FQ10 FAIL（跨文件调用链串联不足） |
| Citation | 指向真实源码路径 ✅ |
| Redline | BLOCKER=0 |
| mvn test | 1018/0/0/0 BUILD SUCCESS |

---

## 4. 红线检查

| 检查项 | 结果 |
|--------|:---:|
| 是否修改 query/answer/rerank/fallback/citation/deepresearch？ | **否** |
| 是否修改 schema.sql？ | **否** |
| 是否修改 prompt 文件？ | **否** |
| 是否修改 scripts？ | **否** |
| 是否修改题集？ | **否** |
| DOCUMENT profile 是否保持默认行为？ | **是**（null/blank/未知 → DOCUMENT） |
| 是否在代码中写业务词/类名/方法名/文件名特判？ | **否** |
| 是否引入新 source type？ | **否** |

---

## 5. 已知后置风险

| 风险 | 严重度 | 是否阻塞提交 | 后续处理 |
|------|:---:|:---:|------|
| `hasEnhancements` 增量路径走 LLM | 低 | **否** | Fresh CODE_LIGHT 不触发；后续可改为 hasEnhancements 也走 build_lightweight_articles |
| `generate_synthesis_artifacts` 仍在图中 | 极低 | **否** | 节点对 CODE_LIGHT 产出最小/无输出，不引入 LLM 调用；后续可选跳过 |
| `Files.readString` 无大小限制 | 低 | **否** | 源码文件 < 100KB；Phase 2 加上限 |
| 手动 JSON 拼接 | 低 | **否** | Phase 2 改用 ObjectMapper |

---

## 6. FQ10 是否阻塞判断

**不阻塞。** FQ10（"查询支付订单的完整调用链"）失败是因为 CODE_LIGHT 下每文件 = 独立 article，LLM 未能跨文件串联 Controller→Service→Mapper→SQL 的调用链。这是 CODE_LIGHT 模式的**固有特征**（跳过 Writer 就没有 LLM 合成的跨文件文章），不是编译路径缺陷。Design report 已明确此限制。

---

## 7. 精确建议提交文件清单

### 生产代码（必须提交，9 个文件）

```
src/main/java/com/xbk/lattice/compiler/graph/CompileGraphConditions.java
src/main/java/com/xbk/lattice/compiler/graph/CompileGraphDefinitionFactory.java
src/main/java/com/xbk/lattice/compiler/graph/CompileGraphState.java
src/main/java/com/xbk/lattice/compiler/graph/CompileGraphStateKeys.java
src/main/java/com/xbk/lattice/compiler/graph/CompileGraphStateMapper.java
src/main/java/com/xbk/lattice/compiler/graph/node/BuildLightweightArticlesNode.java
src/main/java/com/xbk/lattice/compiler/service/CompileExecutionRequest.java
src/main/java/com/xbk/lattice/compiler/service/CompileJobService.java
src/main/java/com/xbk/lattice/compiler/service/StateGraphCompileOrchestrator.java
```

### 报告文件（建议随本次提交归档，5 个文件）

```
docs/test/knowledge-base-e2e/code_light_indexing_mode_design_report.md                        (agentB 设计)
docs/test/knowledge-base-e2e/code_light_content_profile_implementation_report.md              (agentA 实现)
docs/test/knowledge-base-e2e/code_light_content_profile_pre_d_gate_review_report.md           (agentB pre-D 审查)
docs/test/knowledge-base-e2e/code_light_content_profile_normalize_fix_result_report.md        (agentA hyphen 修复)
docs/test/knowledge-base-e2e/code_light_content_profile_runtime_gate_report.md                (agentD gate)
```

---

## 8. 精确排除文件清单

### 必须排除（永远不提交）

```
special_cases_report.md    (redline 输出，AGENTS.md 禁止提交)
```

### 暂不提交（属其他独立主题线）

```
# PE3 采购合同 eval 线
docs/test/knowledge-base-e2e/fresh-eval-2026-06/
docs/test/knowledge-base-e2e/fresh-eval-2026-06_acceptance_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-06_asset_packaging_completion_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-06_build_report.md
docs/test/knowledge-base-e2e/fresh-eval-2026-06_design_report.md

# PE4 医疗设备 eval 线
docs/test/knowledge-base-e2e/fresh-eval-2026-07_design_report.md

# PE1 Q2 缩略词分析线
docs/test/knowledge-base-e2e/pe1_q2_acronym_general_solution_design_report.md
docs/test/knowledge-base-e2e/pe1_q2_acronym_query_retrieval_analysis_report.md
docs/test/knowledge-base-e2e/pe1_q2_writer_acronym_preservation_fix_result_report.md

# Dogfood 验证报告
docs/test/knowledge-base-e2e/internal_mirror_dogfood_java_project_runtime_report.md

# Java Codebase Eval 资料包
docs/test/knowledge-base-e2e/java-codebase-public-eval/
docs/test/knowledge-base-e2e/java_codebase_public_eval_build_report.md

# Post-S2 状态报告
docs/test/knowledge-base-e2e/post_s2_writer_title_preservation_current_head_full_eval_gate_report.md
docs/test/knowledge-base-e2e/post_s2_writer_title_preservation_status.md

# 报告归档计划
docs/test/knowledge-base-e2e/post_compiler_admin_fixes_report_archive_plan.md
```

### 建议单独提交（累计进度更新，非 CODE_LIGHT 专属）

```
docs/quality-progress-and-lessons.md    (含 S2/PE1 Q2/admin/eval gate 等多项累计更新)
```

---

## 9. 建议 Commit Message

```
feat(compiler): add CODE_LIGHT content profile for lightweight code indexing

Introduce CODE_LIGHT content profile as a compile-time strategy for
Java backend projects imported via INTERNAL_MIRROR (and optionally
UPLOAD/GIT). CODE_LIGHT skips writer/reviewer/fixer LLM calls for
source code files, directly using source text as article content.

- CompileExecutionRequest: add contentProfile field with normalize
  (null/blank/unknown → DOCUMENT; CODE-LIGHT/code-light → CODE_LIGHT)
- CompileGraphState: propagate contentProfile through graph state
- CompileGraphConditions: route CODE_LIGHT full compiles to new
  build_lightweight_articles node; incremental hasCreates also
  routes to build_lightweight_articles
- BuildLightweightArticlesNode (new): build minimal ArticleRecord
  from source chunks — title=file path, content=source text,
  reviewStatus=passed, metadata.contentProfile=CODE_LIGHT
- CompileGraphDefinitionFactory: register new node and edges
- StateGraphCompileOrchestrator/CompileJobService: wire contentProfile
  from KnowledgeSource through to graph

For a 2000-file Java project, CODE_LIGHT reduces compile time from
~30-60 min (full LLM) to <2 min (zero LLM calls). Writer/reviewer/
fixer confirmed 0 occurrences in runtime gate. Search 6/6, Q&A 4/5,
citation points to real source paths.

DOCUMENT profile (default) behavior unchanged. Redline BLOCKER=0.
mvn test 1018/0/0/0 BUILD SUCCESS.
```

---

## 10. 下一步给 `/code-commit` 的注意事项

1. **只提交 CODE_LIGHT 生产代码 + 5 个 CODE_LIGHT 报告**，不要混入 PE3/PE4/acronym/dogfood/post-S2 等其他线报告
2. `docs/quality-progress-and-lessons.md` 建议单独提交（累计进度更新，非 CODE_LIGHT 专属）
3. `special_cases_report.md` 必须排除
4. `java-codebase-public-eval/` 和 `fresh-eval-2026-06/` 资料包目录属于 eval 资产，建议后续单独提交
5. 新增文件 `BuildLightweightArticlesNode.java` 需要 `git add`

---

## 11. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt / config / schema / scripts
- [x] 未修改题集 / redline allowlist
- [x] 未提交 commit
- [x] 全部审查基于 git diff 源码分析 + 5 份报告 + runtime gate
- [x] 9 个生产文件均在 `compiler/` 包内，无越界
- [x] 提交范围明确：9 个生产文件 + 5 个报告文件
- [x] 排除范围明确：PE3/PE4/acronym/dogfood/post-S2 等 7 个独立主题线
