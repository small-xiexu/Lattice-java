package com.xbk.lattice.infra.persistence;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Deep Research 答案投影记录
 *
 * 职责：承载 deep_research_answer_projections 表的写入字段
 *
 * @author xiexu
 */
@Getter
@AllArgsConstructor
public class DeepResearchAnswerProjectionRecord {

    private final Long runId;

    private final Long answerAuditId;

    private final int projectionOrdinal;

    private final String anchorId;

    private final String citationLiteral;

    private final String sourceType;

    private final String targetKey;

    private final String status;

    private final int repairRound;

    private final Integer repairedFromProjectionOrdinal;
}
