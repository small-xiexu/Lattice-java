package com.xbk.lattice.query.service;

/**
 * 查询证据类型
 * <p>
 * 职责：标识查询命中的证据来源层级
 *
 * @author xiexu
 */
public enum QueryEvidenceType {
    /**
     * 文章/稿件
     */
    ARTICLE,
    /**
     * 知识图谱
     */
    GRAPH,
    /**
     * 原始信源
     */
    SOURCE,
    /**
     * 事实卡
     */
    FACT_CARD,
    /**
     * 用户贡献
     */
    CONTRIBUTION
}
