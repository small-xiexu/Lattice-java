# 剩余 docs/report 收口审计与提交建议

**审计时间**：2026-05-27
**审计 Agent**：agentC（文档/报告治理 Agent）
**审计模式**：只读盘点 + 敏感信息扫描 + 逐桶审计
**约束声明**：未 stage、未 commit、未 push，未修改任何生产代码、测试代码、配置、脚本

---

## 0. 只读声明

本轮审计严格只读盘点剩余 docs/report 工作区，未触碰 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`、redline 脚本或 allowlist。未读取 `docs/模型绑定配置参考.md` 内容。未执行任何 stage/commit/push 操作。

---

## 1. 当前剩余未提交项总览

```
来源: git status --short + git ls-files --others --exclude-standard

已修改（unstaged）:
  M docs/模型绑定配置参考.md           ← 私有配置，永远排除
  M docs/项目全流程真实验收手册.md      ← 项目手册
  M special_cases_report.md            ← redline 输出，永远排除

未跟踪:
  ?? docs/plans/2026-05-24-知识条目标题生成优化实施计划.md
  ?? docs/plans/2026-05-25-知识库验收阻塞修复实施方案.md
  ?? docs/test/knowledge-base-e2e/q6_exact_path_sibling_root_cause_analysis_report.md
  ?? docs/test/knowledge-base-e2e/q6_exact_path_terminal_field_verification_report.md
  ?? docs/test/llm/execution_llm_snapshot_decrypt_failure_tests_fix_result_report.md
  ?? docs/test/llm/execution_llm_snapshot_pre_commit_verification_report.md
  ?? docs/test/llm/execution_llm_snapshot_service_change_analysis_report.md

需评估更新的已跟踪文件:
  docs/quality-progress-and-lessons.md  ← 质量台账，本轮已更新
```

---

## 2. 明确禁止提交清单

| 文件 | 原因 |
|---|---|
| `docs/模型绑定配置参考.md` | 私有配置，包含真实 apiKey / provider 连接信息 |
| `special_cases_report.md` | redline 脚本输出产物，非源文档 |

上述两项在任何情况下都不提交、不 stage、不建议提交。

---

## 3. 逐桶审计

### 3.1 项目手册：`docs/项目全流程真实验收手册.md`

| 审计维度 | 结论 |
|---|---|
| 是否建议提交 | **建议提交** |
| 是否单独提交 | **是**，与代码变更无关，属于独立文档提交 |
| 是否被后续报告覆盖 | 否。该手册是项目级端到端验收的权威记录，后续报告均为单项修复/验证报告，不替代项目手册 |
| 是否与已提交 commit 冲突 | 否。手册描述的是 2026-04-18/04-22 的验收基线，早于当前所有代码提交 |
| 是否包含敏感信息 | **否**。经扫描：仅含 localhost 测试地址（`127.0.0.1:18082`）、README 演示连接（`127.0.0.1:19999`）、占位环境变量（`LATTICE_LLM_SECRET_ENCRYPTION_KEY='<自行设置的 32+ 字节密钥>'`）。无真实 apiKey/sk-/token/password |
| 是否包含过期结论 | 否。验收结论标记了明确日期和适用范围，且明确区分了共享实例限制与隔离实例通过项 |
| 建议 commit message | `docs: 项目端到端真实验收手册` |

**敏感信息扫描详情**：
- `partner-password-reset.pdf`：样本目录下的文件名，非真实密码
- `max tokens`：UI 字段描述文本，非 token 值
- 无 `sk-`、真实 apiKey、真实密码命中

---

### 3.2 Plans

#### 3.2.1 `docs/plans/2026-05-24-知识条目标题生成优化实施计划.md`

| 审计维度 | 结论 |
|---|---|
| 是否建议提交 | **建议提交** |
| 是否单独提交 | **建议与 2026-05-25 计划一起提交**，作为 plans 桶 |
| 是否被后续报告覆盖 | 否。该计划是标题生成功能的设计文档，所有事项已标记为已完成。对应已提交代码：`02f220e`（compiler）、`e551d4c`（documentparse）、`38ca188`（admin API）、`9e16999`（admin UI） |
| 是否与已提交 commit 冲突 | 否。计划中所有 P0-P4 事项均标记为已完成，与已提交代码一致 |
| 是否包含敏感信息 | **否** |
| 是否包含过期结论 | 否。计划已完成，作为设计文档具备归档价值 |
| 建议 commit message | `docs: 知识条目标题生成优化实施计划与知识库验收阻塞修复方案` |

#### 3.2.2 `docs/plans/2026-05-25-知识库验收阻塞修复实施方案.md`

| 审计维度 | 结论 |
|---|---|
| 是否建议提交 | **建议提交** |
| 是否单独提交 | **与 2026-05-24 计划一起提交** |
| 是否被后续报告覆盖 | 否。该方案是 Analyze 主链修复、fallback 标题来源、小资料轻量主题提取的设计文档。大部分事项已完成，仅"资料源诊断与处理历史统一 UI 语言"标记为进行中 |
| 是否与已提交 commit 冲突 | 否 |
| 是否包含敏感信息 | **注意**：第 508 行包含 `http://127.0.0.1:8888/v1`，这是本地 Chat 网关测试地址，非生产密钥。属于可接受的本地开发环境引用 |
| 是否包含过期结论 | 部分进行中事项未完成，但方案本身是有效设计文档。Q6 标记为"阻塞"的结论已被后续 `4d5e8bc` 修复覆盖 |
| 建议 commit message | 同上，合并为一个 plans 提交 |

