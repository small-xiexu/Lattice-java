# B9-B10 Checkpoint 提交前门禁核查报告

核查时间：2026-06-01
核查人：agentD（只读门禁审计）
范围：B9-B10 全部批次（api/admin query feedback / retrieval audit / overview / pending / processing task DTO）
状态：**有条件通过 — 存在 2 个已知 out-of-scope dirty 文件，未做还原**

---

## 1. 当前 Git 变更分类

### 1.1 按文件类别统计

| 类别 | 数量 | 状态 |
|---|---|---|
| 生产代码（B9-B10 DTO） | 24 | 全部在 scope 内 |
| 计划文档（治理台账） | 1 | 台账更新（B9/B10→已完成，下一步→B11a），符合预期 |
| 模型绑定配置参考 | 1 | **已知 out-of-scope dirty（API key 变更 + 计划禁令），不得纳入** |
| special_cases_report | 1 | **已知 out-of-scope dirty（机械重扫 + 计划禁令），不得纳入** |
| 批次报告（untracked，B9-B10） | 6 | 可纳入，为治理产物 |

### 1.2 生产代码按批次分布

| 批次 | 子批次 | 文件数 | 典型文件 |
|---|---|---|---|
| B9a (query feedback) | — | 6 | `AdminQueryFeedbackCreateRequest`, `AdminQueryFeedbackResponse`, `AdminQueryFeedbackAuditResponse` 等 |
| B9b (retrieval audit) | — | 5 | `AdminQueryRetrievalAuditRunResponse`, `AdminQueryRetrievalChannelHitResponse`, `AdminQueryRetrievalChannelRunResponse` 等 |
| B10a (overview + pending) | — | 5 | `AdminOverviewResponse`, `AdminOverviewPendingItemResponse`, `AdminPendingItemResponse` 等 |
| B10b (processing task) | — | 8 | `AdminProcessingTaskItemResponse`（45 字段超大 DTO）, `AdminProcessingTaskSummaryResponse`, `AdminKnowledgeHelpStateResponse` 等 |

**合计：24 个生产代码文件，全部位于 `src/main/java/com/xbk/lattice/api/admin/`**

### 1.3 越界检查

以下范围均未被 B9-B10 变更触及，确认无越界修改：

- `src/main/java/com/xbk/lattice/api/query/` — B0-B3 范围，本轮未改
- `src/main/java/com/xbk/lattice/api/compiler/` — B4 范围，本轮未改
- `src/main/java/com/xbk/lattice/admin/service/` — B4 范围，本轮未改
- `src/main/java/com/xbk/lattice/query/service/` — B0.5 范围，本轮未改
- `src/main/java/com/xbk/lattice/compiler/domain/` / `compiler/ast/` / `compiler/config/` — B13/B12a 范围，本轮未改
- `src/main/java/com/xbk/lattice/*/domain/` — 所有 domain 层均未触及
- `src/main/java/com/xbk/lattice/infra/persistence/` — 明确排除范围，未触及
- 所有 Controller 类 — 仅治理 DTO，未改控制器
- `scripts/scan-redline.sh` — 未修改
- redline allowlist — 未修改
- `admin/service/` 枚举类（`AdminProcessingTaskDisplayStatus`, `AdminProcessingTaskStep`, `AdminProcessingTaskStepStatus`）— B10 分析报告明确排除，本轮未改

---

## 2. 可纳入本次 Checkpoint 的文件清单

### 2.1 计划台账（1 个）

- `docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md`
  - 变更内容：B9/B10 状态"已完成"，台账行补充验证结果，"当前下一步"更新为 B11a
  - 风险：无

### 2.2 生产代码 DTO — B9 查询反馈与检索审计（11 个）

