package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧编译人工确认队列列表响应
 *
 * 职责：承载队列列表及总数
 *
 * @author xiexu
 */
public class AdminCompileReviewQueueListResponse {

    private final int total;

    private final List<AdminCompileReviewQueueItemResponse> items;

    /**
     * 创建管理侧编译人工确认队列列表响应。
     *
     * @param total 总数
     * @param items 条目集合
     */
    public AdminCompileReviewQueueListResponse(
            int total,
            List<AdminCompileReviewQueueItemResponse> items
    ) {
        this.total = total;
        this.items = items;
    }

    /**
     * 获取总数。
     *
     * @return 总数
     */
    public int getTotal() {
        return total;
    }

    /**
     * 获取条目集合。
     *
     * @return 条目集合
     */
    public List<AdminCompileReviewQueueItemResponse> getItems() {
        return items;
    }
}