**敏感信息说明**：
- `http://127.0.0.1:8888/v1`：本地 OpenAI 兼容 Chat 网关，仅在本机可访问，非公网服务地址。属于本地开发环境信息，不构成安全风险。

---

### 3.3 Q6 余波报告

#### 3.3.1 `docs/test/knowledge-base-e2e/q6_exact_path_sibling_root_cause_analysis_report.md`

| 审计维度 | 结论 |
|---|---|
| 是否建议提交 | **建议提交** |
| 是否单独提交 | **建议与 LLM 报告分桶提交**（Q6 桶 vs LLM 桶） |
| 是否被后续报告覆盖 | 部分覆盖。该报告分析了 sibling 字段误选的根因（终端字段语义未建模），直接导向了 `4d5e8bc` 的配置化 terminal field alias 修复。最终修复报告 `q6_exact_path_terminal_field_fix_result_report.md` 已随代码提交。本报告作为根因分析文档，补充了"为什么这样修"的诊断链路 |
| 是否与已提交 commit 冲突 | 否。报告结论与 `4d5e8bc` 修复方向一致 |
| 是否包含敏感信息 | **否**。扫描仅命中 `token` 等技术术语，无真实密钥 |
| 是否包含过期结论 | 否。根因分析结论（终端字段语义未建模）仍有效，且已被后续修复验证 |
| 建议 commit message | `docs: Q6 exact path sibling 根因分析与端到端验证审计` |

#### 3.3.2 `docs/test/knowledge-base-e2e/q6_exact_path_terminal_field_verification_report.md`

| 审计维度 | 结论 |
|---|---|
| 是否建议提交 | **不建议提交** |
| 是否被后续报告覆盖 | **是，已被完全覆盖**。该报告是 agentD 在 terminal field alias 修复前的 FAIL 验证（Q6 仍返回 `periodSeconds=10`）。最终成功验证报告 `q6_exact_path_terminal_field_fix_result_report.md` 已随 `4d5e8bc` 提交，该报告已无独立归档价值 |
| 是否包含敏感信息 | 否（但无关紧要，因为不建议提交） |
| 理由 | 这是一份中间失败验证报告，其结论（FAIL → 需继续修复）已经被后续修复闭环。保留它会混淆审计链路——未来读者可能误以为 Q6 修复未通过验证。根因分析报告（3.3.1）已充分记录诊断过程 |

---

### 3.4 LLM 报告（be4d216 审计链路）

#### 3.4.1 `docs/test/llm/execution_llm_snapshot_service_change_analysis_report.md`（agentB 只读分析）

| 审计维度 | 结论 |
|---|---|
| 是否建议提交 | **建议提交** |
| 是否被后续报告覆盖 | 否。该报告是 agentB 对 `ExecutionLlmSnapshotService.java` 唯一改动的只读分析，覆盖调用链、行为变化、安全风险判断，是 `be4d216` 的独立审计证据 |
| 是否包含敏感信息 | **否**。`apiKey` 均为变量名/分析描述，无真实密钥 |
| 建议 commit message | `docs: LLM snapshot apiKey 解密优雅降级审计链路报告` |

#### 3.4.2 `docs/test/llm/execution_llm_snapshot_pre_commit_verification_report.md`（agentD 提交前验证）

