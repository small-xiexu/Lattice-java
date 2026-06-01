package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧文章列表响应。
 *
 * <p>承载管理侧文章浏览列表，由 {@code AdminArticleController} 组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminArticleListResponse {

    /**
     * 当前返回的文章数。
     *
     * <p>等于 {@code items.size()}，受分页参数限制，不等于数据库总数。</p>
     */
    private final int count;

    /**
     * 文章摘要列表。
     *
     * <p>按入库时间倒序排列。</p>
     */
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
}
