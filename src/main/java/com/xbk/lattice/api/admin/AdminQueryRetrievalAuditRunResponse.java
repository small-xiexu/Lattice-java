package com.xbk.lattice.api.admin;

/**
 * 管理侧 Query 检索审计 run 响应
 *
 * 职责：承载一次检索 run 的摘要信息
 *
 * @author xiexu
 */
public class AdminQueryRetrievalAuditRunResponse {

    private final Long runId;

    private final String queryId;

    private final String question;

    private final String normalizedQuestion;

    private final String retrievalQuestion;

    private final String versionTag;

    private final String strategyTag;

    private final String questionTypeTag;

    private final String retrievalMode;

    private final boolean rewriteApplied;

    private final String rewriteAuditRef;

    private final String retrievalStrategyRef;

    private final int fusedHitCount;

    private final int channelCount;

    private final String createdAt;

    /**
     * 创建管理侧 Query 检索审计 run 响应。
     *
     * @param runId 主键
     * @param queryId 查询标识
     * @param question 原始问题
     * @param normalizedQuestion 归一化问题
     * @param retrievalQuestion 实际检索问题
     * @param versionTag 版本标签
     * @param strategyTag 策略标签
     * @param questionTypeTag 问题类型标签
     * @param retrievalMode 检索模式
     * @param rewriteApplied 是否改写
     * @param rewriteAuditRef 改写审计引用
     * @param retrievalStrategyRef 检索策略引用
     * @param fusedHitCount 融合命中数
     * @param channelCount 通道数
     * @param createdAt 创建时间
     */
    public AdminQueryRetrievalAuditRunResponse(
            Long runId,
            String queryId,
            String question,
            String normalizedQuestion,
            String retrievalQuestion,
            String versionTag,
            String strategyTag,
            String questionTypeTag,
            String retrievalMode,
            boolean rewriteApplied,
            String rewriteAuditRef,
            String retrievalStrategyRef,
            int fusedHitCount,
            int channelCount,
            String createdAt
    ) {
        this.runId = runId;
        this.queryId = queryId;
        this.question = question;
        this.normalizedQuestion = normalizedQuestion;
        this.retrievalQuestion = retrievalQuestion;
        this.versionTag = versionTag;
        this.strategyTag = strategyTag;
        this.questionTypeTag = questionTypeTag;
        this.retrievalMode = retrievalMode;
        this.rewriteApplied = rewriteApplied;
        this.rewriteAuditRef = rewriteAuditRef;
        this.retrievalStrategyRef = retrievalStrategyRef;
        this.fusedHitCount = fusedHitCount;
        this.channelCount = channelCount;
        this.createdAt = createdAt;
    }

    /**
     * 获取主键。
     *
     * @return 主键
     */
    public Long getRunId() {
        return runId;
    }

    /**
     * 获取查询标识。
     *
     * @return 查询标识
     */
    public String getQueryId() {
        return queryId;
    }

    /**
     * 获取原始问题。
     *
     * @return 原始问题
     */
    public String getQuestion() {
        return question;
    }

    /**
     * 获取归一化问题。
     *
     * @return 归一化问题
     */
    public String getNormalizedQuestion() {
        return normalizedQuestion;
    }

    /**
     * 获取实际检索问题。
     *
     * @return 实际检索问题
     */
    public String getRetrievalQuestion() {
        return retrievalQuestion;
    }

    /**
     * 获取版本标签。
     *
     * @return 版本标签
     */
    public String getVersionTag() {
        return versionTag;
    }

    /**
     * 获取策略标签。
     *
     * @return 策略标签
     */
    public String getStrategyTag() {
        return strategyTag;
    }

    /**
     * 获取问题类型标签。
     *
     * @return 问题类型标签
     */
    public String getQuestionTypeTag() {
        return questionTypeTag;
    }

    /**
     * 获取检索模式。
     *
     * @return 检索模式
     */
    public String getRetrievalMode() {
        return retrievalMode;
    }

    /**
     * 获取是否改写。
     *
     * @return 是否改写
     */
    public boolean isRewriteApplied() {
        return rewriteApplied;
    }

    /**
     * 获取改写审计引用。
     *
     * @return 改写审计引用
     */
    public String getRewriteAuditRef() {
        return rewriteAuditRef;
    }

    /**
     * 获取检索策略引用。
     *
     * @return 检索策略引用
     */
    public String getRetrievalStrategyRef() {
        return retrievalStrategyRef;
    }

    /**
     * 获取融合命中数。
     *
     * @return 融合命中数
     */
    public int getFusedHitCount() {
        return fusedHitCount;
    }

    /**
     * 获取通道数。
     *
     * @return 通道数
     */
    public int getChannelCount() {
        return channelCount;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public String getCreatedAt() {
        return createdAt;
    }
}
