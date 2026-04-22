package com.xbk.lattice.query.domain;

import java.util.List;

/**
 * Query 重写载荷
 *
 * 职责：承载 Query rewrite 结构化输出的最小语义
 *
 * @author xiexu
 */
public class QueryRewritePayload {

    private final String answerMarkdown;

    private final AnswerOutcome answerOutcome;

    private final GenerationMode generationMode;

    private final ModelExecutionStatus modelExecutionStatus;

    private final String confidenceLevel;

    private final List<String> missingInformation;

    /**
     * 创建 Query 重写载荷。
     *
     * @param answerMarkdown Markdown 答案
     * @param answerOutcome 答案语义
     * @param generationMode 生成模式
     * @param modelExecutionStatus 模型执行状态
     * @param confidenceLevel 置信度等级
     * @param missingInformation 缺失信息列表
     */
    public QueryRewritePayload(
            String answerMarkdown,
            AnswerOutcome answerOutcome,
            GenerationMode generationMode,
            ModelExecutionStatus modelExecutionStatus,
            String confidenceLevel,
            List<String> missingInformation
    ) {
        this.answerMarkdown = answerMarkdown;
        this.answerOutcome = answerOutcome;
        this.generationMode = generationMode;
        this.modelExecutionStatus = modelExecutionStatus;
        this.confidenceLevel = confidenceLevel;
        this.missingInformation = missingInformation;
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
     * 获取置信度等级。
     *
     * @return 置信度等级
     */
    public String getConfidenceLevel() {
        return confidenceLevel;
    }

    /**
     * 获取缺失信息列表。
     *
     * @return 缺失信息列表
     */
    public List<String> getMissingInformation() {
        return missingInformation;
    }

    /**
     * 转换为当前 Query 主链使用的最小答案载荷。
     *
     * @return Query 答案载荷
     */
    public QueryAnswerPayload toAnswerPayload() {
        boolean answerCacheable = answerOutcome == AnswerOutcome.SUCCESS;
        return new QueryAnswerPayload(
                answerMarkdown,
                answerOutcome,
                generationMode,
                modelExecutionStatus,
                answerCacheable
        );
    }
}