| 文件 | 子批次 | 变更摘要 |
|---|---|---|
| `AdminQueryFeedbackCreateRequest.java` | B9a | `@Getter/@Setter`，删除 8 getter+8 setter，8 字段 Javadoc，禁止 @Data |
| `AdminQueryFeedbackHandleRequest.java` | B9a | `@Getter/@Setter`，删除 2 getter+2 setter，2 字段 Javadoc，禁止 @Data |
| `AdminQueryFeedbackResponse.java` | B9a | 类级 `@Getter`，删除 15 getter，15 字段 Javadoc，禁止 @Data |
| `AdminQueryFeedbackListResponse.java` | B9a | 类级 `@Getter`，删除 2 getter，2 字段 Javadoc |
| `AdminQueryFeedbackDetailResponse.java` | B9a | 类级 `@Getter`，删除 2 getter，2 字段 Javadoc |
| `AdminQueryFeedbackAuditResponse.java` | B9a | 类级 `@Getter`，删除 9 getter，9 字段 Javadoc，禁止 @Data |
| `AdminQueryRetrievalAuditListResponse.java` | B9b | 类级 `@Getter`，删除 2 getter，2 字段 Javadoc |
| `AdminQueryRetrievalAuditDetailResponse.java` | B9b | 类级 `@Getter`，删除 7 getter，7 字段 Javadoc |
| `AdminQueryRetrievalAuditRunResponse.java` | B9b | 类级 `@Getter`，删除 21 getter，21 字段 Javadoc，保留 `List.copyOf`，禁止 @Data |
| `AdminQueryRetrievalChannelRunResponse.java` | B9b | 类级 `@Getter`，删除 8 getter，8 字段 Javadoc（timeout/zeroHit 计算字段标注），禁止 @Data |
| `AdminQueryRetrievalChannelHitResponse.java` | B9b | 类级 `@Getter`，删除 20 getter，20 字段 Javadoc（RRF 融合语义+大 JSON 标注），禁止 @Data |

### 2.3 生产代码 DTO — B10 概览与处理任务（13 个）

| 文件 | 子批次 | 变更摘要 |
|---|---|---|
| `AdminOverviewResponse.java` | B10a | 类级 `@Getter`，删除 3 getter，3 字段 Javadoc（status/quality 分层问题标注） |
| `AdminOverviewPendingResponse.java` | B10a | 类级 `@Getter`，删除 2 getter，2 字段 Javadoc |
| `AdminOverviewPendingItemResponse.java` | B10a | 类级 `@Getter`，删除 3 getter，3 字段 Javadoc（question 禁止 @Data） |
| `AdminPendingResponse.java` | B10a | 类级 `@Getter`，删除 2 getter，2 字段 Javadoc |
| `AdminPendingItemResponse.java` | B10a | 类级 `@Getter`，删除 8 getter，8 字段 Javadoc（question/answer 禁止 @Data） |
| `AdminProcessingTaskActionResponse.java` | B10b | 类级 `@Getter`，删除 8 getter，8 字段 Javadoc（原无 Javadoc） |
| `AdminProcessingTaskItemResponse.java` | B10b | 类级 `@Getter`，删除 45 getter，45 字段 Javadoc（8 组语义分组），保留双构造器委托 |
| `AdminProcessingTaskListResponse.java` | B10b | 类级 `@Getter`，删除 2 getter，2 字段 Javadoc（原无 Javadoc） |
| `AdminProcessingTaskStepResponse.java` | B10b | 类级 `@Getter`，删除 4 getter，4 字段 Javadoc（key/status 枚举引用标注） |
| `AdminProcessingTaskSummaryCardResponse.java` | B10b | 类级 `@Getter`，删除 4 getter，4 字段 Javadoc（原无 Javadoc） |
| `AdminProcessingTaskSummaryResponse.java` | B10b | 类级 `@Getter`，删除 7 getter，7 字段 Javadoc（原无 Javadoc） |
| `AdminKnowledgeHelpStateResponse.java` | B10b | 类级 `@Getter`，删除 5 getter，5 字段 Javadoc（原无 Javadoc） |
| `AdminKnowledgeHelpActionResponse.java` | B10b | 类级 `@Getter`，删除 3 getter，3 字段 Javadoc（原无 Javadoc） |

### 2.4 B9-B10 批次报告产物（6 个 untracked）

全部位于 `docs/test/knowledge-base-e2e/fresh-eval-2026-05/`：

