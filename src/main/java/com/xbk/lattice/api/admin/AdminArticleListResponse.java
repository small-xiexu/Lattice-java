package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧文章列表响应
 *
 * 职责：承载管理侧文章浏览列表
 *
 * @author xiexu
 */
public class AdminArticleListResponse {

    private final int count;

    private final List<AdminArticleSummaryResponse> items;

    /**
     * 创建管理侧文章列表响应。
     *
     * @param count 文章数量
     * @param items 条目列表
     */
    public AdminArticleListResponse(int count, List<AdminArticleSummaryResponse> items) {
        this.count = count;
        this.items = items;
    }

    /**
     * 获取文章数量。
     *
     * @return 文章数量
     */
    public int getCount() {
        return count;
    }

    /**
     * 获取条目列表。
     *
     * @return 条目列表
     */
    public List<AdminArticleSummaryResponse> getItems() {
        return items;
    }
}
