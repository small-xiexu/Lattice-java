package com.xbk.lattice.admin.service;

/**
 * 当前处理任务步骤状态。
 *
 * 职责：集中维护进度条步骤状态码
 *
 * @author xiexu
 */
public enum AdminProcessingTaskStepStatus {

    PENDING("PENDING"),
    ACTIVE("ACTIVE"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED");

    private final String code;

    /**
     * 创建步骤状态。
     *
     * @param code 稳定状态码
     */
    AdminProcessingTaskStepStatus(String code) {
        this.code = code;
    }

    /**
     * 获取稳定状态码。
     *
     * @return 稳定状态码
     */
    public String getCode() {
        return code;
    }
}