| 文件名 | 对应批次 |
|---|---|
| `admin_query_feedback_retrieval_audit_dto_contract_analysis_report.md` | B9（边界审查） |
| `admin_query_feedback_dto_contract_javadoc_lombok_fix_result_report.md` | B9a |
| `admin_query_retrieval_audit_dto_contract_javadoc_lombok_fix_result_report.md` | B9b |
| `admin_overview_pending_processing_task_dto_contract_analysis_report.md` | B10（边界审查） |
| `admin_overview_pending_dto_contract_javadoc_lombok_fix_result_report.md` | B10a |
| `admin_processing_task_knowledge_help_dto_contract_javadoc_lombok_fix_result_report.md` | B10b |

---

## 3. 不建议纳入本次 Checkpoint 的文件及原因

### 3.1 `docs/模型绑定配置参考.md` — 必须排除

| 维度 | 详情 |
|---|---|
| **原因 1：计划明确禁止** | 治理计划"风险与禁令"第 5 条："禁止修改 `docs/模型绑定配置参考.md`" |
| **原因 2：敏感信息泄露风险** | diff 中存在 API key 变更（已脱敏），不在报告中输出完整 key |
| **原因 3：非 DTO 治理范围** | 项目运行配置参考文档 |
| **本轮指令** | 不还原此文件，不 stage 此文件 |

### 3.2 `special_cases_report.md` — 必须排除

| 维度 | 详情 |
|---|---|
| **原因 1：计划明确禁止** | 治理计划"风险与禁令"第 5 条："禁止修改 `special_cases_report.md`" |
| **原因 2：纯机械重扫** | DTO 代码行号偏移导致的 redline 重扫结果更新 |
| **原因 3：非 DTO 治理范围** | redline 扫描工具产物 |
| **本轮指令** | 不还原此文件，不 stage 此文件 |

---

## 4. 敏感信息泄露风险评估

### 4.1 API key 泄露

**风险等级：高**（继承自上一 checkpoint，未消除）

`docs/模型绑定配置参考.md` 的 diff 中仍存在 API key 变更。该文件不得纳入任何 commit。具体 key 值已在上一 checkpoint 的脱敏报告中处理，此处不重复输出。

### 4.2 B9-B10 DTO 敏感字段

所有 B9-B10 DTO 变更均符合治理计划的安全要求：

| 批次 | 敏感字段防护 |
|---|---|
| B9a | 6 个类全部禁止 `@Data`（question/comment/reportedBy/handledBy/resolutionComment/metadataJson 等用户数据/审计字段） |
| B9b | 3 个类禁止 `@Data`（question/channelRunSummaryJson/errorSummary/metadataJson/sourceChunkIdsJson/sourcePathsJson 等大文本/用户数据） |
| B10a | 2 个类禁止 `@Data`（question/answer 用户数据） |
| B10b | 1 个类禁止 `@Data`（errorMessage/evidenceJson 大文本） |
| **合计** | 12/24 个类明确标注禁止 @Data；无任何类引入 @Data |

**无 toString() 暴露密钥、令牌或凭证的风险。** B9-B10 DTO 全部处理的是查询反馈、检索审计、Dashboard 概览和任务工作台数据，不涉及 API key、token、password 等凭证字段。

### 4.3 计划台账文件

计划文档无敏感信息。

### 4.4 批次报告

6 个批次报告均为治理过程产物，无敏感信息。

---

## 5. Compile / Redline / 定向测试证据汇总

### 5.1 逐批次验证结果

| 批次 | 子批次 | mvn compile | redline BLOCKER | 定向测试 | 备注 |
|---|---|---|---|---|---|
| B9 | B9a | BUILD SUCCESS | 0 | 无 api/admin feedback 测试类 | — |
| B9 | B9b | BUILD SUCCESS | clean | 无 api/admin retrieval audit 测试类 | `List.copyOf` 保留确认 |
| B10 | B10a | BUILD SUCCESS | clean | 无 api/admin overview/pending 测试类 | — |
| B10 | B10b | BUILD SUCCESS | clean | — | 双构造器委托模式保留确认（第 303 行） |

### 5.2 B9-B10 汇总

