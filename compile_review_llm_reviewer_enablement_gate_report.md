# compile review LLM reviewer enablement gate 只读核验报告

核验时间：2026-05-17
核验角色：agentD（验证/测试）
核验类型：只读核验（不改代码，不启用 reviewer）

## 1. Redline 扫描

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 退出码：0
- BLOCKER：0
- REVIEW：1853
- ALLOWLIST：239

## 2. Git Status

```
## codex/qa-polish...origin/codex/qa-polish [ahead 1]
?? compile_review_llm_reviewer_enablement_readiness_report.md
?? compile_review_llm_reviewer_loop_design_report.md
?? current_gate_snapshot_after_query_visibility.md
```

工作区干净，无修改文件。领先 origin 1 个 commit（fail-closed 修复 `df67223`）。3 个未跟踪文件均为报告/设计文档。

## 3. review-enabled 默认值

**仍为 false。**

| 来源 | 值 |
|---|---|
| `src/main/resources/config/lattice-llm.yml` 第 13 行 | `review-enabled: ${LATTICE_LLM_REVIEW_ENABLED:false}` |
| `LlmProperties.reviewEnabled`（Java 默认） | `false` |
| `compile_review_settings` 表 | 0 行（无运行时覆盖） |
| 当前环境变量 `LATTICE_LLM_REVIEW_ENABLED` | 未设置（代码逻辑走 `false` 默认） |

当前关闭时行为：`ArticleReviewerGateway` 在 `review-enabled=false` 时走早返分支（第 89 行），直接调用 `RuleBasedArticleReviewer`，route 为 `rule-based`。

## 4. Compile/Reviewer Binding 是否存在

**存在且 enabled。**

| 字段 | 值 |
|---|---|
| id | 2 |
| scene | `compile` |
| agent_role | `reviewer` |
| route_label | `compile.reviewer.gpt-5.5` |
| enabled | `true` |
| primary_model_profile_id | 1 |

## 5. Model Profile / Provider Connection 是否 Enabled

**均 enabled。**

**Model Profile（id=1）：**

| 字段 | 值 |
|---|---|
| model_code | `baseline-gpt-5-5-chat` |
| model_name | `gpt-5.5` |
| model_kind | `CHAT` |
| temperature | 0.20 |
| max_tokens | 4096 |
| timeout_seconds | 180 |
| enabled | `true` |
| extra_options_json | `{}`（空，无 response_format 覆盖） |

**Provider Connection（id=1）：**

| 字段 | 值 |
|---|---|
| connection_code | `xigua_openai_compatible` |
| provider_type | `openai_compatible` |
| enabled | `true` |

**结论：** `compile/reviewer` → model profile → provider connection 全链路 enabled。

## 6. JSON 输出稳定性依据

**compile-review prompt 当前不享受自动 JSON response_format 注入。**

`LlmInvocationExecutor.shouldForceJsonResponseFormat()` 只对 `query-answer-structured` 和 `query-rewrite-from-review-structured` 两个 purpose 自动注入 JSON schema。`compile-review` 不在该列表中。

reviwer 的 JSON 输出完全依赖 prompt 指令让模型返回 JSON（`ReviewResultParser` 期望的格式）。依据：

| 依据来源 | 内容 |
|---|---|
| `.claude/t1.md` | OpenAI-compatible `gpt-5.5` 已验证支持 JSON schema |
| 模型 kind | `CHAT`（支持 chat completion，非 completion-only） |
| 历史探针 | 无专项 compile-review JSON 输出探针报告 |

**风险评估：** `gpt-5.5` 作为 chat 模型对 JSON 输出的遵循度较高，但 compile-review prompt 场景下未经过专项验证。该风险可接受，因为：

1. `extra_options_json` 为空，小流量时可按需配置 `response_format` 作为增强手段
2. fail-closed 已就位：LLM 异常或不可解析输出不会静默 rule-based pass，会进入 `needs_human_review`
3. 小流量验证本身就是用来确认 JSON 稳定性的

## 7. 当前是否能区分 rule-based / LLM / fail-closed

**能。**

