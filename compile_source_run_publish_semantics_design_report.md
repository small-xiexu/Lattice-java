# Compile Source Run / Publish 语义设计报告

## 1. 总体判断

当前问题的根因不是编译流程错了，而是**运行态语义错了**。

现在系统已经具备正确的治理闭环：

- Writer / Reviewer 正常执行
- 非通过内容不会直接入正式 `articles`
- 先进入 `compile_article_review_queue.review_status = needs_human_review`
- approve 后才入库
- reject 后不会入库

但 `source-run` / `processing-tasks` 仍把“compile job 自动执行结束”直接说成“资料已写入知识库”。这在引入 human review queue 之前还勉强成立，引入人工确认后就不成立了。

结论：

- **系统执行状态** 和 **业务发布结果** 当前被混成了一层
- 这层混淆会直接误导用户
- 下一轮最安全的修复应落在**读模型 / 展示语义层**，不要回头改 Writer / Reviewer / Fixer / approve / reject 流程

## 2. 当前状态机 / 显示语义哪里错了

### 2.1 当前 source-run 持久化状态流

当前 `SourceUploadWorkflowSupport` 的持久化状态流大致是：

```text
TASK_RECEIVED
-> MATCHING
-> WAIT_CONFIRM          (资料归并决策，编译前人工确认)
-> SKIPPED_NO_CHANGE
-> COMPILE_QUEUED
-> RUNNING
-> SUCCEEDED | FAILED
```

其中关键代码在：

- [SourceUploadWorkflowSupport.java](/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/source/service/SourceUploadWorkflowSupport.java)

最关键的错误点在 `refreshRunFromCompileJob(...)`：

- 只要 `compileJobRecord.getStatus() == SUCCEEDED`
- 就立刻把 `SourceSyncRun.status` 写成 `SUCCEEDED`
- 并把 evidence message 改成：`处理成功，资料已写入知识库`

也就是说，当前 persisted `SourceSyncRun` 把：

- `compile job 自动执行成功`

错误地等同成了：

- `业务正式发布成功`

### 2.2 当前 processing-tasks 显示链路

显示链路是：

1. `SourceUploadService.listRecentRunDetails()` / `getRunDetail()`
2. `SourceUploadWorkflowSupport.toDetail(...)`
3. `AdminProcessingTaskPresentationResolver.resolve(...)`
4. `AdminProcessingTaskService.toSourceSyncTask(...)`
5. 前端 `processing-tasks`

其中：

- `SourceUploadWorkflowSupport.toDetail(...)` 把 `run.status + compileJobStatus + compileDerivedStatus + message` 组装进 `SourceSyncRunDetail`
- `AdminProcessingTaskPresentationResolver` 根据 `displayStatus` 生成：
  - `displayStatusLabel`
  - `currentStepLabel`
  - `nextStepHint`
  - `reasonSummary`
  - `completionNotice`
- `AdminProcessingTaskService.buildSummaryCards(...)` 又把 `SUCCEEDED` 任务统一统计为“已完成”，并给出：
  - `最近已经成功处理并写入知识库`

### 2.3 具体错位点

当前有两层错位：

| 层 | 当前语义 | 实际语义 |
| --- | --- | --- |
| `SourceSyncRun.status=SUCCEEDED` | 资料已经成功入库 | 只是 compile job 自动执行结束 |
| `processing-tasks displayStatusLabel=已完成` + `reasonSummary=资料已写入知识库` | 已正式发布完成 | 可能只是进入了 `needs_human_review` 队列，还没 approve/reject |

所以现在的问题不是“文案太硬”，而是：

- **没有一层字段表达“publish result”**

## 3. “系统完成”与“已入库完成”是否需要拆开

需要，而且必须拆开。

### 建议拆成两层

#### A. 系统执行状态

表示自动流程跑到哪一步，是否还在跑：

- `MATCHING`
- `WAIT_CONFIRM`（资料归并决策）
- `COMPILE_QUEUED`
- `RUNNING`
- `SUCCEEDED`
- `FAILED`
- `SKIPPED_NO_CHANGE`

这层描述的是：

- 上传/同步/compile 自动链路有没有跑完

#### B. 业务发布结果

表示 compile 产物是否真的进入正式知识库：

