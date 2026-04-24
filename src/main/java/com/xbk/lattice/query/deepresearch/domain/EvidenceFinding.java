package com.xbk.lattice.query.deepresearch.domain;

import lombok.Data;

/**
 * 证据发现项
 *
 * 职责：表示研究员从 grounding 中抽出的单条结构化发现
 *
 * @author xiexu
 */
@Data
public class EvidenceFinding {

    private String claim;

    private String quote;

    private String sourceType;

    private String sourceId;

    private String chunkId;

    private double confidence;
}
