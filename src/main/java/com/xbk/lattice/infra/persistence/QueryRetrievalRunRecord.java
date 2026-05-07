package com.xbk.lattice.infra.persistence;

/**
 * Query 检索审计主记录
 *
 * 职责：承载 query_retrieval_runs 表的最小写入字段
 *
 * @author xiexu
 */
public class QueryRetrievalRunRecord {

    private final String queryId;

    private final String question;

    private final String normalizedQuestion;

    private final String retrievalQuestion;

    private final String versionTag;

    private final String strategyTag;

    private final String questionTypeTag;

    private final String answerShape;

    private final String retrievalMode;

    private final boolean rewriteApplied;

    private final String rewriteAuditRef;

    private final String retrievalStrategyRef;

    private final int fusedHitCount;

    private final int channelCount;

    private final int factCardHitCount;

    private final int sourceChunkHitCount;

    private final String coverageStatus;

    private final String channelRunSummaryJson;

    /**
     * 创建 Query 检索审计主记录。
     *
     * @param queryId 查询标识
     * @param question 原始问题
     * @param normalizedQuestion 归一化问题
     * @param retrievalQuestion 实际检索问题
     * @param versionTag 检索版本标签
     * @param strategyTag 检索策略标签
     * @param questionTypeTag 问题类型标签
     * @param answerShape 答案形态
     * @param retrievalMode 检索模式
     * @param rewriteApplied 是否发生改写
     * @param rewriteAuditRef 改写审计引用
     * @param retrievalStrategyRef 检索策略引用
     * @param fusedHitCount 融合命中数
     * @param channelCount 启用通道数
     * @param factCardHitCount Fact Card 命中数
     * @param sourceChunkHitCount Source Chunk 命中数
     * @param coverageStatus 覆盖状态
     */
    public QueryRetrievalRunRecord(
            String queryId,
            String question,
            String normalizedQuestion,
            String retrievalQuestion,
            String versionTag,
            String strategyTag,
            String questionTypeTag,
            String answerShape,
            String retrievalMode,
            boolean rewriteApplied,
            String rewriteAuditRef,
            String retrievalStrategyRef,
            int fusedHitCount,
            int channelCount,
            int factCardHitCount,
            int sourceChunkHitCount,
            String coverageStatus
    ) {
        this(
                queryId,
                question,
                normalizedQuestion,
                retrievalQuestion,
                versionTag,
                strategyTag,
                questionTypeTag,
                answerShape,
                retrievalMode,
                rewriteApplied,
                rewriteAuditRef,
                retrievalStrategyRef,
                fusedHitCount,
                channelCount,
                factCardHitCount,
                sourceChunkHitCount,
                coverageStatus,
                "{}"
        );
    }

    /**
     * 创建 Query 检索审计主记录。
     *
     * @param queryId 查询标识
     * @param question 原始问题
     * @param normalizedQuestion 归一化问题
     * @param retrievalQuestion 实际检索问题
     * @param versionTag 检索版本标签
     * @param strategyTag 检索策略标签
     * @param questionTypeTag 问题类型标签
     * @param answerShape 答案形态
     * @param retrievalMode 检索模式
     * @param rewriteApplied 是否发生改写
     * @param rewriteAuditRef 改写审计引用
     * @param retrievalStrategyRef 检索策略引用
     * @param fusedHitCount 融合命中数
     * @param channelCount 通道数
     * @param factCardHitCount Fact Card 命中数
     * @param sourceChunkHitCount Source Chunk 命中数
     * @param coverageStatus 覆盖状态
     * @param channelRunSummaryJson 通道运行摘要 JSON
     */
    public QueryRetrievalRunRecord(
            String queryId,
            String question,
            String normalizedQuestion,
            String retrievalQuestion,
            String versionTag,
            String strategyTag,
            String questionTypeTag,
            String answerShape,
            String retrievalMode,
            boolean rewriteApplied,
            String rewriteAuditRef,
            String retrievalStrategyRef,
            int fusedHitCount,
            int channelCount,
            int factCardHitCount,
            int sourceChunkHitCount,
            String coverageStatus,
            String channelRunSummaryJson
    ) {
        this.queryId = queryId;
        this.question = question;
        this.normalizedQuestion = normalizedQuestion;
        this.retrievalQuestion = retrievalQuestion;
        this.versionTag = versionTag;
        this.strategyTag = strategyTag;
        this.questionTypeTag = questionTypeTag;
        this.answerShape = answerShape;
        this.retrievalMode = retrievalMode;
        this.rewriteApplied = rewriteApplied;
        this.rewriteAuditRef = rewriteAuditRef;
        this.retrievalStrategyRef = retrievalStrategyRef;
        this.fusedHitCount = fusedHitCount;
        this.channelCount = channelCount;
        this.factCardHitCount = factCardHitCount;
        this.sourceChunkHitCount = sourceChunkHitCount;
        this.coverageStatus = coverageStatus;
        this.channelRunSummaryJson = channelRunSummaryJson;
    }

    public String getQueryId() {
        return queryId;
    }

    public String getQuestion() {
        return question;
    }

    public String getNormalizedQuestion() {
        return normalizedQuestion;
    }

    public String getRetrievalQuestion() {
        return retrievalQuestion;
    }

    public String getVersionTag() {
        return versionTag;
    }

    public String getStrategyTag() {
        return strategyTag;
    }

    public String getQuestionTypeTag() {
        return questionTypeTag;
    }

    public String getAnswerShape() {
        return answerShape;
    }

    public String getRetrievalMode() {
        return retrievalMode;
    }

    public boolean isRewriteApplied() {
        return rewriteApplied;
    }

    public String getRewriteAuditRef() {
        return rewriteAuditRef;
    }

    public String getRetrievalStrategyRef() {
        return retrievalStrategyRef;
    }

    public int getFusedHitCount() {
        return fusedHitCount;
    }

    public int getChannelCount() {
        return channelCount;
    }

    public int getFactCardHitCount() {
        return factCardHitCount;
    }

    public int getSourceChunkHitCount() {
        return sourceChunkHitCount;
    }

    public String getCoverageStatus() {
        return coverageStatus;
    }

    public String getChannelRunSummaryJson() {
        return channelRunSummaryJson;
    }
}
