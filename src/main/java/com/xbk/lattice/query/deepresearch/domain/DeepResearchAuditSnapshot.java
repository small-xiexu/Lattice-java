package com.xbk.lattice.query.deepresearch.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Deep Research 审计快照。
 *
 * <p>承载一次 Deep Research 收口后的最小持久化结果——用于运行记录追溯和审计。
 *
 * @author xiexu
 */
@Getter
public class DeepResearchAuditSnapshot {

    /** 运行主键。 */
    private final Long runId;
    /** 本次运行产生的证据卡数量。 */
    private final int evidenceCardCount;

    @JsonCreator
    public DeepResearchAuditSnapshot(
            @JsonProperty("runId") Long runId,
            @JsonProperty("evidenceCardCount") int evidenceCardCount
    ) {
        this.runId = runId;
        this.evidenceCardCount = evidenceCardCount;
    }
}
