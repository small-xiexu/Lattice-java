package com.xbk.lattice.source.domain;

import java.util.List;

/**
 * 资料源分页结果。
 *
 * 职责：承载后台资料源列表的分页查询结果
 *
 * @author xiexu
 */
public class KnowledgeSourcePage {

    private final int page;

    private final int size;

    private final long total;

    private final List<KnowledgeSource> items;

    /**
     * 创建资料源分页结果。
     *
     * @param page 当前页码
     * @param size 分页大小
     * @param total 总数
     * @param items 当前页数据
     */
    public KnowledgeSourcePage(int page, int size, long total, List<KnowledgeSource> items) {
        this.page = page;
        this.size = size;
        this.total = total;
        this.items = items;
    }

    /**
     * 返回当前页码。
     *
     * @return 当前页码
     */
    public int getPage() {
        return page;
    }

    /**
     * 返回分页大小。
     *
     * @return 分页大小
     */
    public int getSize() {
        return size;
    }

    /**
     * 返回总记录数。
     *
     * @return 总记录数
     */
    public long getTotal() {
        return total;
    }

    /**
     * 返回当前页数据。
     *
     * @return 当前页资料源列表
     */
    public List<KnowledgeSource> getItems() {
        return items;
    }
}
