package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧 pending 列表响应
 *
 * 职责：承载管理侧 pending 管理列表
 *
 * @author xiexu
 */
public class AdminPendingResponse {

    private final int count;

    private final List<AdminPendingItemResponse> items;

    /**
     * 创建管理侧 pending 列表响应。
     *
     * @param count 数量
     * @param items 条目
     */
    public AdminPendingResponse(int count, List<AdminPendingItemResponse> items) {
        this.count = count;
        this.items = items;
    }

    /**
     * 获取数量。
     *
     * @return 数量
     */
    public int getCount() {
        return count;
    }

    /**
     * 获取条目。
     *
     * @return 条目
     */
    public List<AdminPendingItemResponse> getItems() {
        return items;
    }
}
