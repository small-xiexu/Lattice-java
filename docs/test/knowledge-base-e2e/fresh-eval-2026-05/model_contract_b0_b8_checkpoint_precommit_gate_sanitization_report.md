# B0-B8 Checkpoint 门禁报告脱敏清理报告

清理时间：2026-06-01
清理人：agentD
目标文件：`model_contract_b0_b8_checkpoint_precommit_gate_report.md`

---

## 1. 已脱敏字段说明

| 位置（行号区域） | 原始内容 | 脱敏后内容 | 说明 |
|---|---|---|---|
| 第 103 行（3.1 节原因 2） | 旧 key `sk-7ctk9Y...` → 新 key `sk-b37449...` | 旧 key `sk-****` → 新 key `sk-****` | API key 完整值，在 diff 引述中暴露 |
| 第 128-129 行（4.1 节） | 两个完整 `sk-...` token（旧+新） | 两个 `sk-****` | 敏感信息泄露风险分析章节 |
| 第 254 行（7 节问题 1） | 旧 key 前缀 `sk-7ctk...` → 新 key 前缀 `sk-b374...` | 旧 key 已脱敏 → 新 key 已脱敏 | 用户确认问题中引用 |

**脱敏策略**：所有完整 `sk-` token 替换为 `sk-****`；部分前缀引用替换为"已脱敏"标。

**验证**：`rg -n "sk-[A-Za-z0-9_-]{12,}" <报告路径>` 无输出，确认无残留完整 key。

---

## 2. Staging 建议修正

| 修正项 | 修正前 | 修正后 |
|---|---|---|
| 批次报告 stage | `git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/*.md`（通配符，可能误 stage 未检查报告） | 显式列出 17 个批次报告 + 本门禁报告 + 本脱敏报告，逐文件 add |
| 门禁报告纳入条件 | 未标注 | 文件头部添加"脱敏状态：已脱敏，脱敏后方可纳入 commit"声明 |

---

## 3. 当前仍需排除的文件

| 文件 | 原因 | 处理建议 |
|---|---|---|
| `docs/模型绑定配置参考.md` | 完整 API key 变更 + 计划禁令 + 非 DTO 治理范围 | `git checkout --` 还原，不在本次 commit 中 |
| `special_cases_report.md` | 机械重扫（行号偏移 + 时间戳更新）+ 计划禁令 + 非 DTO 治理范围 | `git checkout --` 还原，不在本次 commit 中 |

---

## 4. 可提交报告文件清单建议

经过脱敏和修正后，以下报告文件是安全的，可纳入 B0-B8 checkpoint commit：

| 序号 | 文件 | 状态 |
|---|---|---|
| 1 | `model_contract_b0_b8_checkpoint_precommit_gate_report.md` | 已脱敏，可提交 |
| 2 | `model_contract_b0_b8_checkpoint_precommit_gate_sanitization_report.md` | 本报告，无敏感内容，可提交 |
| 3 | `query_service_core_dto_contract_javadoc_lombok_fix_result_report.md` | 批次报告，可提交 |
| 4 | `query_api_citation_dto_contract_javadoc_lombok_fix_result_report.md` | 批次报告，可提交 |
| 5 | `query_api_structured_evidence_dto_contract_javadoc_lombok_fix_result_report.md` | 批次报告，可提交 |
| 6 | `query_api_search_pending_dto_contract_javadoc_lombok_fix_result_report.md` | 批次报告，可提交 |
| 7 | `compiler_admin_service_contract_javadoc_lombok_fix_result_report.md` | 批次报告，可提交 |
| 8 | `admin_source_credential_sync_dto_contract_analysis_report.md` | 批次报告，可提交 |
| 9 | `admin_source_credential_sync_dto_contract_javadoc_lombok_fix_result_report.md` | 批次报告，可提交 |
| 10 | `admin_vault_repo_lifecycle_dto_contract_analysis_report.md` | 批次报告，可提交 |
| 11 | `admin_vault_repo_lifecycle_dto_contract_javadoc_lombok_fix_result_report.md` | 批次报告，可提交 |
| 12 | `admin_vector_retrieval_config_dto_contract_analysis_report.md` | 批次报告，可提交 |
| 13 | `admin_vector_retrieval_config_dto_contract_javadoc_lombok_fix_result_report.md` | 批次报告，可提交 |
| 14 | `admin_compile_job_review_dto_contract_analysis_report.md` | 批次报告，可提交 |
| 15 | `admin_compile_job_review_dto_contract_javadoc_lombok_fix_result_report.md` | 批次报告，可提交 |
| 16 | `admin_article_display_hotspot_dto_contract_javadoc_lombok_fix_result_report.md` | 批次报告，可提交 |
| 17 | `admin_article_review_rollback_dto_contract_javadoc_lombok_fix_result_report.md` | 批次报告，可提交 |
| 18 | `admin_article_factcard_quality_dto_contract_analysis_report.md` | 批次报告，可提交 |
| 19 | `admin_factcard_quality_dto_contract_javadoc_lombok_fix_result_report.md` | 批次报告，可提交 |

---

## 5. 下一轮 /code-commit 的安全 Staging 注意事项

1. **禁止通配符 add**：不得使用 `git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/*.md`，必须逐文件显式 add。
2. **先排除敏感文件**：在 stage 任何文件之前，先还原 `docs/模型绑定配置参考.md` 和 `special_cases_report.md`。
3. **门禁报告预检**：在 commit 前执行 `rg -n "sk-[A-Za-z0-9_-]{12,}" docs/test/` 确认报告目录无残留 API key。
4. **禁止夹带**：不 stage 任何非 DTO 治理范围的文件（scripts、config、domain、entity、controller）。
5. **本门禁报告纳入条件**：确认已脱敏且验证通过后才 `git add`。
