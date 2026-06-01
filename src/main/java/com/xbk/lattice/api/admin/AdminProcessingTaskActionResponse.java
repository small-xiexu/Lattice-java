package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 当前处理任务动作响应。
 *
 * <p>承载后端定义的可执行动作——含按钮文案、样式和关联参数，
 * 供前端直接渲染与触发。由 {@code AdminProcessingTaskController} 组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminProcessingTaskActionResponse {

    /** 动作键（后端定义的动作标识，前端据此路由到对应处理逻辑）。 */
    private final String actionKey;

    /** 按钮展示文案。 */
    private final String label;

    /** 按钮样式类名（前端 CSS class）。 */
    private final String buttonClass;

    /** 关联运行标识。为 {@code null} 时表示无关联 run。 */
    private final Long runId;

    /** 关联资料源标识。为 {@code null} 时表示无关联 source。 */
    private final Long sourceId;

    /** 确认决策值（驱动前端确认弹窗的选项）。 */
    private final String decision;

    /** 决策目标资料源标识。为 {@code null} 时表示无特定目标。 */
    private final Long decisionSourceId;

    /** 是否为上传重试动作。 */
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
}
