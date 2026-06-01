package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧答案反馈列表响应。
 *
 * <p>承载答案反馈队列列表和数量，由 {@code AdminQueryFeedbackController} 组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminQueryFeedbackListResponse {

    /**
     * 当前返回的反馈记录数。
     *
     * <p>等于 {@code items.size()}，受分页参数限制。</p>
     */
    private final int count;

    /**
     * 反馈记录列表。
     *
     * <p>按创建时间倒序排列。</p>
     */
    private final List<AdminQueryFeedbackResponse> items;

    /**
     * 创建管理侧答案反馈列表响应。
     *
     * @param count 数量
     * @param items 反馈列表
     */
    public AdminQueryFeedbackListResponse(int count, List<AdminQueryFeedbackResponse> items) {
        this.count = count;
        this.items = items;
    }
}
