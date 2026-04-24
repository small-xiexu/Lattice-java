package com.xbk.lattice.query.citation;

/**
 * 答案审计快照
 *
 * 职责：承载最终答案与 Citation 审计落库后的最小快照
 *
 * @author xiexu
 */
public class QueryAnswerAuditSnapshot {

    private final Long auditId;

    private final int answerVersion;

    private final CitationCheckReport citationCheckReport;

    /**
     * 创建答案审计快照。
     *
     * @param auditId 审计主键
     * @param answerVersion 答案版本号
     * @param citationCheckReport Citation 检查报告
     */
    public QueryAnswerAuditSnapshot(Long auditId, int answerVersion, CitationCheckReport citationCheckReport) {
        this.auditId = auditId;
        this.answerVersion = answerVersion;
        this.citationCheckReport = citationCheckReport;
    }

    /**
     * 返回审计主键。
     *
     * @return 审计主键
     */
    public Long getAuditId() {
        return auditId;
    }

    /**
     * 返回答案版本号。
     *
     * @return 答案版本号
     */
    public int getAnswerVersion() {
        return answerVersion;
    }

    /**
     * 返回 Citation 检查报告。
     *
     * @return Citation 检查报告
     */
    public CitationCheckReport getCitationCheckReport() {
        return citationCheckReport;
    }
}
