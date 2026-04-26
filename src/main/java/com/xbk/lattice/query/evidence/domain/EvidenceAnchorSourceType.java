package com.xbk.lattice.query.evidence.domain;

/**
 * 证据锚点来源类型
 *
 * 职责：定义 v2.6 证据平面中锚点的合法来源类型
 *
 * @author xiexu
 */
public enum EvidenceAnchorSourceType {

    ARTICLE,

    SOURCE_FILE,

    GRAPH_FACT,

    CONTRIBUTION
}
