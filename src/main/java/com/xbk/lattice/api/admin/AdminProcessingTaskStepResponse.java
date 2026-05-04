package com.xbk.lattice.api.admin;

/**
 * 当前处理任务步骤响应。
 *
 * 职责：承载后端定义的完整真实步骤链，供前端直接展示
 *
 * @author xiexu
 */
public class AdminProcessingTaskStepResponse {

    private final String key;

    private final String label;

    private final String status;

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

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public String getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }
}