| 审计维度 | 结论 |
|---|---|
| 是否建议提交 | **建议提交** |
| 是否被后续报告覆盖 | 否。该报告是 agentD 对 `be4d216` 提交前验证的完整记录，覆盖 redline/mvn test/敏感信息扫描/fail-open/fail-closed 行为审核 |
| 是否包含敏感信息 | **否**。使用脱敏值 `sk-du****3456`、mock URL `http://localhost:8888`、测试桩值 `dummy-ciphertext` |
| 建议 commit message | 同上，合并为一个 LLM 报告提交 |

#### 3.4.3 `docs/test/llm/execution_llm_snapshot_decrypt_failure_tests_fix_result_report.md`（agentA 测试修复）

| 审计维度 | 结论 |
|---|---|
| 是否建议提交 | **BLOCKED — 不建议提交当前版本** |
| 阻塞原因 | **包含真实 API 密钥**：第 77 行将真实 `sk-` 明文写入了审计表（已脱敏为 `sk-7ctk...sLN`；原报告仍需脱敏后才能提交） |
| 修复建议 | 将该行改为脱敏形式，例如 `sk-7ctk...sLN`（仅保留前缀和后缀各 3-4 字符），或直接写"来自私有配置文件的已有修改，非本轮改动"而不写出密钥明文 |
| 修复后是否可提交 | 是。脱敏后可与其他两份 LLM 报告一起提交 |
| 注意 | 该报告第 73 行已正确使用脱敏值 `sk-du****3456`，第 77 行是审计自身时意外引入的真实密钥 |

---

## 4. 敏感信息扫描汇总

```
扫描命令:
rg -n "apiKey|sk-[A-Za-z0-9]|token|secret|password|127\\.0\\.0\\.1:8888|localhost:8888" \
   docs/test docs/plans "docs/项目全流程真实验收手册.md" docs/quality-progress-and-lessons.md

结果分类:

✅ 可接受（变量名/脱敏值/测试地址/通用术语）:
  - apiKey 作为 Java 变量名出现（所有 LLM 报告）— 可接受
  - sk-du****3456（LLM 验证报告）— 脱敏展示值，可接受
  - http://localhost:8888（LLM 验证报告）— mock URL，可接受
  - http://127.0.0.1:8888/v1（知识库验收阻塞修复方案）— 本地测试网关，可接受
  - dummy-ciphertext（LLM 测试报告）— 测试桩值，可接受
  - token 作为技术术语出现（Q6 报告、quality-progress）— 可接受
  - promptLength / max tokens 作为配置描述 — 可接受

🔴 BLOCKER（真实密钥）:
  - sk-7ctk...sLN（已脱敏；原位置为真实 sk- 明文，原报告仍需脱敏后才能提交）
    位置: docs/test/llm/execution_llm_snapshot_decrypt_failure_tests_fix_result_report.md:77
    判定: 真实 API 密钥明文，必须脱敏后才能提交

未扫描文件:
  - docs/模型绑定配置参考.md（按指令禁止读取）
```

---

## 5. 是否更新 quality-progress-and-lessons.md

**已更新。** 更新内容：

1. 在"已提交 checkpoint"区域新增标题生成与 Q6/LLM 共 7 个 scoped commit 的条目
2. 下一步计划第 30 项从"（下一步）"改为"（已完成）"
3. 更新时间戳更新为 2026-05-27

详见本报告第 7 节。

---

## 6. 建议提交顺序与精确文件清单

### 第 1 提交：项目手册（独立文档）

```
docs/项目全流程真实验收手册.md
```

Commit message:
```
docs: 项目端到端真实验收手册

基于 2026-04-18/04-22 真实验收的端到端手册，覆盖环境启动、
编译、问答、CLI、MCP、Admin、快照回滚、Vault 导出全链路。
```

### 第 2 提交：Plans 桶（设计文档归档）

```
docs/plans/2026-05-24-知识条目标题生成优化实施计划.md
docs/plans/2026-05-25-知识库验收阻塞修复实施方案.md
```

Commit message:
```
docs: 知识条目标题生成优化实施计划与知识库验收阻塞修复方案

归档标题生成（规则优先+LLM兜底）与知识库验收阻塞修复
（Analyze 主链恢复/fallback 标题来源/小资料轻量提取）的
实施方案。均已在对应代码提交中落地。
```

### 第 3 提交：Q6 根因分析报告（诊断链路归档）

```
docs/test/knowledge-base-e2e/q6_exact_path_sibling_root_cause_analysis_report.md
```

