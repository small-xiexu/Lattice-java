package com.xbk.lattice.query.deepresearch.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * 研究任务召回命中。
 *
 * <p>记录单个 Deep Research 任务在检索阶段获得的原始证据命中——含多通道排序分数和内容摘录。
 *
 * @author xiexu
 */
@Getter
@Setter
public class ResearchTaskHit {

    /** 命中序号。 */
    private int hitOrdinal;
    /** 来源通道名称。 */
    private String channel;
    /** 证据类型（article / source_file / fact_card）。 */
    private String evidenceType;
    /** 来源实体 ID。 */
    private String sourceId;
    /** 文章唯一键。 */
    private String articleKey;
    /** 概念标识。 */
    private String conceptId;
    /** 文章标题。 */
    private String title;
    /** chunk 标识。 */
    private String chunkId;
    /** 来源路径。 */
    private String path;
    /** 原始通道分数。 */
    private Double originalScore;
    /** RRF 融合分数。 */
    private Double rrfScore;
    /** 最终融合分数。 */
    private Double fusedScore;
    /**
     * 检索内容摘录。
     *
     * <p>可能为较长文本片段，不应进入 toString()。</p>
     */
    private String contentExcerpt;
}
