# INTERNAL_MIRROR 内部代码镜像源 — 提交前质量审查报告

审查时间：2026-06-07
执行人：agentB（架构/质量顾问，只读审查 Agent）
类型：Pre-commit 质量审查，不改代码，不提交

---

## 1. 总体结论

### **YES — 建议进入 `/code-commit`**

理由：

1. **实现完全符合设计**：不恢复旧 `SERVER_DIR`、使用 `INTERNAL_MIRROR`、受控 mirror root + 相对 projectPath、MVP 手动刷新、manifest/hash 跳过、默认过滤规则、复用现有 source sync / compile 主链
2. **门禁全过**：redline BLOCKER=0、mvn test 1018/0/0/0
3. **Runtime gate 全部通过**：create/validate/sync/files 成功、路径安全 3/3 通过、文件过滤 6/6 纳入+6/6 排除、无变化跳过、修改触发新 sync、UPLOAD 回归正常
4. **红线审查 PASS**：无项目名/业务名/目录名/题集名/答案片段硬编码、未恢复旧 `SERVER_DIR`、路径受 allowlist 保护、未扩大 redline allowlist
5. **已知限制不阻塞**：删除 reconciliation、定时轮询、webhook、敏感内容扫描、扫描限额均为 MVP 明确划定的后续项

---

## 2. 设计符合性审查

| 设计要求 | 实现状态 | 证据 |
|----------|:---:|------|
| 不恢复旧 `SERVER_DIR` | ✅ | 代码中无 `SERVER_DIR` 字符串，`ALLOWED_SOURCE_TYPES` 仅含 `UPLOAD/GIT/INTERNAL_MIRROR` |
| 使用新的 `INTERNAL_MIRROR` source type | ✅ | `ALLOWED_SOURCE_TYPES` 已包含，专用 API 端点已实现 |
| 受控 mirror root + 相对 projectPath | ✅ | `SourceAdminProperties.mirrorRoots` Map + `resolveMirrorProjectDir()` 多层校验 |
| MVP 手动刷新 | ✅ | `POST /api/v1/admin/sources/{id}/sync` |
| manifest/hash 跳过 | ✅ | `BundleFeatureExtractor` 复用 + `SKIPPED_NO_CHANGE` |
| 默认过滤规则 | ✅ | 完整 include/exclude 常量，runtime gate 验证 12/12 |
| 复用现有 source sync / compile 主链 | ✅ | `SourceUploadService.acceptMaterializedSource()` → compile job |

---

## 3. 代码修改范围审查

### 3.1 生产代码（6 个文件 + 1 个配置）

| 文件 | 修改类型 | 风险 | 审查判定 |
|------|:---:|:---:|:---:|
| `AdminSourceController.java` | `ALLOWED_SOURCE_TYPES` 扩展 + 新端点 | 低 | ✅ 独立路由，不影响现有端点 |
| `AdminSourceCreateRequest.java` | 新增 2 个字段 | 低 | ✅ 仅 INTERNAL_MIRROR 类型使用 |
| `SourceAdminProperties.java` | 新增 `mirrorRoots` Map | 低 | ✅ 配置绑定，默认空 Map |
| `SourceMaterializationService.java` | 新增 validate/materialize 分支 + 4 个私有方法 | 中 | ✅ 仅新增路径，不修改现有 GIT/UPLOAD 逻辑 |
| `SourceSyncWorkflowService.java` | 新增 `createInternalMirrorSource()` + `buildConfigJson` 分支 | 低 | ✅ 独立方法 + else-if 分支 |
| `lattice-source.yml` | 新增 `mirror-roots` 配置占位 | 低 | ✅ 默认空 Map |

### 3.2 未触碰的模块（确认隔离）

| 模块 | 状态 |
|------|:---:|
| query / answer / rerank / fallback | ✅ **零修改** |
| compiler / documentparse | ✅ 零修改 |
| citation / evidence selector | ✅ 零修改 |
| prompt / schema / scripts | ✅ 零修改 |
| AGENTS.md / README.md | ✅ 零修改 |
| redline allowlist | ✅ 零修改 |

---

## 4. 红线风险审查