Commit message:
```
docs: Q6 exact path sibling 根因分析报告

归档 Q6 终端字段语义未建模导致 sibling 误选的根因分析，
作为 4d5e8bc (配置化 exact path terminal field alias) 的
诊断链路补充。
```

### 第 4 提交：LLM 审计链路报告（be4d216 补充证据）

```
docs/test/llm/execution_llm_snapshot_service_change_analysis_report.md
docs/test/llm/execution_llm_snapshot_pre_commit_verification_report.md
```

注意：`execution_llm_snapshot_decrypt_failure_tests_fix_result_report.md` 因包含真实 API 密钥，**不纳入此提交**，需先脱敏处理。

Commit message:
```
docs: LLM snapshot apiKey 解密优雅降级审计链路报告

agentB 只读分析 + agentD 提交前验证报告，作为 be4d216
(fix(llm): apiKey 解密失败优雅降级) 的独立审计证据。
```

### 第 5 提交：质量台账更新

```
docs/quality-progress-and-lessons.md
```

Commit message:
```
docs: 更新质量台账——收口 7 个 scoped commit 与剩余文档审计结论
```

### 不提交（保留本地或丢弃）

```
docs/test/knowledge-base-e2e/q6_exact_path_terminal_field_verification_report.md  ← 中间 FAIL 报告，已被最终报告覆盖
docs/test/llm/execution_llm_snapshot_decrypt_failure_tests_fix_result_report.md   ← BLOCKED: 含真实 API 密钥，需脱敏后再议
docs/模型绑定配置参考.md                                                           ← 私有配置，永远排除
special_cases_report.md                                                            ← redline 输出，永远排除
```

---

## 7. quality-progress-and-lessons.md 更新说明

本轮已对 `docs/quality-progress-and-lessons.md` 做以下更新：

### 7.1 新增"已提交 scoped commit 清单"区域

在"当前阶段"区域末尾新增 7 个 scoped commit 的明确记录：

| Commit | 描述 | 所属桶 |
|---|---|---|
| `02f220e` | feat(compiler): 增加标题画像生成与文档标题回流 | title-generation |
| `e551d4c` | feat(documentparse): 回流文档标题元数据 | title-generation |
| `38ca188` | feat(admin): 展示文章标题画像 | title-generation |
| `9e16999` | feat(admin): 优化治理工作台诊断 UI 与处理历史 | title-generation |
| `e286c79` | fix(query): 保留结构化事实卡片作为 fallback 证据 | Q6 fallback |
| `4d5e8bc` | fix(query): 配置化 exact path terminal field alias | Q6 terminal field |
| `be4d216` | fix(llm): apiKey 解密失败优雅降级，deep_research 保持 fail-closed | LLM infrastructure |

### 7.2 更新下一步计划

- 第 30 项：从"（下一步）Q6 terminal field alias scoped commit"改为"（已完成）Q6 terminal field alias scoped commit 已提交（4d5e8bc）"

### 7.3 更新时间戳

- 更新为 2026-05-27（agentC 剩余文档收口审计后更新）

---

## 8. 最终判定

| 文件 | 判定 |
|---|---|
| `docs/项目全流程真实验收手册.md` | ✅ 建议提交（第 1 提交） |
| `docs/plans/2026-05-24-知识条目标题生成优化实施计划.md` | ✅ 建议提交（第 2 提交） |
| `docs/plans/2026-05-25-知识库验收阻塞修复实施方案.md` | ✅ 建议提交（第 2 提交） |
| `docs/test/knowledge-base-e2e/q6_exact_path_sibling_root_cause_analysis_report.md` | ✅ 建议提交（第 3 提交） |
| `docs/test/knowledge-base-e2e/q6_exact_path_terminal_field_verification_report.md` | ❌ 不建议提交（中间 FAIL，已被覆盖） |
| `docs/test/llm/execution_llm_snapshot_service_change_analysis_report.md` | ✅ 建议提交（第 4 提交） |
| `docs/test/llm/execution_llm_snapshot_pre_commit_verification_report.md` | ✅ 建议提交（第 4 提交） |
| `docs/test/llm/execution_llm_snapshot_decrypt_failure_tests_fix_result_report.md` | 🔴 BLOCKED（含真实 sk-，需脱敏） |
| `docs/quality-progress-and-lessons.md` | ✅ 已更新（第 5 提交） |
| `docs/模型绑定配置参考.md` | 🚫 永远排除 |
| `special_cases_report.md` | 🚫 永远排除 |

---

*本报告由 agentC 生成。未 stage、未 commit、未 push。*
