package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧答案反馈详情响应。
 *
 * <p>承载单条答案反馈的完整详情与处理审计历史，
 * 由 {@code AdminQueryFeedbackController} 组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminQueryFeedbackDetailResponse {

    /**
     * 反馈详情。
     *
     * <p>含用户问题、答案、反馈内容、处理状态与审计信息的完整快照。</p>
     */
    private final AdminQueryFeedbackResponse feedback;

    /**
     * 处理审计历史列表。
     *
     * <p>按操作时间倒序排列，最新的操作排在最前。</p>
     */
    private final List<AdminQueryFeedbackAuditResponse> audits;

    /**
     * 创建管理侧答案反馈详情响应。
     *
     * @param feedback 答案反馈
     * @param audits 审计历史
     */
    public AdminQueryFeedbackDetailResponse(
            AdminQueryFeedbackResponse feedback,
            List<AdminQueryFeedbackAuditResponse> audits
    ) {
        this.feedback = feedback;
        this.audits = audits;
    }
}
