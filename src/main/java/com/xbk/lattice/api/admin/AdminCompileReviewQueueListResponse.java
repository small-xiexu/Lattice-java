package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧编译审查人工确认队列列表响应。
 *
 * <p>承载队列列表及受分页限制的总数，由 {@code AdminCompileReviewQueueController} 组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminCompileReviewQueueListResponse {

    /**
     * 当前返回的队列条目数。
     *
     * <p>受 {@code limit} 参数限制，不等于数据库中的队列总数。</p>
     */
    private final int total;

    /**
     * 队列条目列表。
     *
     * <p>按创建时间排序。</p>
     */
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
}
