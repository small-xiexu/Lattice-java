package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 当前处理任务步骤响应。
 *
 * <p>承载后端定义的完整真实步骤链，供前端直接展示处理进度。
 * 由 {@code AdminProcessingTaskController} 组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminProcessingTaskStepResponse {

    /**
     * 步骤键。
     *
     * <p>取自 {@code AdminProcessingTaskStep} 枚举的步骤码（如 {@code resolving} / {@code syncing}）。</p>
     */
    private final String key;

    /** 步骤展示名称。 */
    private final String label;

    /**
     * 步骤状态。
     *
     * <p>取自 {@code AdminProcessingTaskStepStatus} 枚举：
     * {@code PENDING} / {@code ACTIVE} / {@code COMPLETED} / {@code FAILED}。
     * 驱动前端步骤图标的颜色和动画。</p>
     */
    private final String status;

    /** 步骤补充说明文本。可为空。 */
    private final String detail;

    /**
     * 创建当前处理任务步骤响应。
     *
     * @param key 步骤键
     * @param label 步骤名称
     * @param status 步骤状态
     * @param detail 步骤说明
     */
    public AdminProcessingTaskStepResponse(String key, String label, String status, String detail) {
        this.key = key;
        this.label = label;
        this.status = status;
        this.detail = detail;
    }
}
