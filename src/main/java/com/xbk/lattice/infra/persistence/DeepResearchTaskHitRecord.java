package com.xbk.lattice.infra.persistence;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Deep Research 任务命中记录
 *
 * 职责：承载 deep_research_task_hits 表的写入字段
 *
 * @author xiexu
 */
@Getter
@AllArgsConstructor
public class DeepResearchTaskHitRecord {

    private final Long runId;

    private final String taskId;

    private final int hitOrdinal;

    private final String channel;

    private final String evidenceType;

    private final String sourceId;

    private final String articleKey;

    private final String conceptId;

    private final String title;

    private final String chunkId;

    private final String path;

    private final Double originalScore;

    private final Double rrfScore;

    private final Double fusedScore;

    private final String contentExcerpt;
}
