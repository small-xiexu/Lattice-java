package com.xbk.lattice.query.evidence.domain;

/**
 * 投影引用格式。
 *
 * <p>定义允许进入最终出站 citation 模型的引用格式——决定引用的目标实体类型。
 *
 * @author xiexu
 */
public enum ProjectionCitationFormat {

    /** 文章引用格式。 */
    ARTICLE,

    /** 源文件引用格式。 */
    SOURCE_FILE
}
