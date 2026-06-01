package com.xbk.lattice.query.domain;

import lombok.Getter;

import java.util.List;

/**
 * Query 重写载荷。
 *
 * <p>承载 Query rewrite 结构化输出的最小语义——含 Markdown 答案、置信度和缺失信息。
 * 通过 {@code toAnswerPayload()} 可转换为 QueryAnswerPayload。
 *
 * @author xiexu
 */
@Getter
public class QueryRewritePayload {

    /** Markdown 格式答案。可能为大型文本。 */
    private final String answerMarkdown;
    /** 答案语义归类。 */
    private final AnswerOutcome answerOutcome;
    /** 答案生成模式。 */
    private final GenerationMode generationMode;
    /** 模型执行状态。 */
    private final ModelExecutionStatus modelExecutionStatus;
    /** 置信度等级（如 high / medium / low）。 */
    private final String confidenceLevel;
    /** 缺失信息列表（rewrite 时发现的知识缺口）。 */
    private final List<String> missingInformation;

    public QueryRewritePayload(
            String answerMarkdown, AnswerOutcome answerOutcome, GenerationMode generationMode,
            ModelExecutionStatus modelExecutionStatus, String confidenceLevel, List<String> missingInformation
    ) {
        this.answerMarkdown = answerMarkdown;
        this.answerOutcome = answerOutcome;
        this.generationMode = generationMode;
        this.modelExecutionStatus = modelExecutionStatus;
        this.confidenceLevel = confidenceLevel;
        this.missingInformation = missingInformation;
    }

    /** 转换为当前 Query 主链使用的最小答案载荷。 */
    public QueryAnswerPayload toAnswerPayload() {
        boolean answerCacheable = answerOutcome == AnswerOutcome.SUCCESS;
        return new QueryAnswerPayload(answerMarkdown, answerOutcome, generationMode, modelExecutionStatus, answerCacheable);
    }
}
