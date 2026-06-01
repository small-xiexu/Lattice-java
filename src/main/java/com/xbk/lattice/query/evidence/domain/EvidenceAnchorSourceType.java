package com.xbk.lattice.query.evidence.domain;

/**
 * 证据锚点来源类型。
 *
 * <p>定义 v2.6 证据平面中锚点的合法来源类型——决定锚点关联的实体类别。
 *
 * @author xiexu
 */
public enum EvidenceAnchorSourceType {

    /** 文章来源。 */
    ARTICLE,

    /** 源文件来源。 */
    SOURCE_FILE,

    /** 图谱事实来源。 */
    GRAPH_FACT,

    /** 贡献度来源。 */
    CONTRIBUTION
}
