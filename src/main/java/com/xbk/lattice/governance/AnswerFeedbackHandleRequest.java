package com.xbk.lattice.governance;

/**
 * 答案反馈处理请求
 *
 * 职责：承载结果反馈队列处理动作入参
 *
 * @author xiexu
 */
public class AnswerFeedbackHandleRequest {

    private final String handledBy;

    private final String comment;

    /**
     * 创建答案反馈处理请求。
     *
     * @param handledBy 处理人
     * @param comment 处理说明
     */
    public AnswerFeedbackHandleRequest(String handledBy, String comment) {
        this.handledBy = handledBy;
        this.comment = comment;
    }

    /**
     * 获取处理人。
     *
     * @return 处理人
     */
    public String getHandledBy() {
        return handledBy;
    }

    /**
     * 获取处理说明。
     *
     * @return 处理说明
     */
    public String getComment() {
        return comment;
    }
}
