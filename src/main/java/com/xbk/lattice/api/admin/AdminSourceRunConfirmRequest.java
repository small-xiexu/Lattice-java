package com.xbk.lattice.api.admin;

/**
 * 同步运行人工确认请求。
 *
 * <p>承载对 WAIT_CONFIRM 状态同步运行的人工确认决策。由 Spring MVC 从 JSON 请求体绑定。
 *
 * @author xiexu
 */
public class AdminSourceRunConfirmRequest {

    /**
     * 人工确认决策。
     *
     * <p>取值 ACCEPT 或 REJECT。ACCEPT 表示接受本次同步变更，继续后续流程；
     * REJECT 表示拒绝，同步运行被终止。对应 {@code SourceSyncWorkflowService} 的分支逻辑。</p>
     */
    private String decision;

    /**
     * 目标资料源主键。
     *
     * <p>对应 knowledge_sources.id，标识本次人工确认所针对的具体资料源。</p>
     */
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
