# compile_fixer_payload_slimming_fix_result_report

## 1. 修改了哪些文件和方法

- `src/main/java/com/xbk/lattice/compiler/service/ReviewFixService.java`
  - 修改 `applyFix(...)`
  - 新增 `boundText(...)`
- `src/test/java/com/xbk/lattice/compiler/service/ReviewFixServiceTests.java`
  - 新增 Fixer article/source payload 预算回归测试

## 2. Fixer 之前为什么重

修复前，Fixer 的输入构造方式是：

- `issueList`：最多前 5 条审查问题
- `articleContent`：**整篇原始文章全文直接传入**
- `sourceContents`：直接取 `ArticleCompileSupport -> CompileArticleNode.buildReviewSourceContents(...)` 的结果
- 之后仅对 `sourceContents` 做一个简单前缀截断：
  - `sourceContents.length() > 10000 ? substring(0, 10000) : sourceContents`

这意味着单次 Fixer 调用会同时带着：

- 较重的文章正文
- 较重的来源正文
- 再加上 issue list

而 Fixer 触发后还要再进一轮 Reviewer，因此一旦进入 `fix_review_issues`，总成本会被明显放大。

## 3. 现在如何瘦身

现在只对 Fixer 的输入预算做最小瘦身：

- `articleContent` 预算：`6000` 字符
- `sourceContents` 预算：`7000` 字符

实现方式：

- 在 `ReviewFixService.applyFix(...)` 中，不再把整篇 article 原文直接拼进 Prompt
- 也不再把 source 仅做单边 `10000` 前缀截断
- 改为：
  - `boundText(articleContent, 6000)`
  - `boundText(sourceContents, 7000)`
- 然后再构造 Fixer user prompt

所以本轮的瘦身是：

- **article/source 双预算**
- 控制 Fixer 单次输入总量
- 避免“整篇原文 + 较长 source”叠加过重

## 4. 是否复用已有机制

是，部分复用。

- `sourceContents` 仍然复用 Reviewer 侧已有的 payload slimming 入口：
  - `ArticleCompileSupport -> CompileArticleNode.buildReviewSourceContents(...)`
- 也就是说，Fixer 仍然吃的是 Reviewer 已经筛过的相关来源片段
- 本轮新增的是 **Fixer 自己的二次预算控制**：
  - 在 `ReviewFixService` 内再对 article/source 做有界截断

因此这轮没有另造一套新的 source relevance 选择器，而是在已有 Reviewer slimming 基础上继续控总量。

## 5. 是否会影响 re-review

不会改变 re-review 语义。

- `fix_review_issues -> review_articles` 的状态机未修改
- Fixer 成功后仍然回到 Reviewer
- 本轮只减少 Fixer 单次输入成本，不改变 Fixer 后续是否 re-review

## 6. 是否减少了 Fixer 覆盖面

否。

- 没有减少进入 Fixer 的文章数
- 没有改变 Reviewer / Fixer 的触发规则
- 没有改 `maxFixRounds`
- 只是缩小了 Fixer 单次调用的输入体积

## 7. 是否新增业务特判

否。

- 没有按业务词、文档名、样例字符串做特判
- 只是通用预算控制

## 8. redline BLOCKER 是否仍为 0

- 已运行 `bash scripts/scan-redline.sh special_cases_report.md`
- 结果：`BLOCKER=0`，`REVIEW=1912`，`ALLOWLIST=246`

## 9. 测试是否通过

- 定向测试通过：
  - `ReviewFixServiceTests`
  - `CompileArticleReviewFlowTests`
- 结果：`Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`
- 全量 `mvn test`：`Tests run: 867, Failures: 0, Errors: 0, Skipped: 0`

## 10. 下一轮是否建议交给 agentD 做性能复验

建议。

下一轮建议 agentD 做性能复验，重点看：

- 进入 `fix_review_issues` 的 job 上，Fixer 单次调用耗时是否下降
- review/fix loop 总耗时是否下降
- Fixer 后 re-review 语义是否保持不变
- Fixer 修复质量是否没有明显退化
