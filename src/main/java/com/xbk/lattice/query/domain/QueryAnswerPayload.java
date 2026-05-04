package com.xbk.lattice.query.domain;

/**
 * Query 答案载荷
 *
 * 职责：承载 Query 主链在结构化输出阶段生成的最小答案语义
 *
 * @author xiexu
 */
public class QueryAnswerPayload {

    private final String answerMarkdown;

    private final AnswerOutcome answerOutcome;

    private final GenerationMode generationMode;

    private final ModelExecutionStatus modelExecutionStatus;

    private final boolean answerCacheable;

    private final String fallbackReason;

    /**
     * 创建 Query 答案载荷。
     *
     * @param answerMarkdown Markdown 答案
     * @param answerOutcome 答案语义
     * @param generationMode 生成模式
     * @param modelExecutionStatus 模型执行状态
     * @param answerCacheable 是否允许写入 Query Cache
     * @param fallbackReason fallback 原因
     */
    public QueryAnswerPayload(
            String answerMarkdown,
            AnswerOutcome answerOutcome,
            GenerationMode generationMode,
            ModelExecutionStatus modelExecutionStatus,
            boolean answerCacheable
    ) {
        this(
                answerMarkdown,
                answerOutcome,
                generationMode,
                modelExecutionStatus,
                answerCacheable,
                ""
        );
    }

    /**
     * 创建 Query 答案载荷。
     *
     * @param answerMarkdown Markdown 答案
     * @param answerOutcome 答案语义
     * @param generationMode 生成模式
     * @param modelExecutionStatus 模型执行状态
     * @param answerCacheable 是否允许写入 Query Cache
     * @param fallbackReason fallback 原因
     */
    public QueryAnswerPayload(
            String answerMarkdown,
            AnswerOutcome answerOutcome,
            GenerationMode generationMode,
            ModelExecutionStatus modelExecutionStatus,
            boolean answerCacheable,
            String fallbackReason
    ) {
        this.answerMarkdown = answerMarkdown;
        this.answerOutcome = answerOutcome;
        this.generationMode = generationMode;
        this.modelExecutionStatus = modelExecutionStatus;
        this.answerCacheable = answerCacheable;
        this.fallbackReason = fallbackReason;
    }

    /**
     * 创建 LLM 成功返回的答案载荷。
     *
     * @param answerMarkdown Markdown 答案
     * @param answerOutcome 答案语义
     * @param answerCacheable 是否允许写入 Query Cache
     * @return Query 答案载荷
     */
    public static QueryAnswerPayload llm(
            String answerMarkdown,
            AnswerOutcome answerOutcome,
            boolean answerCacheable
    ) {
        return new QueryAnswerPayload(
                answerMarkdown,
                answerOutcome,
                GenerationMode.LLM,
                ModelExecutionStatus.SUCCESS,
                answerCacheable,
                ""
        );
    }

    /**
     * 创建规则路径答案载荷。
     *
     * @param answerMarkdown Markdown 答案
     * @param answerOutcome 答案语义
     * @return Query 答案载荷
     */
    public static QueryAnswerPayload ruleBased(String answerMarkdown, AnswerOutcome answerOutcome) {
        return new QueryAnswerPayload(
                answerMarkdown,
                answerOutcome,
                GenerationMode.RULE_BASED,
                ModelExecutionStatus.SKIPPED,
                false,
                ""
        );
    }

    /**
     * 创建模型返回不可复用结果后的降级答案载荷。
     *
     * @param answerMarkdown Markdown 答案
     * @return Query 答案载荷
     */
    public static QueryAnswerPayload fallback(String answerMarkdown) {
        return new QueryAnswerPayload(
                answerMarkdown,
                AnswerOutcome.PARTIAL_ANSWER,
                GenerationMode.FALLBACK,
                ModelExecutionStatus.DEGRADED,
                false,
                "LLM_UNSTRUCTURED_FALLBACK"
        );
    }

    /**
     * 创建模型降级后的答案载荷，并显式标注 fallback 原因。
     *
     * @param answerMarkdown Markdown 答案
     * @param fallbackReason fallback 原因
     * @return Query 答案载荷
     */
    public static QueryAnswerPayload fallback(String answerMarkdown, String fallbackReason) {
        return new QueryAnswerPayload(
                answerMarkdown,
                AnswerOutcome.PARTIAL_ANSWER,
                GenerationMode.FALLBACK,
                ModelExecutionStatus.DEGRADED,
                false,
                fallbackReason
        );
    }

    /**
     * 创建模型执行异常后的降级答案载荷。
     *
     * @param answerMarkdown Markdown 答案
     * @return Query 答案载荷
     */
    public static QueryAnswerPayload failedFallback(String answerMarkdown) {
        return new QueryAnswerPayload(
                answerMarkdown,
                AnswerOutcome.PARTIAL_ANSWER,
                GenerationMode.FALLBACK,
                ModelExecutionStatus.FAILED,
                false,
                "LLM_CALL_FAILED"
        );
    }

    /**
     * 获取 Markdown 答案。
     *
     * @return Markdown 答案
     */
    public String getAnswerMarkdown() {
        return answerMarkdown;
    }

    /**
     * 获取答案语义。
     *
     * @return 答案语义
     */
    public AnswerOutcome getAnswerOutcome() {
        return answerOutcome;
    }

    /**
     * 获取生成模式。
     *
     * @return 生成模式
     */
    public GenerationMode getGenerationMode() {
        return generationMode;
    }

    /**
     * 获取模型执行状态。
     *
     * @return 模型执行状态
     */
    public ModelExecutionStatus getModelExecutionStatus() {
        return modelExecutionStatus;
    }

    /**
     * 返回当前答案是否允许写入 Query Cache。
     *
     * @return 是否允许写入 Query Cache
     */
    public boolean isAnswerCacheable() {
        return answerCacheable;
    }

    /**
     * 返回 fallback 原因。
     *
     * @return fallback 原因
     */
    public String getFallbackReason() {
        return fallbackReason;
    }
}
