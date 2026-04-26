package com.xbk.lattice.infra.persistence;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * Deep Research 任务记录
 *
 * 职责：承载 deep_research_tasks 表的写入字段
 *
 * @author xiexu
 */
@Getter
@AllArgsConstructor
public class DeepResearchTaskRecord {

    private final Long runId;

    private final String taskId;

    private final int layerIndex;

    private final String taskType;

    private final String question;

    private final String expectedFactSchemaJson;

    private final String preferredUpstreamTaskIdsJson;

    private final String status;

    private final int llmCallCount;

    private final boolean timedOut;

    private final String errorReason;

    private final OffsetDateTime startedAt;

    private final OffsetDateTime finishedAt;
}
