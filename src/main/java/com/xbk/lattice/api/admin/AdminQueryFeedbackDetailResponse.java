package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 管理侧答案反馈详情响应
 *
 * 职责：承载单条答案反馈与处理审计历史
 *
 * @author xiexu
 */
public class AdminQueryFeedbackDetailResponse {

    private final AdminQueryFeedbackResponse feedback;

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

    /**
     * 获取答案反馈。
     *
     * @return 答案反馈
     */
    public AdminQueryFeedbackResponse getFeedback() {
        return feedback;
    }

    /**
     * 获取审计历史。
     *
     * @return 审计历史
     */
    public List<AdminQueryFeedbackAuditResponse> getAudits() {
        return audits;
    }
}
