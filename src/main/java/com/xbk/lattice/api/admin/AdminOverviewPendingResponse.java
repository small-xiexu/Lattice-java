package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧 pending 汇总响应。
 *
 * <p>承载 admin overview Dashboard 中的待确认查询列表，
 * 为截断摘要（非全量），完整 pending 列表见 {@link AdminPendingResponse}。
 *
 * @author xiexu
 */
@Getter
public class AdminOverviewPendingResponse {

    /**
     * 待确认查询总数。
     *
     * <p>可能大于 {@code items.size()}（Dashboard 截断展示）。</p>
     */
    private final int count;

    /**
     * 待确认查询摘要列表。
     *
     * <p>截断展示，非全量。如需完整列表应使用 pending 管理接口。</p>
     */
    private final List<AdminOverviewPendingItemResponse> items;

    /**
     * 创建管理侧 pending 汇总响应。
     *
     * @param count 待确认数量
     * @param items 条目列表
     */
    public AdminOverviewPendingResponse(int count, List<AdminOverviewPendingItemResponse> items) {
        this.count = count;
        this.items = items;
    }
}
