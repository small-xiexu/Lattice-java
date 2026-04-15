package com.xbk.lattice.api.query;

/**
 * 待确认查询答案响应
 *
 * 职责：返回纠错后的答案与当前 pending 状态
 *
 * @author xiexu
 */
public class PendingQueryAnswerResponse {

    private final String queryId;

    private final String answer;

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

    /**
     * 获取查询标识。
     *
     * @return 查询标识
     */
    public String getQueryId() {
        return queryId;
    }

    /**
     * 获取答案。
     *
     * @return 答案
     */
    public String getAnswer() {
        return answer;
    }

    /**
     * 获取状态。
     *
     * @return 状态
     */
    public String getStatus() {
        return status;
    }
}
