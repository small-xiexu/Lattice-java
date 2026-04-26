package com.xbk.lattice.infra.persistence;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Deep Research finding 记录
 *
 * 职责：承载 deep_research_findings 表的写入字段
 *
 * @author xiexu
 */
@Getter
@AllArgsConstructor
public class DeepResearchFindingRecord {

    private final Long runId;

    private final String findingId;

    private final String taskId;

    private final String factKey;

    private final String subject;

    private final String predicate;

    private final String valueText;

    private final String valueType;

    private final String unit;

    private final String qualifier;

    private final String claimText;

    private final String supportLevel;

    private final double confidence;

    private final String anchorIdsJson;
}