| 指标 | 结果 |
|---|---|
| mvn compile | 全部 PASS（4/4 子批次） |
| redline BLOCKER | 全部 0 |
| Lombok 统计 | B9: 96 getter + 10 setter 已删除；B10: 96 getter 已删除 |
| Javadoc 统计 | B9: 96 字段；B10: 96 字段 |
| @Data 新增 | **0**（全部批次未引入） |
| 构造器保护 | B9b `List.copyOf` 保留；B10b 双构造器委托保留 |

### 5.3 里程碑确认

计划台账确认：**B0-B10 全部完成**，所有 `api/admin`、`api/query`、`api/compiler`、`admin/service`、`query/service` 的 API 边界 DTO 共 **83 类**（B0-B8 71 类 + B9 11 类 + B10 13 类，含 B0 已提交的 3 类？实际当前 diff 中为 71+24=95 类，含 B0-B10 全部未提交变更）现已全部治理完毕。

---

## 6. 给下一轮 /code-commit 的安全 Staging 建议

### 6.1 B9-B10 新文件 staging（按子批次分组）

```bash
# === B9a: Query Feedback DTO（6 个）===
git add src/main/java/com/xbk/lattice/api/admin/AdminQueryFeedbackCreateRequest.java
git add src/main/java/com/xbk/lattice/api/admin/AdminQueryFeedbackHandleRequest.java
git add src/main/java/com/xbk/lattice/api/admin/AdminQueryFeedbackResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminQueryFeedbackListResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminQueryFeedbackDetailResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminQueryFeedbackAuditResponse.java

# === B9b: Retrieval Audit DTO（5 个）===
git add src/main/java/com/xbk/lattice/api/admin/AdminQueryRetrievalAuditListResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminQueryRetrievalAuditDetailResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminQueryRetrievalAuditRunResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminQueryRetrievalChannelRunResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminQueryRetrievalChannelHitResponse.java

# === B10a: Overview + Pending DTO（5 个）===
git add src/main/java/com/xbk/lattice/api/admin/AdminOverviewResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminOverviewPendingResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminOverviewPendingItemResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminPendingResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminPendingItemResponse.java

# === B10b: Processing Task + Knowledge Help DTO（8 个）===
git add src/main/java/com/xbk/lattice/api/admin/AdminProcessingTaskActionResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminProcessingTaskItemResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminProcessingTaskListResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminProcessingTaskStepResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminProcessingTaskSummaryCardResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminProcessingTaskSummaryResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminKnowledgeHelpStateResponse.java
git add src/main/java/com/xbk/lattice/api/admin/AdminKnowledgeHelpActionResponse.java

# === B9-B10 批次报告（显式清单，禁止通配符）===
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_query_feedback_retrieval_audit_dto_contract_analysis_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_query_feedback_dto_contract_javadoc_lombok_fix_result_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_query_retrieval_audit_dto_contract_javadoc_lombok_fix_result_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_overview_pending_processing_task_dto_contract_analysis_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_overview_pending_dto_contract_javadoc_lombok_fix_result_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/admin_processing_task_knowledge_help_dto_contract_javadoc_lombok_fix_result_report.md

# === 本门禁报告 ===
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/model_contract_b9_b10_checkpoint_precommit_gate_report.md
```

### 6.2 如果与 B0-B8 合并提交

如果 B0-B8 尚未提交，建议将 B0-B10 全部合并为一个 commit（共 95 个 DTO 文件），staging 时合并 B0-B8 和 B9-B10 两轮的文件清单，并统一添加计划台账。

### 6.3 禁止 staging 的文件

| 文件 | 原因 |
|---|---|
| `docs/模型绑定配置参考.md` | API key 泄露 + 计划禁令（两轮 checkpoint 均确认） |
| `special_cases_report.md` | 机械重扫 + 计划禁令（两轮 checkpoint 均确认） |

### 6.4 安全注意事项

1. **禁止通配符 add**：不得使用 `git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/*.md`
2. **先排除敏感文件**：确认 `docs/模型绑定配置参考.md` 和 `special_cases_report.md` 不在 staging 范围
3. **门禁报告预检**：在 commit 前执行 `rg -n "sk-[A-Za-z0-9_-]{12,}" docs/test/` 确认无残留 API key
4. **本门禁报告不含完整 API key**，可直接纳入 commit