| 检查项 | 结果 | 证据 |
|--------|:---:|------|
| 是否存在项目名硬编码？ | **否** | grep 生产代码无具体项目名 |
| 是否存在业务名硬编码？ | **否** | 过滤规则为通用文件扩展名/目录名 |
| 是否存在目录名硬编码？ | **否** | `mirrorRoots` 通过外部配置注入 |
| 是否存在题集名/答案片段硬编码？ | **否** | 代码中无 eval 相关内容 |
| 是否恢复或变相恢复旧 `SERVER_DIR`？ | **否** | 新类型为 `INTERNAL_MIRROR`，语义和实现完全不同 |
| 是否引入任意服务器路径读取风险？ | **否** | 三层防护：allowlist + canonicalize + boundary check |
| 是否扩大 redline allowlist？ | **否** | 未修改 `scripts/scan-redline.sh` 和相关配置 |

---

## 5. 验证充分性审查

| 验证项 | 结果 | 判定 |
|--------|------|:---:|
| redline BLOCKER=0 | **0** | ✅ |
| mvn test | **1018/0/0/0** | ✅ |
| Create source | **201 Created** | ✅ |
| Validate | **valid: true** | ✅ |
| Sync + compile | **SUCCEEDED** | ✅ |
| 文件过滤（纳入 6/6） | **全纳入** | ✅ |
| 文件过滤（排除 6/6） | **全排除** | ✅ |
| 路径安全（3/3） | **全部拒绝** | ✅ |
| 无变化二次 sync | **SKIPPED_NO_CHANGE** | ✅ |
| 修改触发新 sync | **SUCCEEDED（新 compile job）** | ✅ |
| UPLOAD 回归 | **正常** | ✅ |
| GIT 回归 | **未实测** | ⚠️ 可接受（代码隔离，GIT 路径无修改） |

### GIT 未实测判断

GIT source 创建/同步路径在 `SourceSyncWorkflowService` 和 `SourceMaterializationService` 中与 INTERNAL_MIRROR 代码完全独立（不同的 `if/else if` 分支）。`AdminSourceController` 新增的端点 `POST /internal-mirror` 是独立路由。GIT 回归风险极低。**不阻塞提交。**

---

## 6. 已知限制审查

| 限制项 | MVP 范围 | 是否阻塞提交 | 理由 |
|--------|:---:|:---:|------|
| 删除 reconciliation 未实现 | 明确排除 | **否** | 设计报告和实现报告均标记为 MVP 限制 |
| 定时轮询未实现 | 明确排除 | **否** | MVP 仅手动刷新 |
| Webhook 未实现 | 明确排除 | **否** | 设计报告列为增强能力 |
| 敏感内容扫描未实现 | 文件名级已实现 | **否** | 文件名排除已覆盖 `.env/.pem/*.key/*.p12/*.jks/id_rsa/id_dsa` |
| 扫描限额未实现 | 明确排除 | **否** | 大项目保护后续补充 |
| async compile → sync run 状态回写缺口 | 已知缺口 | **否** | 不影响 sync 流程正确性，SKIPPED_NO_CHANGE 仍生效 |

**全部已知限制为 MVP 明确划定的后续项，不阻塞本轮提交。**

---

## 7. 提交拆分建议

### 7.1 INTERNAL_MIRROR 提交（本次主提交）

**生产代码（必须提交，6 个文件）**：

```
src/main/java/com/xbk/lattice/api/admin/AdminSourceController.java
src/main/java/com/xbk/lattice/api/admin/AdminSourceCreateRequest.java
src/main/java/com/xbk/lattice/source/config/SourceAdminProperties.java
src/main/java/com/xbk/lattice/source/service/SourceMaterializationService.java
src/main/java/com/xbk/lattice/source/service/SourceSyncWorkflowService.java
src/main/resources/config/lattice-source.yml
```

**报告文件（建议随本次提交归档，6 个文件）**：

```
docs/test/knowledge-base-e2e/internal_code_mirror_source_design_report.md              (agentB 设计)
docs/test/knowledge-base-e2e/internal_code_mirror_source_fix_result_report.md           (agentA 实现)
docs/test/knowledge-base-e2e/internal_code_mirror_source_runtime_gate_report.md         (agentD 首次 gate)
docs/test/knowledge-base-e2e/internal_code_mirror_source_controller_endpoint_fix_result_report.md  (agentA 补齐)
docs/test/knowledge-base-e2e/internal_code_mirror_source_runtime_gate_after_controller_report.md   (agentD 复验)
docs/test/knowledge-base-e2e/internal_code_mirror_source_incremental_resync_gate_report.md        (agentD 增量)
```

