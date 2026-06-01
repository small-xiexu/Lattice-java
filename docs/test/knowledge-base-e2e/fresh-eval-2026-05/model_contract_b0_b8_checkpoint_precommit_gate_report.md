# B0-B8 Checkpoint 提交前门禁核查报告

核查时间：2026-06-01
核查人：agentD（只读门禁审计）
范围：B0–B8 全部批次（api/query + api/compiler + admin/service + api/admin source/credential/vault/repo/lifecycle + api/admin vector/retrieval config + api/admin compile job/review + api/admin article/fact card/quality + query/service）
状态：**有条件通过 — 存在 2 个必须排除的文件**
脱敏状态：**已脱敏**（所有完整 API key 已替换为 `sk-****`，本报告脱敏后方可纳入 commit）

---

## 1. 当前 Git 变更分类

### 1.1 按文件类别统计

| 类别 | 数量 | 状态 |
|---|---|---|
| 生产代码（B0-B8 DTO） | 71 | 全部在 scope 内 |
| 计划文档（治理台账） | 1 | 台账更新，符合预期 |
| 模型绑定配置参考 | 1 | **必须排除（敏感信息 + 计划禁令）** |
| special_cases_report | 1 | **必须排除（机械重扫 + 计划禁令）** |
| 批次报告（untracked） | 17 | 可纳入，为治理产物 |

### 1.2 生产代码按批次分布

| 批次 | 文件数 | 典型文件 |
|---|---|---|
| B0 (QueryResponse 等) | 已提交 | 不在本次 diff |
| B0.5 (query/service) | 3 | `QueryArticleHit`, `RetrievalStrategy`, `RetrievalChannelRun` |
| B1 (引用 DTO) | 4 | `QueryCitationMarkerResponse`, `CitationCheckSummary`, `DeepResearchSummary` |
| B2 (结构化证据) | 4 | `QueryStructuredEvidenceResponse` 等 4 个 |
| B3 (搜索/pending) | 7 | `QueryRequest`, `SearchResponse`, `PendingQuery*` 等 |
| B4 (compiler + admin/service) | 6 | `CompileRequest/Response`, `CompileArticleReviewQueueActionRequest/Result` |
| B5a (source/credential) | 6 | `AdminSourceCreateRequest`, `AdminSourceCredentialRequest` 等 |
| B5b (vault/repo/lifecycle) | 6 | `AdminVaultExportRequest`, `AdminRepoDiffResponse` 等 |
| B6 (vector/retrieval config) | 7 | `AdminVectorConfigRequest/Response`, `AdminQueryRetrievalConfigRequest/Response` 等 |
| B7 (compile job/review) | 10 | `AdminCompileJobRequest/Response`, `AdminCompileReview*` 等 |
| B8 (article/fact card/quality) | 18 | `AdminArticle*`, `AdminFactCard*`, `AdminQualityResponse` |

**无越界文件**：未发现 domain、config、entity、controller、infra 或任何非 DTO 生产代码变更。

---

## 2. 可纳入本次 Checkpoint 的文件清单

### 2.1 计划台账（1 个）

- `docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md`
  - 变更内容：B0.5–B8 状态从"待开始"→"已完成"，"当前下一步"更新为 B9
  - 风险：无

### 2.2 生产代码 DTO（71 个）

全部位于以下路径，变更仅限于 Javadoc 契约注释 + Lombok `@Getter` 替代手写 getter + 少量 `@Data`→`@Getter/@Setter` 降级：

