package com.xbk.lattice.infra.persistence;

/**
 * 查询答案 claim 审计记录
 *
 * 职责：承载 query_answer_claims 表的写入字段
 *
 * @author xiexu
 */
public class QueryAnswerClaimRecord {

    private final Long auditId;

    private final int claimIndex;

    private final String claimText;

    private final String claimStatus;

    private final int citationCount;

    /**
     * 创建查询答案 claim 审计记录。
     *
     * @param auditId 审计主键
     * @param claimIndex claim 序号
     * @param claimText claim 文本
     * @param claimStatus claim 状态
     * @param citationCount claim 下的引用数
     */
    public QueryAnswerClaimRecord(
            Long auditId,
            int claimIndex,
            String claimText,
            String claimStatus,
            int citationCount
    ) {
        this.auditId = auditId;
        this.claimIndex = claimIndex;
        this.claimText = claimText;
        this.claimStatus = claimStatus;
        this.citationCount = citationCount;
    }

    public Long getAuditId() {
        return auditId;
    }

    public int getClaimIndex() {
        return claimIndex;
    }

    public String getClaimText() {
        return claimText;
    }

    public String getClaimStatus() {
        return claimStatus;
    }

    public int getCitationCount() {
        return citationCount;
    }
}
