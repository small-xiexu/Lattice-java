package com.xbk.lattice.api.admin;

/**
 * 管理侧答案反馈处理请求
 *
 * 职责：承载反馈队列处理动作入参
 *
 * @author xiexu
 */
public class AdminQueryFeedbackHandleRequest {

    private String handledBy;

    private String comment;

    /**
     * 获取处理人。
     *
     * @return 处理人
     */
    public String getHandledBy() {
        return handledBy;
    }

    /**
     * 设置处理人。
     *
     * @param handledBy 处理人
     */
    public void setHandledBy(String handledBy) {
        this.handledBy = handledBy;
    }

    /**
     * 获取处理说明。
     *
     * @return 处理说明
     */
    public String getComment() {
        return comment;
    }

    /**
     * 设置处理说明。
     *
     * @param comment 处理说明
     */
    public void setComment(String comment) {
        this.comment = comment;
    }
}
