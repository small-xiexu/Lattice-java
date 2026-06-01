package com.xbk.lattice.source.domain;

import lombok.Getter;

import java.util.List;

/**
 * 资料源分页结果。
 *
 * <p>承载后台资料源列表的分页查询结果。
 *
 * @author xiexu
 */
@Getter
public class KnowledgeSourcePage {

    /** 当前页码（1-based）。 */
    private final int page;
    /** 每页大小。 */
    private final int size;
    /** 符合条件的总记录数。 */
    private final long total;
    /** 当前页资料源列表。 */
    private final List<KnowledgeSource> items;

    public KnowledgeSourcePage(int page, int size, long total, List<KnowledgeSource> items) {
        this.page = page;
        this.size = size;
        this.total = total;
        this.items = items;
    }
}