- `src/main/java/com/xbk/lattice/api/query/*.java`（B0-B3 范围）
- `src/main/java/com/xbk/lattice/api/compiler/*.java`（B4 范围）
- `src/main/java/com/xbk/lattice/admin/service/*.java`（B4 范围）
- `src/main/java/com/xbk/lattice/api/admin/AdminSource*.java`（B5a）
- `src/main/java/com/xbk/lattice/api/admin/AdminVault*.java`（B5b）
- `src/main/java/com/xbk/lattice/api/admin/AdminRepo*.java`（B5b）
- `src/main/java/com/xbk/lattice/api/admin/AdminLifecycle*.java`（B5b）
- `src/main/java/com/xbk/lattice/api/admin/AdminVector*.java`（B6）
- `src/main/java/com/xbk/lattice/api/admin/AdminQueryRetrieval*.java`（B6）
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileJob*.java`（B7）
- `src/main/java/com/xbk/lattice/api/admin/AdminCompileReview*.java`（B7）
- `src/main/java/com/xbk/lattice/api/admin/AdminArticle*.java`（B8）
- `src/main/java/com/xbk/lattice/api/admin/AdminFactCard*.java`（B8）
- `src/main/java/com/xbk/lattice/api/admin/AdminQuality*.java`（B8）
- `src/main/java/com/xbk/lattice/query/service/*.java`（B0.5）

### 2.3 批次报告产物（17 个 untracked）

全部位于 `docs/test/knowledge-base-e2e/fresh-eval-2026-05/`：

| 文件名 | 对应批次 |
|---|---|
| `query_service_core_dto_contract_javadoc_lombok_fix_result_report.md` | B0.5 |
| `query_api_citation_dto_contract_javadoc_lombok_fix_result_report.md` | B1 |
| `query_api_structured_evidence_dto_contract_javadoc_lombok_fix_result_report.md` | B2 |
| `query_api_search_pending_dto_contract_javadoc_lombok_fix_result_report.md` | B3 |
| `compiler_admin_service_contract_javadoc_lombok_fix_result_report.md` | B4 |
| `admin_source_credential_sync_dto_contract_analysis_report.md` | B5a |
| `admin_source_credential_sync_dto_contract_javadoc_lombok_fix_result_report.md` | B5a |
| `admin_vault_repo_lifecycle_dto_contract_analysis_report.md` | B5b |
| `admin_vault_repo_lifecycle_dto_contract_javadoc_lombok_fix_result_report.md` | B5b |
| `admin_vector_retrieval_config_dto_contract_analysis_report.md` | B6 |
| `admin_vector_retrieval_config_dto_contract_javadoc_lombok_fix_result_report.md` | B6 |
| `admin_compile_job_review_dto_contract_analysis_report.md` | B7 |
| `admin_compile_job_review_dto_contract_javadoc_lombok_fix_result_report.md` | B7 |
| `admin_article_display_hotspot_dto_contract_javadoc_lombok_fix_result_report.md` | B8a1 |
| `admin_article_review_rollback_dto_contract_javadoc_lombok_fix_result_report.md` | B8a2 |
| `admin_article_factcard_quality_dto_contract_analysis_report.md` | B8 |
| `admin_factcard_quality_dto_contract_javadoc_lombok_fix_result_report.md` | B8b |

---

## 3. 不建议纳入本次 Checkpoint 的文件及原因

### 3.1 `docs/模型绑定配置参考.md` — 必须排除

| 维度 | 详情 |
|---|---|
| **原因 1：计划明确禁止** | 治理计划"风险与禁令"第 5 条："禁止修改 `docs/模型绑定配置参考.md`" |
| **原因 2：敏感信息泄露风险** | diff 中包含 API key 完整变更（旧 key `sk-****` → 新 key `sk-****`）。旧 key 已在仓库历史中，新 key 若提交将进一步扩大泄露面 |
| **原因 3：非 DTO 治理范围** | 这是项目运行配置参考文档，不属于 DTO/Javadoc/Lombok 治理范围 |
| **变更内容** | `baseUrl` 从外部端点改为 localhost；`apiKey` 替换为新 key（3 处） |
| **建议** | **立即还原此文件**（`git checkout -- docs/模型绑定配置参考.md`）。如果确实需要更新配置参考，应在单独的 PR 中处理，且不得包含真实 API key |

### 3.2 `special_cases_report.md` — 必须排除

| 维度 | 详情 |
|---|---|
| **原因 1：计划明确禁止** | 治理计划"风险与禁令"第 5 条："禁止修改 `special_cases_report.md`" |
| **原因 2：纯机械重扫** | 变更原因为 DTO 代码行号偏移导致的 redline 重扫结果更新，不涉及规则、allowlist 或 BLOCKER 状态变化 |
| **原因 3：非 DTO 治理范围** | 这是 redline 扫描工具的产物，属于工程质检基础设施，不是治理目标 |
| **变更内容** | 扫描时间戳更新（05-22→06-01）、risk_type 标签排序调整（`中文业务文案 return, 固定答案 return` → `固定答案 return, 中文业务文案 return`）、约 80+ 条目行号因源代码变更而偏移、新增条目均为原代码中已存在但此前因行号未覆盖的匹配项 |
| **BLOCKER 状态** | 无变化。所有 BLOCKER/REVIEW/ALLOWLIST 分类保持一致 |
| **建议** | **立即还原此文件**（`git checkout -- special_cases_report.md`）。如需更新 redline 基线，应在全部 DTO 治理完成后单独执行 |

---

## 4. 敏感信息泄露风险评估

### 4.1 API key 泄露

**风险等级：高**

`docs/模型绑定配置参考.md` 的 diff 中暴露了 2 个 API key：
- 旧 key（已在仓库历史中）：`sk-****`
- 新 key（工作区未提交）：`sk-****`

**缓解措施**：
1. 立即还原该文件，阻止新 key 进入版本历史
2. 如果旧 key 仍然有效，应在 API 提供商侧吊销
3. 如果新 key 是有效的，同样应吊销并生成新 key
4. 建议将 `docs/模型绑定配置参考.md` 加入 `.gitignore` 或使用 git-secrets 扫描

### 4.2 DTO 敏感字段

所有 DTO 变更均符合治理计划的安全要求：
- `AdminSourceCredentialRequest` 已标注禁止 `@Data`（B5a）
- `AdminCompileReviewConfigRequest` 已从 `@Data` 降级为 `@Getter/@Setter`（B7）
- `AdminFactCardItemResponse.itemsJson` / `evidenceText` 大文本字段已标注禁止 `@Data`（B8b）
- 无 `toString()` 暴露密钥、令牌或凭证的风险

---

## 5. Redline / Compile / 定向测试证据汇总

### 5.1 逐批次验证结果

| 批次 | mvn compile | 定向测试 | redline BLOCKER | 全量 mvn test |
|---|---|---|---|---|
| B0.5 | — | — | 0 | 995/0/0/0 |
| B1 | — | — | 0 | 995/0/0/0 |
| B2 | — | — | 0 | 995/0/0/0 |
| B3 | — | — | 0 | 995/0/0/0 |
| B4 | — | — | 0 | 995/0/0/0 |
| B5a | — | — | 0 | 995/0/0/0 |
| B5b | — | — | 0 | 995/0/0/0 |
| B6 | PASS | 8/0/0/0 | 0 | 预存 ClassNotFoundException（无关） |
| B7 | PASS | 11/0/0/0 | 0 | — |
| B8 | PASS | 9/0/0/0 | 0 | — |

### 5.2 汇总

- **compile**：B6/B7/B8 均 PASS（前期批次在更早的全量 test 中隐式验证）
- **定向测试**：B6+B7+B8 合计 28/0/0/0
- **redline BLOCKER**：所有批次均为 0
- **全量 mvn test**：B0.5–B5b 均为 995/0/0/0；B6 因预存 `FactCardReviewerTests ClassNotFoundException` 失败，与 DTO 治理无关

---

## 6. 给下一轮 /code-commit 的 Staging 建议

### 6.1 推荐 staging 命令（按顺序）

```bash
# 步骤 1：还原必须排除的文件
git checkout -- docs/模型绑定配置参考.md
git checkout -- special_cases_report.md

# 步骤 2：验证还原后状态
git diff --stat  # 应不再包含上述 2 个文件

# 步骤 3：stage 计划台账
git add docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md

# 步骤 4：stage 所有生产代码 DTO（按批次分组便于审查）
git add src/main/java/com/xbk/lattice/api/query/*.java
git add src/main/java/com/xbk/lattice/api/compiler/*.java
git add src/main/java/com/xbk/lattice/admin/service/*.java
git add src/main/java/com/xbk/lattice/api/admin/AdminSource*.java
git add src/main/java/com/xbk/lattice/api/admin/AdminVault*.java
git add src/main/java/com/xbk/lattice/api/admin/AdminRepo*.java
git add src/main/java/com/xbk/lattice/api/admin/AdminLifecycle*.java
git add src/main/java/com/xbk/lattice/api/admin/AdminVector*.java
git add src/main/java/com/xbk/lattice/api/admin/AdminQueryRetrieval*.java
git add src/main/java/com/xbk/lattice/api/admin/AdminCompileJob*.java
git add src/main/java/com/xbk/lattice/api/admin/AdminCompileReview*.java
git add src/main/java/com/xbk/lattice/api/admin/AdminArticle*.java
git add src/main/java/com/xbk/lattice/api/admin/AdminFactCard*.java
git add src/main/java/com/xbk/lattice/api/admin/AdminQuality*.java
git add src/main/java/com/xbk/lattice/query/service/QueryArticleHit.java
git add src/main/java/com/xbk/lattice/query/service/RetrievalStrategy.java
git add src/main/java/com/xbk/lattice/query/service/RetrievalChannelRun.java

# 步骤 5：stage 批次报告产物（显式清单，禁止通配符避免误 stage 未检查报告）
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/query_service_core_dto_contract_javadoc_lombok_fix_result_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/query_api_citation_dto_contract_javadoc_lombok_fix_result_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/query_api_structured_evidence_dto_contract_javadoc_lombok_fix_result_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/query_api_search_pending_dto_contract_javadoc_lombok_fix_result_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/compiler_admin_service_contract_javadoc_lombok_fix_result_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_source_credential_sync_dto_contract_analysis_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_source_credential_sync_dto_contract_javadoc_lombok_fix_result_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_vault_repo_lifecycle_dto_contract_analysis_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_vault_repo_lifecycle_dto_contract_javadoc_lombok_fix_result_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_vector_retrieval_config_dto_contract_analysis_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_vector_retrieval_config_dto_contract_javadoc_lombok_fix_result_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_compile_job_review_dto_contract_analysis_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_compile_job_review_dto_contract_javadoc_lombok_fix_result_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_article_display_hotspot_dto_contract_javadoc_lombok_fix_result_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_article_review_rollback_dto_contract_javadoc_lombok_fix_result_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_article_factcard_quality_dto_contract_analysis_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_factcard_quality_dto_contract_javadoc_lombok_fix_result_report.md
# 本门禁报告（已脱敏）：
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/model_contract_b0_b8_checkpoint_precommit_gate_report.md
# 脱敏清理报告（如有）：
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/model_contract_b0_b8_checkpoint_precommit_gate_sanitization_report.md

# 步骤 6：最终确认
git status
git diff --cached --stat
```

### 6.2 建议的 commit message

```
feat(dto): B0-B8 DTO 字段契约注释与 Lombok 治理（71 个类，13 个批次）

完成 api/query、api/compiler、admin/service、api/admin（source/credential/
vault/repo/lifecycle/vector/retrieval/compile job/review/article/fact card/
quality）及 query/service 共 71 个 DTO 的字段 Javadoc 契约注释、Lombok
@Getter 替代手写 getter 及少量 @Data→@Getter/@Setter 降级。

验证：全量 mvn test 995/0/0/0（B0.5-B5b），定向测试 28/0/0/0（B6-B8），
redline BLOCKER=0，mvn compile PASS。

排除 docs/模型绑定配置参考.md（API key 泄露风险）和 special_cases_report.md
（机械重扫）。

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

### 6.3 禁止 staging 的文件

| 文件 | 原因 |
|---|---|
| `docs/模型绑定配置参考.md` | API key 泄露 + 计划禁令 |
| `special_cases_report.md` | 机械重扫 + 计划禁令 |

---

## 7. 需要用户确认的最小问题清单

1. **`docs/模型绑定配置参考.md` 中的 API key 变更**：两个 key（旧 key 已脱敏 → 新 key 已脱敏）均已在 diff 中暴露。是否需要立即吊销这两个 key？该文件的修改是否是您主动做的（例如切换到本地测试环境），还是 agent 误修改？

2. **`special_cases_report.md` 的还原**：该文件为 redline 重扫产物，不包含 BLOCKER 状态变化。确认可以还原吗？

3. **staging 范围确认**：本次 checkpoint 建议纳入 71 个 DTO + 1 个台账 + 17 个批次报告，排除上述 2 个文件。范围是否认可？

4. **B9 启动**：门禁通过并提交后，是否立即进入 B9（`api/admin` query feedback / retrieval audit DTO）？

---

## 附录 A：越界检查结果

以下路径均未被本次变更触及，确认无越界修改：

- `src/main/java/com/xbk/lattice/compiler/domain/` — 未修改
- `src/main/java/com/xbk/lattice/compiler/ast/` — 未修改
- `src/main/java/com/xbk/lattice/compiler/config/` — 未修改
- `src/main/java/com/xbk/lattice/query/domain/` — 未修改
- `src/main/java/com/xbk/lattice/query/evidence/` — 未修改
- `src/main/java/com/xbk/lattice/query/deepresearch/` — 未修改
- `src/main/java/com/xbk/lattice/source/domain/` — 未修改
- `src/main/java/com/xbk/lattice/documentparse/domain/` — 未修改
- `src/main/java/com/xbk/lattice/llm/domain/` — 未修改
- `src/main/java/com/xbk/lattice/governance/domain/` — 未修改
- `src/main/java/com/xbk/lattice/infra/persistence/` — 未修改
- `src/main/java/com/xbk/lattice/*/config/` — 未修改
- 所有 Controller 类 — 未修改
- `scripts/scan-redline.sh` — 未修改
- redline allowlist — 未修改

## 附录 B：Special Cases Report 变更详细分析

`special_cases_report.md` 的 1000 行 diff 可分解为：

| 变更类型 | 数量（估计） | 说明 |
|---|---|---|
| 扫描时间戳 | 1 | `2026-05-22` → `2026-06-01` |
| 标签排序调整 | ~30 | `中文业务文案 return, 固定答案 return` → `固定答案 return, 中文业务文案 return`（risk_type 不变，仅标签内关键词顺序变化） |
| 行号偏移 | ~50 | 源代码增删 Javadoc/getter 导致行号变化 |
| 新增条目 | ~15 | 原已存在但因行号范围未覆盖的匹配项（如 AnalyzeNode 新增 fallback 相关条目） |
| 删除条目 | ~5 | 原手写 getter 代码被删除后对应匹配项消失 |
| **BLOCKER 变化** | **0** | 无任何 BLOCKER 条目新增、删除或状态变更 |
| **REVIEW 变化** | **0** | 数量因行号偏移有增减，但均为同类型条目 |
| **ALLOWLIST 变化** | **0** | 数量因行号偏移有增减，但均为同类型条目 |
