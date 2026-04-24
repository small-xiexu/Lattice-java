package com.xbk.lattice.infra.persistence;

/**
 * 查询答案审计记录
 *
 * 职责：承载 query_answer_audits 表的最小写入字段
 *
 * @author xiexu
 */
public class QueryAnswerAuditRecord {

    private final String queryId;

    private final int answerVersion;

    private final String question;

    private final String answerMarkdown;

    private final String answerOutcome;

    private final String generationMode;

    private final String reviewStatus;

    private final double citationCoverage;

    private final int unsupportedClaimCount;

    private final int verifiedCitationCount;

    private final int demotedCitationCount;

    private final int skippedCitationCount;

    private final boolean cacheable;

    private final String routeType;

    private final String modelSnapshotJson;

    /**
     * 创建查询答案审计记录。
     *
     * @param queryId 查询标识
     * @param answerVersion 答案版本号
     * @param question 问题
     * @param answerMarkdown 答案 Markdown
     * @param answerOutcome 答案语义
     * @param generationMode 生成模式
     * @param reviewStatus 审查状态
     * @param citationCoverage 引用覆盖率
     * @param unsupportedClaimCount 不受支持 claim 数
     * @param verifiedCitationCount 已验证引用数
     * @param demotedCitationCount 已降级引用数
     * @param skippedCitationCount 已跳过引用数
     * @param cacheable 是否可缓存
     * @param routeType 路由类型
     * @param modelSnapshotJson 模型快照 JSON
     */
    public QueryAnswerAuditRecord(
            String queryId,
            int answerVersion,
            String question,
            String answerMarkdown,
            String answerOutcome,
            String generationMode,
            String reviewStatus,
            double citationCoverage,
            int unsupportedClaimCount,
            int verifiedCitationCount,
            int demotedCitationCount,
            int skippedCitationCount,
            boolean cacheable,
            String routeType,
            String modelSnapshotJson
    ) {
        this.queryId = queryId;
        this.answerVersion = answerVersion;
        this.question = question;
        this.answerMarkdown = answerMarkdown;
        this.answerOutcome = answerOutcome;
        this.generationMode = generationMode;
        this.reviewStatus = reviewStatus;
        this.citationCoverage = citationCoverage;
        this.unsupportedClaimCount = unsupportedClaimCount;
        this.verifiedCitationCount = verifiedCitationCount;
        this.demotedCitationCount = demotedCitationCount;
        this.skippedCitationCount = skippedCitationCount;
        this.cacheable = cacheable;
        this.routeType = routeType;
        this.modelSnapshotJson = modelSnapshotJson;
    }

    public String getQueryId() {
        return queryId;
    }

    public int getAnswerVersion() {
        return answerVersion;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswerMarkdown() {
        return answerMarkdown;
    }

    public String getAnswerOutcome() {
        return answerOutcome;
    }

    public String getGenerationMode() {
        return generationMode;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public double getCitationCoverage() {
        return citationCoverage;
    }

    public int getUnsupportedClaimCount() {
        return unsupportedClaimCount;
    }

    public int getVerifiedCitationCount() {
        return verifiedCitationCount;
    }

    public int getDemotedCitationCount() {
        return demotedCitationCount;
    }

    public int getSkippedCitationCount() {
        return skippedCitationCount;
    }

    public boolean isCacheable() {
        return cacheable;
    }

    public String getRouteType() {
        return routeType;
    }

    public String getModelSnapshotJson() {
        return modelSnapshotJson;
    }
}
