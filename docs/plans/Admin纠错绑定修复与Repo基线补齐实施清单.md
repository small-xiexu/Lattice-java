# Admin纠错绑定修复与Repo基线补齐实施清单

## 1. 目标

基于已完成的“入口文档收口”和最新一轮真实复核结果，继续完成两项剩余缺口的修复与补验，确保：

- `Admin 文章纠错` 不再受 README 演示绑定污染，默认运行口径下具备可执行的修复路径
- `repo snapshot diff / rollback` 不再长期卡在 `gitCommit=null`，仓库里存在可复用的 Git-backed baseline 建立能力
- 后续真实验收在独立基线或专用 schema 下可重复执行，不误伤共享 `lattice` 数据
- `README.md`、`docs/项目全流程真实验收手册.md` 与本轮唯一活动台账保持一致

## 2. 范围

- 文档入口：
  - `README.md`
  - `docs/项目全流程真实验收手册.md`
- 本轮唯一活动台账：
  - `docs/plans/Admin纠错绑定修复与Repo基线补齐实施清单.md`
- LLM 配置与运行时绑定：
  - `src/main/java/com/xbk/lattice/llm/**`
  - `src/main/java/com/xbk/lattice/compiler/service/LlmGateway.java`
  - `src/main/java/com/xbk/lattice/governance/ArticleCorrectionService.java`
- Repo snapshot / Vault Git：
  - `src/main/java/com/xbk/lattice/governance/repo/**`
  - `src/main/java/com/xbk/lattice/vault/**`
  - `src/main/java/com/xbk/lattice/api/admin/AdminRepoSnapshotController.java`

## 3. 非目标

- 本轮不回退已完成的 `ChatClient + Advisor`、`OpenAI 配置回归`、`增量编译收紧` 实现
- 本轮不删除历史方案文档与已完成实施清单
- 在未确认真实可用连接前，不直接把未知密钥或未知外部网关写入共享 `lattice` schema
- 本轮不在共享 `lattice` 数据上直接做高风险 live rollback 验证

## 4. 实施清单

- [x] F1 建立新唯一活动台账并切换入口文档指向
  - 验收：已创建 `docs/plans/Admin纠错绑定修复与Repo基线补齐实施清单.md`，并把 `README.md` 与 `docs/项目全流程真实验收手册.md` 的当前活动台账入口切到本清单。

- [ ] F2 复核并收敛 Admin 纠错绑定修复路径
  - 阻塞：当前 `lattice` schema 中仅存在 README 演示绑定，且当前 `18082` 进程未暴露可复用的真实 bootstrap 覆盖配置；在缺少真实可用连接前，无法把 Admin 纠错直接修到可验真状态。
  - 下一步：在 repo baseline 能力已补齐的前提下，继续决定是否通过代码防呆或专用连接导入继续收口 Admin 纠错。

- [x] F3 为 repo snapshot 增加 Git-backed baseline 建立能力
  - 验收：已补齐显式 baseline 建立入口，协调 `vault export -> git commit -> repo snapshot(gitCommit)`，并新增管理接口 `POST /api/v1/admin/snapshot/repo/baseline` 与 CLI 命令 `repo-baseline`。
  - 验收：已通过 `mvn -q -s .codex/maven-settings.xml -Dtest=VaultSnapshotServiceTests,AdminRepoSnapshotControllerTests test`，并顺手修正 `VaultExportService` 在无内容变化时仍刷新 manifest 时间导致重复 Git commit 的问题。

- [ ] F4 在隔离基线下补齐专项验收
  - 进行中：repo 侧隔离基线回归已通过 `VaultSnapshotServiceTests` 与 `AdminRepoSnapshotControllerTests` 覆盖 baseline / diff / rollback 主链路；剩余 Admin 纠错仍受真实可用连接缺失约束。

- [ ] F5 回写最终结论并收口入口文档
  - 待开始：根据 F2-F4 的结果更新 `README.md` 与验收手册，明确哪些缺口已关闭、哪些仍受环境前置条件限制。

## 5. 当前断点

- 当前阶段：F4 进行中
- 已知事实：
  - 当前共享 `lattice` schema 里只有一套 README 演示 LLM 绑定，`compile.writer` 命中 `http://127.0.0.1:19999`
  - `POST /api/v1/admin/articles/ops/correct` 在 `18082` 上稳定返回 `500 / COMPILE_IO_ERROR`
  - repo baseline 独立入口、repo diff 与 repo rollback 的隔离集成链路已经补齐并通过定向测试
- 下一步：
  - 继续收敛 Admin 纠错的最终修复路径，并在有真实可用连接后补做专项验收与文档收口
