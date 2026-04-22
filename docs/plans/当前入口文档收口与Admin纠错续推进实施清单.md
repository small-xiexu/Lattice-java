# 当前入口文档收口与Admin纠错续推进实施清单

## 1. 目标

基于当前仓库已经完成的 `OpenAI 配置回归`、`ChatClient + Advisor`、`增量编译收紧` 三组实施清单，继续完成最后一轮入口文档与专项验收收口，确保：

- `README.md`、`docs/项目启动配置清单.md`、`docs/项目全流程真实验收手册.md` 三份入口文档的后续指向一致
- 仓库里存在且只存在一份**唯一活动台账**
- `Admin 文章纠错` 的最新真实验收结论有明确记录
- `repo diff / rollback` 的最新真实验收结论有明确记录
- 若专项链路仍受特定网关组合限制，文档需写实，不误标为“已完成”

## 2. 范围

- 文档入口：
  - `README.md`
  - `docs/项目启动配置清单.md`
  - `docs/项目全流程真实验收手册.md`
- 本轮唯一活动台账：
  - `docs/plans/当前入口文档收口与Admin纠错续推进实施清单.md`
- 专项验收链路：
  - Admin 文章纠错
  - repo snapshot diff / repo rollback

## 3. 非目标

- 本轮不回退已完成的 `ChatClient + Advisor`、`OpenAI 配置回归`、`增量编译收紧` 实现
- 本轮不提前删除已完成的历史方案文档与实施清单
- 本轮不扩大到与当前入口收口无关的新功能开发

## 4. 实施清单

- [x] E1 建立唯一活动台账并修正入口文档指向
  - 验收：已创建 `docs/plans/当前入口文档收口与Admin纠错续推进实施清单.md`；`README.md` 与 `docs/项目全流程真实验收手册.md` 的后续入口已统一指向本清单。

- [x] E2 复核并补齐 Admin 文章纠错最新真实验收
  - 验收：已在 `http://127.0.0.1:18082` 真实调用 `POST /api/v1/admin/articles/ops/correct`，当前稳定返回 `HTTP 500 / COMPILE_IO_ERROR`。
  - 结论：失败根因已定位为运行时 LLM 绑定配置而非主干纠错逻辑；当前 `compile.writer` 绑定命中 `readme-demo-openai -> http://127.0.0.1:19999`，`/api/v1/admin/llm/connections/test` 与 `/api/v1/admin/llm/models/test` 均返回 `Connection refused` / I/O error，因此在当前默认验收口径下 Admin 纠错未跑通。

- [x] E3 复核并补齐 repo diff / rollback 最新真实验收
  - 验收：已在 `http://127.0.0.1:18082` 真实调用 `/api/v1/admin/snapshot/repo?limit=10`，确认当前实例已有 `6` 条 repo snapshot history；已真实调用 `/api/v1/admin/vault/export` 导出到临时目录并生成 Vault `.git` 仓库。
  - 结论：`/api/v1/admin/snapshot/repo/6/diff?vaultDir=...` 当前稳定返回 `HTTP 400 / 目标 repo snapshot 未绑定 Vault Git commit: 6`；现有 compile / incremental / governance 写入的 repo snapshot 全部是 `gitCommit=null`，后台与 CLI 也没有补建 Git-backed baseline snapshot 的独立入口，因此最新一轮真实回归下 `repo diff` 未跑通。
  - 结论：`repo rollback` 代码路径与测试仍在，但本轮未直接对共享 `lattice` 数据执行真实回滚；原因是当前实例没有可复用的 Git-backed baseline snapshot，且 live rollback 会直接改写现有知识库状态，需在独立验收基线或专用 schema 下执行。

- [x] E4 回写最终结论并收口当前入口文档
  - 验收：`README.md` 与 `docs/项目全流程真实验收手册.md` 已回写 E2/E3 真实结论，明确 Admin 纠错当前受运行时 demo 连接约束、repo diff 当前受 `gitCommit=null` 基线约束，repo rollback 本轮未在共享 `lattice` 数据上直接执行。

## 5. 当前断点

- 当前阶段：本清单已完成
- 已知事实：
  - `docs/plans` 下既有实施清单均已完成并回写
  - `README`、`docs/项目启动配置清单.md` 与 `docs/项目全流程真实验收手册.md` 已完成当前入口收口
  - Admin 纠错与 repo diff / rollback 的最新真实结论已在本清单和入口文档中写实固化
- 下一步：
  - 若继续推进修复而不是继续收口，应新建或指定新的实施清单，再进入下一轮执行