- `PENDING_HUMAN_REVIEW`
- `PARTIALLY_PUBLISHED_PENDING_HUMAN_REVIEW`
- `PARTIALLY_PUBLISHED`
- `PUBLISHED`
- `REJECTED`

这层描述的是：

- 是否已正式入库
- 是否还有草稿待人工确认
- 是否有条目被驳回

### 为什么不能只改文案

因为现在不是只有“提示语错”：

- `AdminProcessingTaskSummary` 的 waiting / succeeded 计数也跟着错
- help card 的下一步建议也跟着错
- 完成提示 `completionNotice` 也跟着错

如果只把“资料已写入知识库”改软成“资料处理完成”，虽然没那么刺眼，但仍然掩盖了：

- 还没发布
- 还有待确认
- 最终可能 reject

所以不能把这件事简单归为“改文案”。

## 4. 每种人工确认场景应该如何展示

下面的“理想语义”都基于：

- compile job 自动部分已经结束
- queue 中存在按 `jobId` 可追踪的草稿结果

### 场景 A：全部待人工确认，尚未发布

条件：

- `publishedCount = 0`
- `pendingHumanReviewCount > 0`
- `rejectedCount = 0`

建议展示：

| 位置 | 建议 |
| --- | --- |
| `displayStatusLabel` | `待人工确认` |
| `currentStepLabel` | `等待人工确认` |
| `reasonSummary` | `质量检查已完成，等待人工确认后决定是否入库` |
| `nextStepHint` | `去待人工确认处理` |
| `completionNotice` | `草稿尚未入库，需人工确认后才能发布` |
| summary card | 记入 `待确认`，不要记入 `已完成` |

### 场景 B：部分 approve，部分待确认

条件：

- `publishedCount > 0`
- `pendingHumanReviewCount > 0`

建议展示：

| 位置 | 建议 |
| --- | --- |
| `displayStatusLabel` | `待人工确认` |
| `currentStepLabel` | `等待剩余草稿确认` |
| `reasonSummary` | `已入库 X 篇，仍有 Y 篇待人工确认` |
| `nextStepHint` | `去待人工确认处理` |
| `completionNotice` | `部分内容已入库，其余仍待人工确认` |
| summary card | 仍记入 `待确认`，不要记成纯 `已完成` |

### 场景 C：部分 approve，部分 reject

条件：

- `publishedCount > 0`
- `pendingHumanReviewCount = 0`
- `rejectedCount > 0`

建议展示：

| 位置 | 建议 |
| --- | --- |
| `displayStatusLabel` | `已处理` 或 `部分入库` |
| `currentStepLabel` | `人工确认已结束` |
| `reasonSummary` | `已入库 X 篇，已驳回 Y 篇` |
| `nextStepHint` | `查看已入库内容` |
| `completionNotice` | `本次草稿已处理完成，但只有部分内容进入知识库` |
| summary card | 可记入 `已完成`，但不得再写“资料已写入知识库”这种全量成功语义 |

### 场景 D：全部 approve

条件：

- `publishedCount > 0`
- `pendingHumanReviewCount = 0`
- `rejectedCount = 0`

建议展示：

| 位置 | 建议 |
| --- | --- |
| `displayStatusLabel` | `已完成` |
| `currentStepLabel` | `写入知识库` 或 `入库完成` |
| `reasonSummary` | `人工确认完成，资料已写入知识库` |
| `nextStepHint` | `可以查看已入库内容或继续问答` |
| `completionNotice` | `资料已正式发布到知识库` |
| summary card | 记入 `已完成` |

### 场景 E：全部 reject

条件：

- `publishedCount = 0`
- `pendingHumanReviewCount = 0`
- `rejectedCount > 0`

建议展示：

| 位置 | 建议 |
| --- | --- |
| `displayStatusLabel` | `已处理` 或 `未入库` |
| `currentStepLabel` | `人工确认已结束` |
| `reasonSummary` | `人工确认后未发布到知识库` |
| `nextStepHint` | `如需入库，请调整资料后重新导入` |
| `completionNotice` | `本次草稿已全部驳回，未进入正式知识库` |
| summary card | 不应记入 `待确认`；可记入 `已完成`，但必须配套真实业务文案 |

## 5. 当前 source-run / processing-tasks / human review queue 三者应如何分工

建议明确三者职责：

