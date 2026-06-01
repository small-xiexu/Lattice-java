package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧文章人工复核审计列表响应。
 *
 * <p>承载单篇文章的完整人工复核历史，由 {@code AdminArticleController} 组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminArticleReviewAuditListResponse {

    /**
     * 当前返回的审计记录数。
     *
     * <p>等于 {@code items.size()}。</p>
     */
    private final int count;

    /**
     * 审计记录列表。
     *
     * <p>按复核时间倒序排列，最新的操作排在最前。</p>
     */
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
}