| 信号 | 观测位置 | 含义 |
|---|---|---|
| `reviewRoute=rule-based` | API `reviewSummary.reviewRoute` + 后台 step detail | review-enabled=false，走 RuleBasedArticleReviewer |
| `reviewRoute=compile.reviewer.gpt-5.5` | API + 后台 | review-enabled=true，LLM reviewer 正常调用 |
| `reviewRoute=compile.reviewer.gpt-5.5` + `status=TIMEOUT_FALLBACK` | reviewResult.status | LLM reviewer 调用异常，fail-closed |
| `reviewRoute=compile.reviewer.gpt-5.5` + `status=PARSE_FAILED` | reviewResult.status | LLM reviewer 返回不可解析输出，fail-closed |
| `reviewModeLabel=规则审查（不是 LLM 内容审查）` | API `reviewSummary.reviewModeLabel` | rule-based route 明确标识 |
| `acceptedCount/pendingReviewCount/needsHumanReviewCount` | API + 后台 step detail | 审查结果分区计数 |

后台可观测性（observability 轮次已验证）支持区分这三种状态。

## 8. 小流量验证是否具备条件

**YES，但有注意事项。**

### 8.1 已满足的条件

| 条件 | 状态 |
|---|---|
| fail-closed 已提交并验证（816/0/0） | 通过 |
| persist gate 已提交（只 persist `passed`） | 通过 |
| query visibility hard filter 已提交（5 mapper） | 通过 |
| reviewer binding enabled | 通过 |
| model profile enabled | 通过 |
| provider connection enabled | 通过 |
| review-enabled 默认 false（安全默认） | 通过 |
| route/outcome/fix 可观测 | 通过 |
| 测试库可用（`ai-rag-knowledge-test`） | 通过 |
| 可通过环境变量临时启用（`LATTICE_LLM_REVIEW_ENABLED=true`） | 通过 |

### 8.2 注意事项（非阻塞）

| 事项 | 说明 | 影响 |
|---|---|---|
| JSON 输出稳定性未经 compile-review prompt 验证 | `gpt-5.5` 已知支持 JSON，但 compile-review prompt 特定场景未探针 | 小流量验证核心目标之一，fail-closed 兜底 |
| extra_options_json 为空 | 未配置 `response_format`，纯依赖 prompt 指令 | 可在小流量前按需配置，非必须 |
| TLS / 连接稳定性未经运行时验证 | `xigua_openai_compatible` 连接未实时探测 | `.claude/t1.md` 记录过 TLS 风险，小流量首轮即可暴露 |
| 未使用临时库 / 隔离源 | 当前干净的 `ai-rag-knowledge-test` 可用 | 小流量时应使用 `ai-rag-knowledge-test` 而非主库 |

## 9. 阻塞项

**无硬阻塞项。** 所有必须满足的前置条件均已通过。

唯一"未经验证"项（JSON 输出稳定性）属于小流量验证的目的本身，不是前置阻塞条件。fail-closed 确保即使 JSON 不稳定，后果可控。

## 10. 下一轮唯一建议

**小流量验证 compile：**

```text
交给：agentD

任务类型：
LLM reviewer 小流量 compile 验证。只验证，不改代码。

前置确认：
1. 用户已明确授权本轮可运行隔离 compile。
2. 仅在运行时通过环境变量启用，不修改任何配置文件。

执行：
1. 确认 git status 干净，redline BLOCKER=0。
2. 设置环境变量 LATTICE_LLM_REVIEW_ENABLED=true。
3. 使用测试库 ai-rag-knowledge-test，确保 spring.datasource.url 指向该库。
4. 上传一个最小测试源文件（1-2 条明确事实，避免多源交叉）。
5. 运行 compile，等待完成。
6. 验证：
   a. review_articles step 的 model_route 不等于 rule-based。
   b. LLM approved 的 article 为 passed，persist 成功。
   c. 后台 API 显示正确的 reviewRoute、accepted/pending/needs 计数。
   d. 如果触发异常或非 JSON 输出，确认进入 needs_human_review 而非 passed。
7. 清除环境变量，恢复 review-enabled=false。
8. 输出 compile_review_llm_reviewer_small_flow_verification_report.md。

禁止：
- 不准修改源码、配置、测试、脚本。
- 不准在主库 ai-rag-knowledge 上运行 compile。
- 不准提交环境变量或配置变更。
- 不准清主库。
```

## 11. 本轮是否修改代码

**否。**

本轮仅新增本报告 `compile_review_llm_reviewer_enablement_gate_report.md`。redline 运行时更新了 `special_cases_report.md`（仅计数变化）。未修改任何源码、测试、配置、数据库记录。
