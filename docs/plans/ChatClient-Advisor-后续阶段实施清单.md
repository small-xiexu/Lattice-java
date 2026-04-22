# ChatClient + Advisor 后续阶段实施清单

## 1. 目标

基于 `docs/LLM调用迁移到ChatClient-Advisor渐进式改造技术方案.md`、已完成的 `Phase 0.5` 与已大幅推进但尚未完整回写的 `Phase 1`，继续完成剩余阶段收口，确保在**方案整体验收完成前不删除方案文档与相关清单**：

- `Phase 2`：补齐 `query review + compile review` 统一执行链、统一 reviewer payload、统一缓存策略
- `Phase 3`：补齐治理侧关键 JSON 调用的 typed payload 与稳定解析路径
- `Phase 4`：让 compile / governance 仍保留的全文本调用统一收敛到 `ChatClient + Advisor + Executor`
- 形成可以支撑“方案整体已完成/仍未完成”的明确核账结论

## 2. 范围

- 文档台账：
  - `docs/LLM调用迁移到ChatClient-Advisor渐进式改造技术方案.md`
  - `docs/plans/ChatClient-Advisor-Phase1实施清单.md`
  - `docs/plans/ChatClient-Advisor-后续阶段实施清单.md`
- Query / compile / governance 相关实现：
  - `src/main/java/com/xbk/lattice/query/**`
  - `src/main/java/com/xbk/lattice/compiler/**`
  - `src/main/java/com/xbk/lattice/governance/**`
  - `src/main/java/com/xbk/lattice/llm/**`
- 回归测试：
  - `src/test/java/com/xbk/lattice/query/**`
  - `src/test/java/com/xbk/lattice/compiler/**`
  - `src/test/java/com/xbk/lattice/governance/**`
  - `src/test/java/com/xbk/lattice/llm/**`

## 3. 非目标

- 本轮不提前删除方案文档、阶段清单或回归清单
- 本轮不回退用户已有脏改
- 本轮不额外扩展与本方案无关的增量编译收紧事项

## 4. 实施清单

- [x] P2-1 核账 `Phase 2/3/4` 当前落地现状
  - 验收：已逐项核对 `query review / compile review / governance JSON / compile/governance text`，确认主缺口是 `LlmGateway` 尚未按 purpose 灰度 ChatClient 路径；本轮已补齐调用策略与配置开关。

- [x] P2-2 完成 `Phase 2` reviewer 路径收口
  - 验收：`LlmReviewerGateway` 与 `ArticleReviewerGateway` 已统一使用 raw facade、`ReviewResultParser/ReviewerPayload` 与 `PromptCacheWritePolicy`；`LlmReviewerGatewayTests`、`ArticleReviewerGatewayTests`、`LlmGatewayTests` 已通过。

- [x] P2-3 完成 `Phase 3` 治理 JSON 调用收口
  - 验收：`CrossValidatePayload / PropagationCheckPayload / AnalyzePayload` 已接入 `ArticleCorrectionService`、`PropagateExecutionService`、`AnalyzeNode` 的真实解析与降级分支；`ArticleCorrectionServiceTests`、`PropagateExecutionServiceTests`、`AnalyzeNodeTests` 已通过。

- [x] P2-4 完成 `Phase 4` 全文本调用统一执行链
  - 验收：`LlmGateway` 已按 `scene/agentRole/purpose` 做 ChatClient/legacy 分流；在 OpenAI/openai_compatible 主验收链路下，`compile-article / apply-correction / apply-propagation / review-fix / lint-fix / incremental-enhance / synthesis-*` 均通过 `LlmInvocationExecutor` 统一到底层 ChatClient，Anthropic 兼容路径仍按主方案要求保留 legacy fallback。

- [x] P2-5 完成回归验证并更新整体结论
  - 验收：`mvn -q -s .codex/maven-settings.xml -Dtest=LlmGatewayTests,AnswerGenerationServiceTests,LlmReviewerGatewayTests,ArticleReviewerGatewayTests,ArticleCorrectionServiceTests,PropagateExecutionServiceTests,LlmInvocationExecutorTests test` 与 `mvn -q -s .codex/maven-settings.xml -Dtest=AnalyzeNodeTests,ReviewFixServiceTests,LintFixServiceTests,SynthesisArtifactsServiceTests test` 已通过；`docs/plans/ChatClient-Advisor-Phase1实施清单.md` 已同步回写完成。

## 5. 当前断点

- 当前阶段：后续阶段收口已完成
- 已完成：
  - 已恢复误删的方案文档与既有清单，避免台账丢失
  - 已补齐 `LlmGateway` 的 purpose 级 ChatClient 灰度控制，避免只按 provider 类型硬切新执行链
  - 已完成 `Phase 1` 与后续阶段两份清单的进度回写
- 下一步：
  - 方案文档与清单现已可作为“整套迁移已完成”的留档依据；按你的要求，在你明确允许前不删除这些文档
