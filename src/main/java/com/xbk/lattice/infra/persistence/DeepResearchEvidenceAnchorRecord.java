package com.xbk.lattice.infra.persistence;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Deep Research 证据锚点记录
 *
 * 职责：承载 deep_research_evidence_anchors 表的写入字段
 *
 * @author xiexu
 */
@Getter
@AllArgsConstructor
public class DeepResearchEvidenceAnchorRecord {

    private final Long runId;

    private final String anchorId;

    private final String taskId;

    private final String sourceType;

    private final String sourceId;

    private final String chunkId;

    private final String path;

    private final Integer lineStart;

    private final Integer lineEnd;

    private final String quoteText;

    private final Double retrievalScore;

    private final String contentHash;
}
