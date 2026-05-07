package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧答案反馈列表响应
 *
 * 职责：承载答案反馈队列列表和数量
 *
 * @author xiexu
 */
public class AdminQueryFeedbackListResponse {

    private final int count;

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

    /**
     * 获取数量。
     *
     * @return 数量
     */
    public int getCount() {
        return count;
    }

    /**
     * 获取反馈列表。
     *
     * @return 反馈列表
     */
    public List<AdminQueryFeedbackResponse> getItems() {
        return items;
    }
}