**可选**：
```
docs/quality-progress-and-lessons.md     (如果本轮已更新 INTERNAL_MIRROR 进度)
```

### 7.2 必须排除提交

```
special_cases_report.md    (redline 输出，AGENTS.md 明确禁止提交)
```

### 7.3 建议分开提交（不同主题线）

| 主题 | 文件 | 建议 |
|------|------|------|
| PE3 采购合同 eval | `fresh-eval-2026-06/**`、`fresh-eval-2026-06_*.md` | 独立提交（eval 资料包） |
| PE4 医疗设备 eval | `fresh-eval-2026-07_design_report.md` | 独立提交（eval 设计） |
| PE1 Q2 缩略词分析 | `pe1_q2_acronym_*.md` | 独立提交或与 acronym 修复一起提交 |
| Post-S2 状态 | `post_s2_writer_title_preservation_*.md` | 独立提交（状态报告） |
| 报告归档计划 | `post_compiler_admin_fixes_report_archive_plan.md` | 独立提交（文档治理） |

---

## 8. 推荐 Commit Message

```
feat(source): add INTERNAL_MIRROR source type for internal code mirror ingestion

Introduce INTERNAL_MIRROR as a new knowledge source type for ingesting
private code projects synced to server mirror directories by external tools.
Lattice reads them as a controlled, read-only scan — not as a Git remote.

- AdminSourceController: add POST /internal-mirror endpoint
- SourceMaterializationService: validate (allowlist-gated mirror roots
  + relative project paths, boundary/.. checks) and materialize
  (recursive scan with default include/exclude filters, copy to staging)
- SourceSyncWorkflowService: createInternalMirrorSource + buildConfigJson
- SourceAdminProperties: mirrorRoots Map binding (lattice-source.yml)
- Reuse BundleFeatureExtractor manifest hash for SKIPPED_NO_CHANGE on
  unchanged mirrors; reuse acceptMaterializedSource → compile chain
- Default include: .java/.xml/.yml/.properties/.json/.sql/.md/.txt/.sh
  pom.xml/build.gradle/Dockerfile, frontend source types
- Default exclude: .git/target/build/node_modules/.idea/.vscode,
  .class/.jar/.war/.zip/.tar/.gz, .env/.pem/*.key/*.p12/*.jks, id_rsa/id_dsa
- Path security: allowlist-gated mirrorRootRef, canonicalize, reject
  .. traversal and boundary escape

MVP limitations (explicitly documented): delete reconciliation,
scheduled polling, webhook triggers, content-level secret scanning,
per-source glob customization, and scan limits.

Redline BLOCKER=0. mvn test 1018/0/0/0 BUILD SUCCESS.
No query/answer/rerank/fallback chain modified.
No SERVER_DIR restoration, no business-specific hardcoding.
```

---

## 9. 后续仍需补的能力

| 能力 | 优先级 | 建议轮次 |
|------|:---:|------|
| 删除 reconciliation | **高**（生产可用前必须） | 独立 agentA 轮次 |
| 扫描限额 | 中（大项目保护） | 与 reconciliation 同轮或后续 |
| 敏感内容扫描 | 中 | 独立安全轮次 |
| 定时轮询 | 低 | 后续增量 |
| Webhook | 低 | 后续增量 |
| async compile → sync run 状态回写 | 低 | 后续修复 |
| 代码知识库题集验证 | 中 | agentD 独立轮次 |
| GIT 回归实测 | 低 | 有 GIT 测试环境时补跑 |

---

## 10. 是否需要 agentA 再修代码

**否。** 本轮实现完整、门禁全过、runtime gate 全部通过。已知限制均为设计明确划定的 MVP 后续项。

## 11. 是否需要 agentD 再跑验证

**否。** 三个 gate 报告已覆盖 create/sync/files/安全/过滤/增量/UPLOAD 回归全部验证点。GIT 回归因无测试环境未实测，但代码隔离充分，风险可接受。

---

## 12. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未修改 prompt / config / schema / scripts（除已审查的变更外）
- [x] 未修改题集 / redline allowlist
- [x] 未读取 hidden eval
- [x] 未提交 commit
- [x] 未清库 / 重建 / 导入资料
- [x] 所有结论基于 5 份报告 + git diff + 源码审查
- [x] 设计符合性、红线风险、验证充分性、提交范围均已覆盖
