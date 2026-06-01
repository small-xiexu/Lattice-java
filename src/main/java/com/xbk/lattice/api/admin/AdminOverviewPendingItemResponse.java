package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 管理侧 pending 条目响应。
 *
 * <p>承载 admin overview Dashboard 中单条待确认查询的摘要信息。
 * 禁止引入 {@code @Data}：{@code question} 为用户查询内容。
 *
 * @author xiexu
 */
@Getter
public class AdminOverviewPendingItemResponse {

    /** 查询会话标识。 */
    private final String queryId;

    /**
     * 用户原始问题文本。
     *
     * <p>用于 Dashboard 快速预览，可能含 PII。禁止参与 {@code toString()}。</p>
     */
    private final String question;

    /**
     * 审查状态。
     *
     * <p>驱动前端展示待处理标签颜色和操作入口。
     * 如 {@code needs_human_review} / {@code pending_review}。</p>
     */
    private final String reviewStatus;

    /**
     * 创建管理侧 pending 条目响应。
     *
     * @param queryId 查询标识
     * @param question 问题
     * @param reviewStatus 审查状态
     */
    public AdminOverviewPendingItemResponse(String queryId, String question, String reviewStatus) {
        this.queryId = queryId;
        this.question = question;
        this.reviewStatus = reviewStatus;
    }
}
