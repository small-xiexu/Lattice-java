package com.xbk.lattice.api.query;

/**
 * 待确认查询状态响应
 *
 * 职责：返回 confirm/discard 的执行状态
 *
 * @author xiexu
 */
public class PendingQueryStatusResponse {

    private final String status;

    /**
     * 创建待确认查询状态响应。
     *
     * @param status 状态
     */
    public PendingQueryStatusResponse(String status) {
        this.status = status;
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
