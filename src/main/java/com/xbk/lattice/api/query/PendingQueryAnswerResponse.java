package com.xbk.lattice.api.query;

import lombok.Getter;

/**
 * 待确认查询答案响应。
 *
 * <p>返回一条 pending query 纠错后的新答案与当前处理状态。
 * 调用方通过这个结构获取纠错后的结果并了解确认流程是否完成。
 *
 * @author xiexu
 */
@Getter
public class PendingQueryAnswerResponse {

    /**
     * 查询标识。
     *
     * <p>本次 pending query 的唯一业务 ID，调用方用它关联原始的查询请求和审计记录。</p>
     */
    private final String queryId;

    /**
     * 纠错后的答案正文。
     *
     * <p>系统根据调用方提交的纠错内容重新生成的答案。前端展示这个答案替代原始有误的回答。</p>
     */
    private final String answer;

    /**
     * 当前处理状态。
     *
     * <p>表示该 pending query 的确认流程处于哪个阶段，例如 confirmed（已确认）、
     * discarded（已丢弃）等。调用方据此判断纠错是否已生效。</p>
     */
    private final String status;

    /**
     * 创建待确认查询答案响应。
     *
     * @param queryId 查询标识
     * @param answer 答案
     * @param status 状态
     */
    public PendingQueryAnswerResponse(String queryId, String answer, String status) {
        this.queryId = queryId;
        this.answer = answer;
        this.status = status;
    }
}
