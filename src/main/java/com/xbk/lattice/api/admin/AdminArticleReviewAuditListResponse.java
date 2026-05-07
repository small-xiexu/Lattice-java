package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧文章人工复核审计列表响应
 *
 * 职责：承载单篇文章的人工复核历史
 *
 * @author xiexu
 */
public class AdminArticleReviewAuditListResponse {

    private final int count;

    private final List<AdminArticleReviewAuditResponse> items;

    /**
     * 创建管理侧文章人工复核审计列表响应。
     *
     * @param count 审计数量
     * @param items 审计列表
     */
    public AdminArticleReviewAuditListResponse(int count, List<AdminArticleReviewAuditResponse> items) {
        this.count = count;
        this.items = items;
    }

    /**
     * 获取审计数量。
     *
     * @return 审计数量
     */
    public int getCount() {
        return count;
    }

    /**
     * 获取审计列表。
     *
     * @return 审计列表
     */
    public List<AdminArticleReviewAuditResponse> getItems() {
        return items;
    }
}
