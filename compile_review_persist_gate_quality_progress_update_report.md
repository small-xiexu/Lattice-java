# Compile Review Persist Gate 质量台账更新报告

更新时间：2026-05-17
执行 Agent：agentC

## 修改了哪些文档

- `docs/quality-progress-and-lessons.md`：更新了以下章节：
  - **当前阶段**：新增 compile review persist gate 已完成修复和测试补强。
  - **当前 Gate**：
    - redline 更新为 persist gate 测试补强后：`BLOCKER=0 / REVIEW=1351 / ALLOWLIST=166`，注明最终以 pre-commit 复核为准。
    - mvn test 更新为 `812/0/0`，注明新增 `PersistArticlesNodeTests`。
    - 新增 compile review persist gate 行：修复 + 测试补强完成。
  - **多 Agent 职责**：更新 agentC、agentD 状态。
  - **已验证结论**：新增 persist gate 已修复、测试补强已完成、Query visibility filter 仍是下一阶段。
  - **踩坑记录**：新增两条——
    - `allowPersistNeedsHumanReview=true` 曾存在绕过 persist gate 风险。
    - 运行时不易自然构造 `needs_human_review`，必须用极窄单元测试覆盖。
  - **下一步计划**：新增第 8 项（persist gate 已完成）、第 9 项（当前：agentD pre-commit 复核）、第 10 项（Query visibility 后续单独处理）。

## 是否修改源码

**否。** 本轮未修改 `src/main/java/**` 下任何文件。

## 是否修改测试

**否。** 本轮未修改 `src/test/java/**` 下任何文件。

## 是否修改配置/脚本

**否。** 本轮未修改 `src/main/resources/**`、`scripts/**`、`AGENTS.md`、`CLAUDE.md`、`special_cases_report.md` 及任何 baseline/eval 题集。

## 是否删除报告

**否。** 本轮未删除任何文件。

## 本轮已记录的关键结论

1. **persist gate 已修复**：`PersistArticlesNode` 不再合并 `needsHumanReviewArticlesRef`，只允许 `review_status=passed` 的 article 进入正式 persist。
2. **passed article 全链路完整**：运行时验证确认 articles(4) → chunks(19) → vector_index(4) → chunk_vector_index(19) 全链路一致。
3. **测试补强已完成**：`PersistArticlesNodeTests` 覆盖混合 passed + needs_human_review 输入，断言只 persist passed；定向测试通过，全量 812/0/0 通过。
4. **needs_human_review 端到端场景无法自然构造**：当前 rule-based reviewer + autoFixEnabled=true 条件下所有文章均为 passed；已通过源码审查 + 定向单元测试闭合验证缺口。
5. **Query visibility hard filter 仍是下一阶段**：不能和本轮 persist gate 混修。
6. **LLM reviewer 未开启**：当前 review route 仍为 rule-based。

## 下一步建议

交给 **agentD** 做 persist gate pre-commit 质量复核：
- 确认 redline BLOCKER=0，REVIEW/ALLOWLIST 均为既有命中
- 确认 mvn test=812/0/0
- 确认工作区只含允许变更
- 后续单独处理 Query visibility hard filter
