package com.xbk.lattice.query.deepresearch.domain;

import lombok.Data;

/**
 * 研究任务召回命中
 *
 * 职责：记录单个 Deep Research 任务在检索阶段获得的原始证据命中
 *
 * @author xiexu
 */
@Data
public class ResearchTaskHit {

    private int hitOrdinal;

    private String channel;

    private String evidenceType;

    private String sourceId;

    private String articleKey;

    private String conceptId;

    private String title;

    private String chunkId;

    private String path;

    private Double originalScore;

    private Double rrfScore;

    private Double fusedScore;

    private String contentExcerpt;
}
