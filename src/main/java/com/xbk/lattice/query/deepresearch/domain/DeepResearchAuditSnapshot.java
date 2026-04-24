package com.xbk.lattice.query.deepresearch.domain;

/**
 * Deep Research 审计快照
 *
 * 职责：承载一次 Deep Research 收口后的最小持久化结果
 *
 * @author xiexu
 */
public class DeepResearchAuditSnapshot {

    private final Long runId;

    private final int evidenceCardCount;

    /**
     * 创建 Deep Research 审计快照。
     *
     * @param runId 运行主键
     * @param evidenceCardCount 证据卡数量
     */
    public DeepResearchAuditSnapshot(Long runId, int evidenceCardCount) {
        this.runId = runId;
        this.evidenceCardCount = evidenceCardCount;
    }

    public Long getRunId() {
        return runId;
    }

    public int getEvidenceCardCount() {
        return evidenceCardCount;
    }
}
