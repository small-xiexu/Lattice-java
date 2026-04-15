package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧 pending 汇总响应
 *
 * 职责：承载 admin overview 中的待确认查询列表
 *
 * @author xiexu
 */
public class AdminOverviewPendingResponse {

    private final int count;

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

    /**
     * 获取待确认数量。
     *
     * @return 待确认数量
     */
    public int getCount() {
        return count;
    }

    /**
     * 获取条目列表。
     *
     * @return 条目列表
     */
    public List<AdminOverviewPendingItemResponse> getItems() {
        return items;
    }
}