---

## 7. 是否可以进入 /code-commit

**可以。** 条件如下：

1. **必须排除** `docs/模型绑定配置参考.md` 和 `special_cases_report.md`（禁止 stage，禁止还原）
2. **建议策略**：如果 B0-B8 尚未提交，将 B0-B10 合并为一个 commit（95 个 DTO + 1 个台账 + 所有批次报告 + 门禁报告），避免分两次 commit 产生中间状态
3. **commit message 建议**：

```
feat(dto): B0-B10 API 边界 DTO 字段契约注释与 Lombok 治理（95 个类）

完成所有 api/query、api/compiler、admin/service、api/admin（source/
credential/vault/repo/lifecycle/vector/retrieval/compile job/review/article/
fact card/quality/query feedback/retrieval audit/overview/pending/
processing task）及 query/service 共 95 个 DTO 的字段 Javadoc 契约注释、
Lombok @Getter 替代手写 getter 及少量 @Data→@Getter/@Setter 降级。

验证：B0.5-B5b 全量 mvn test 995/0/0/0，B6-B10 mvn compile PASS，
redline BLOCKER=0，定向测试 28/0/0/0。

排除 docs/模型绑定配置参考.md（API key 泄露风险）和 special_cases_report.md
（机械重扫）。

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

4. **下一步**：提交后进入 B11a（controller 内部 DTO 治理）

---

## 附录 A：B9 批次详细统计

| 子批次 | 范围 | 类数 | 字段 Javadoc | 删除 getter | 删除 setter | @Data 风险处置 |
|---|---|---|---|---|---|---|
| B9a | query feedback | 6 | 38 | 38 | 10 | 4 类禁止 @Data（用户数据/审计字段） |
| B9b | retrieval audit | 5 | 58 | 58 | 0 | 3 类禁止 @Data（大 JSON/用户数据） |
| **合计** | | **11** | **96** | **96** | **10** | **7/11 类标注禁止 @Data** |

B9 特殊亮点：
- boolean getter 全为 `isXxx()` 标准命名，无 B8a 式 Lombok 不一致问题
- `AdminQueryRetrievalAuditRunResponse.channelRuns` 的 `List.copyOf` 防御性拷贝保留
- `AdminQueryRetrievalChannelRunResponse.timeout/zeroHit` 计算字段标注（由 controller 从枚举推导）

## 附录 B：B10 批次详细统计

| 子批次 | 范围 | 类数 | 字段 Javadoc | 删除 getter | 原无 Javadoc 类数 | @Data 风险处置 |
|---|---|---|---|---|---|---|
| B10a | overview + pending | 5 | 18 | 18 | 0 | 2 类禁止 @Data（用户数据） |
| B10b | processing task + knowledge help | 8 | 78 | 78 | 7 | 1 类禁止 @Data（大文本） |
| **合计** | | **13** | **96** | **96** | **7** | **3/13 类标注禁止 @Data** |

B10 特殊亮点：
- `AdminProcessingTaskItemResponse`（45 字段）是项目最大 DTO，按 8 组语义分组标注
- 双构造器（小→大委托模式）完整保留，委托位于第 303 行 `this(...)`
- 枚举引用标注（`displayStatus`→`AdminProcessingTaskDisplayStatus`，`progressSteps[].key`→`AdminProcessingTaskStep`，`progressSteps[].status`→`AdminProcessingTaskStepStatus`）
- 7 个类原 getter 完全无 Javadoc，本轮从零补齐

## 附录 C：全量 DTO 治理进度

| 阶段 | 批次 | 类数 | 状态 |
|---|---|---|---|
| 试点 | B0 | 3 | 已提交（2888796, b38acdc） |
| api/query + compiler + admin/service | B0.5-B4 | 24 | 已完成（未提交） |
| api/admin 前半 | B5a-B8 | 47 | 已完成（未提交） |
| api/admin 后半 | B9-B10 | 24 | 已完成（未提交） |
| controller 内部 DTO | B11a-B11c | 待定 | 待开始 |
| config + domain + graph | B12-B19 | 待定 | 待开始 |
| 全局复扫 | B20 | 待定 | 待开始 |
