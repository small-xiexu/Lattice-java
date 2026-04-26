package com.xbk.lattice.infra.persistence;

/**
 * 查询答案引用审计记录
 *
 * 职责：承载 query_answer_citations 表的写入字段
 *
 * @author xiexu
 */
public class QueryAnswerCitationRecord {

    private final Long auditId;

    private final Long claimId;

    private final int citationOrdinal;

    private final String literal;

    private final String sourceType;

    private final String targetKey;

    private final String status;

    private final String validatedBy;

    private final double overlapScore;

    private final String matchedExcerpt;

    private final String reason;

    /**
     * 创建查询答案引用审计记录。
     *
     * @param auditId 审计主键
     * @param claimId claim 主键
     * @param citationOrdinal 引用序号
     * @param literal 原始引用文本
     * @param sourceType 来源类型
     * @param targetKey 目标键
     * @param status 校验状态
     * @param validatedBy 校验来源
     * @param overlapScore 重叠分
     * @param matchedExcerpt 命中摘录
     * @param reason 原因
     */
    public QueryAnswerCitationRecord(
            Long auditId,
            Long claimId,
            int citationOrdinal,
            String literal,
            String sourceType,
            String targetKey,
            String status,
            String validatedBy,
            double overlapScore,
            String matchedExcerpt,
            String reason
    ) {
        this.auditId = auditId;
        this.claimId = claimId;
        this.citationOrdinal = citationOrdinal;
        this.literal = literal;
        this.sourceType = sourceType;
        this.targetKey = targetKey;
        this.status = status;
        this.validatedBy = validatedBy;
        this.overlapScore = overlapScore;
        this.matchedExcerpt = matchedExcerpt;
        this.reason = reason;
    }

    public Long getAuditId() {
        return auditId;
    }

    public Long getClaimId() {
        return claimId;
    }

    public int getCitationOrdinal() {
        return citationOrdinal;
    }

    public String getLiteral() {
        return literal;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getTargetKey() {
        return targetKey;
    }

    public String getStatus() {
        return status;
    }

    public String getValidatedBy() {
        return validatedBy;
    }

    public double getOverlapScore() {
        return overlapScore;
    }

    public String getMatchedExcerpt() {
        return matchedExcerpt;
    }

    public String getReason() {
        return reason;
    }
}
