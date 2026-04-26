package com.xbk.lattice.infra.persistence;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Deep Research 证据锚点校验记录
 *
 * 职责：承载 deep_research_evidence_anchor_validations 表的写入字段
 *
 * @author xiexu
 */
@Getter
@AllArgsConstructor
public class DeepResearchEvidenceAnchorValidationRecord {

    private final Long runId;

    private final String anchorId;

    private final int validationRound;

    private final String validationStatus;

    private final String validatedBy;

    private final String reason;

    private final String matchedExcerpt;
}