| 对象 | 真实职责 | 不应该承担什么 |
| --- | --- | --- |
| `source-run` | 记录上传/同步请求，以及 compile 自动执行是否结束 | 不应该单独代表“是否已正式发布” |
| `processing-tasks` | 把 source-run 和 compile job 投影成用户可读的当前任务状态 | 不应该仅根据 `SUCCEEDED` 就宣称“资料已写入知识库” |
| `human review queue` | 记录 compile 审查后待人工确认草稿，以及 approve/reject 结果 | 不应该只当详情页孤岛，而应反向决定 source-run 的业务发布结果 |

也就是说：

- `human review queue` 是 publish gate 的真实事实来源
- `source-run` 是系统执行生命周期
- `processing-tasks` 应该把两者合成一条真实业务状态

## 6. 最小安全修复范围

### 不建议的修法

不要先做下面这些：

1. 直接把 `SourceSyncRun.status` 从 `SUCCEEDED` 改成复用 `WAIT_CONFIRM`
2. 只把“资料已写入知识库”改成更模糊的文案
3. 用 `articles` 当前数量反推是否发布成功

原因：

- `WAIT_CONFIRM` 现在已经被“资料归并确认”占用，复用会混掉两种完全不同的人工动作
- 只改文案掩盖不了“待确认 / 部分发布 / 全驳回”的真实差异
- 用 `articles` 总数反推会丢失 job 级语义，无法区分本次 job 的 publish outcome

### 建议的最小安全修复

把修复限制在**admin/source 读模型**，不要动 compile 主链。

最小安全范围建议为：

- `src/main/java/com/xbk/lattice/infra/persistence/CompileArticleReviewQueueJdbcRepository.java`
- `src/main/java/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.java`
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/CompileArticleReviewQueueMapper.xml`
- `src/main/java/com/xbk/lattice/source/domain/SourceSyncRunDetail.java`
- `src/main/java/com/xbk/lattice/source/service/SourceUploadWorkflowSupport.java`
- `src/main/java/com/xbk/lattice/admin/service/AdminProcessingTaskPresentationResolver.java`
- `src/main/java/com/xbk/lattice/admin/service/AdminProcessingTaskService.java`
- `src/main/java/com/xbk/lattice/api/admin/AdminProcessingTaskItemResponse.java`

### 这组最小修复应该做什么

只做一件事：

- **新增“按 compile jobId 聚合 queue publish outcome 的只读投影”，并让 processing-tasks 基于这个投影生成业务语义**

具体含义：

1. `CompileArticleReviewQueueJdbcRepository` 新增按 `jobId` 聚合：
   - `pendingHumanReviewCount`
   - `publishedCount`
   - `rejectedCount`
2. `SourceUploadWorkflowSupport.toDetail(...)` 读取这组 queue 聚合结果，写进 `SourceSyncRunDetail`
3. `AdminProcessingTaskPresentationResolver` 基于：
   - 系统执行状态
   - queue publish outcome
   生成新的 `displayStatusLabel / reasonSummary / nextStepHint / completionNotice`
4. `AdminProcessingTaskService.buildSummary(...)` 基于 publish outcome 统计：
   - `待确认`
   - `已完成`
   而不是只看 `SUCCEEDED`

### 为什么这叫“最小安全”

- 不改 Writer / Reviewer / Fixer
- 不改 approve / reject / publish 行为
- 不改 source-run 持久化主状态机
- 不改 human review queue 流程
- 只修“如何读、如何展示”

这能把风险控制在最小，同时把语义纠正回来。

## 7. 下一轮禁止事项

下一轮如果实施，建议明确禁止：

1. 不准修改 Writer / Reviewer / Fixer / Persist 主链
2. 不准修改 approve / reject / publish 逻辑
3. 不准把 post-review 人工确认复用成现有 `WAIT_CONFIRM` 状态
4. 不准只改一句文案就声称问题解决
5. 不准用全库 `articles` 总数替代 job 级 publish outcome
6. 不准把 all-reject 归类成系统失败
7. 不准让 `processing-tasks` 再在 pending queue 存在时输出“资料已写入知识库”

## 8. 本轮是否修改代码

否。

本轮只做只读架构/语义分析，未修改任何代码、前端、测试、配置、脚本或数据库。
