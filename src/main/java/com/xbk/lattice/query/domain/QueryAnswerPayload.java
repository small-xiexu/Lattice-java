package com.xbk.lattice.query.domain;

import lombok.Getter;

/**
 * Query 答案载荷。
 *
 * <p>承载 Query 主链在结构化输出阶段生成的最小答案语义——含 Markdown 文本、语义标签和缓存策略。
 * 通过 5 个 static factory 创建不同场景的实例。
 *
 * @author xiexu
 */
@Getter
public class QueryAnswerPayload {

    /** Markdown 格式答案文本。可能为大型文本。 */
    private final String answerMarkdown;
    /** 答案语义归类。 */
    private final AnswerOutcome answerOutcome;
    /** 答案生成模式。 */
    private final GenerationMode generationMode;
    /** 模型执行状态。 */
    private final ModelExecutionStatus modelExecutionStatus;
    /** 是否允许写入 Query Cache。 */
    private final boolean answerCacheable;
    /** fallback 原因。为空字符串表示非 fallback。 */
    private final String fallbackReason;

    public QueryAnswerPayload(
            String answerMarkdown, AnswerOutcome answerOutcome, GenerationMode generationMode,
            ModelExecutionStatus modelExecutionStatus, boolean answerCacheable
    ) {
        this(answerMarkdown, answerOutcome, generationMode, modelExecutionStatus, answerCacheable, "");
    }

    public QueryAnswerPayload(
            String answerMarkdown, AnswerOutcome answerOutcome, GenerationMode generationMode,
            ModelExecutionStatus modelExecutionStatus, boolean answerCacheable, String fallbackReason
    ) {
        this.answerMarkdown = answerMarkdown;
        this.answerOutcome = answerOutcome;
        this.generationMode = generationMode;
        this.modelExecutionStatus = modelExecutionStatus;
        this.answerCacheable = answerCacheable;
        this.fallbackReason = fallbackReason;
    }

    public static QueryAnswerPayload llm(String answerMarkdown, AnswerOutcome answerOutcome, boolean answerCacheable) {
        return new QueryAnswerPayload(answerMarkdown, answerOutcome, GenerationMode.LLM, ModelExecutionStatus.SUCCESS, answerCacheable, "");
    }

    public static QueryAnswerPayload ruleBased(String answerMarkdown, AnswerOutcome answerOutcome) {
        return new QueryAnswerPayload(answerMarkdown, answerOutcome, GenerationMode.RULE_BASED, ModelExecutionStatus.SKIPPED, false, "");
    }

    public static QueryAnswerPayload fallback(String answerMarkdown) {
        return new QueryAnswerPayload(answerMarkdown, AnswerOutcome.PARTIAL_ANSWER, GenerationMode.FALLBACK, ModelExecutionStatus.DEGRADED, false, "LLM_UNSTRUCTURED_FALLBACK");
    }

    public static QueryAnswerPayload fallback(String answerMarkdown, String fallbackReason) {
        return new QueryAnswerPayload(answerMarkdown, AnswerOutcome.PARTIAL_ANSWER, GenerationMode.FALLBACK, ModelExecutionStatus.DEGRADED, false, fallbackReason);
    }

    public static QueryAnswerPayload failedFallback(String answerMarkdown) {
        return new QueryAnswerPayload(answerMarkdown, AnswerOutcome.PARTIAL_ANSWER, GenerationMode.FALLBACK, ModelExecutionStatus.FAILED, false, "LLM_CALL_FAILED");
    }
}
