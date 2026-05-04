package com.xbk.lattice.api.admin;

/**
 * 当前处理任务动作响应。
 *
 * 职责：承载后端定义的可执行动作，供前端直接渲染与触发
 *
 * @author xiexu
 */
public class AdminProcessingTaskActionResponse {

    private final String actionKey;

    private final String label;

    private final String buttonClass;

    private final Long runId;

    private final Long sourceId;

    private final String decision;

    private final Long decisionSourceId;

    private final boolean uploadRetry;

    /**
     * 创建当前处理任务动作响应。
     *
     * @param actionKey 动作键
     * @param label 按钮文案
     * @param buttonClass 按钮样式
     * @param runId 关联运行标识
     * @param sourceId 关联资料源标识
     * @param decision 确认决策
     * @param decisionSourceId 决策目标资料源标识
     * @param uploadRetry 是否重试上传
     */
    public AdminProcessingTaskActionResponse(
            String actionKey,
            String label,
            String buttonClass,
            Long runId,
            Long sourceId,
            String decision,
            Long decisionSourceId,
            boolean uploadRetry
    ) {
        this.actionKey = actionKey;
        this.label = label;
        this.buttonClass = buttonClass;
        this.runId = runId;
        this.sourceId = sourceId;
        this.decision = decision;
        this.decisionSourceId = decisionSourceId;
        this.uploadRetry = uploadRetry;
    }

    public String getActionKey() {
        return actionKey;
    }

    public String getLabel() {
        return label;
    }

    public String getButtonClass() {
        return buttonClass;
    }

    public Long getRunId() {
        return runId;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getDecision() {
        return decision;
    }

    public Long getDecisionSourceId() {
        return decisionSourceId;
    }

    public boolean isUploadRetry() {
        return uploadRetry;
    }
}
