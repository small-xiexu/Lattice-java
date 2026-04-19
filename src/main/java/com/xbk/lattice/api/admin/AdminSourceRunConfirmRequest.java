package com.xbk.lattice.api.admin;

/**
 * 同步运行人工确认请求。
 *
 * 职责：承载 WAIT_CONFIRM 运行的人工确认决策与目标资料源
 *
 * @author xiexu
 */
public class AdminSourceRunConfirmRequest {

    private String decision;

    private Long sourceId;

    /**
     * 获取人工确认决策。
     *
     * @return 人工确认决策
     */
    public String getDecision() {
        return decision;
    }

    /**
     * 设置人工确认决策。
     *
     * @param decision 人工确认决策
     */
    public void setDecision(String decision) {
        this.decision = decision;
    }

    /**
     * 获取目标资料源主键。
     *
     * @return 目标资料源主键
     */
    public Long getSourceId() {
        return sourceId;
    }

    /**
     * 设置目标资料源主键。
     *
     * @param sourceId 目标资料源主键
     */
    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }
}
