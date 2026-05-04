package com.xbk.lattice.admin.service;

import java.util.Locale;

/**
 * 当前处理任务展示状态。
 *
 * 职责：集中维护工作台当前处理任务的展示状态码、文案与交互属性
 *
 * @author xiexu
 */
public enum AdminProcessingTaskDisplayStatus {

    MATCHING("MATCHING", "进行中", "warning", true, false, "success"),
    WAIT_CONFIRM("WAIT_CONFIRM", "待确认", "warning", false, true, "warning"),
    MATERIALIZING("MATERIALIZING", "进行中", "warning", true, false, "success"),
    COMPILE_QUEUED("COMPILE_QUEUED", "进行中", "warning", true, false, "success"),
    RUNNING("RUNNING", "进行中", "warning", true, false, "success"),
    STALLED("STALLED", "失败", "danger", false, false, "warning"),
    SUCCEEDED("SUCCEEDED", "已完成", "success", false, false, "success"),
    SKIPPED_NO_CHANGE("SKIPPED_NO_CHANGE", "已完成", "success", false, false, "info"),
    FAILED("FAILED", "失败", "danger", false, false, "warning"),
    QUEUED("QUEUED", "进行中", "warning", true, false, "success");

    private final String code;

    private final String label;

    private final String tone;

    private final boolean processingActive;

    private final boolean requiresManualAction;

    private final String noticeTone;

    /**
     * 创建展示状态。
     *
     * @param code 稳定状态码
     * @param label 展示文案
     * @param tone 展示色调
     * @param processingActive 是否仍在处理
     * @param requiresManualAction 是否需要人工动作
     * @param noticeTone 通知色调
     */
    AdminProcessingTaskDisplayStatus(
            String code,
            String label,
            String tone,
            boolean processingActive,
            boolean requiresManualAction,
            String noticeTone
    ) {
        this.code = code;
        this.label = label;
        this.tone = tone;
        this.processingActive = processingActive;
        this.requiresManualAction = requiresManualAction;
        this.noticeTone = noticeTone;
    }

    /**
     * 按状态码解析展示状态。
     *
     * @param value 原始状态码
     * @return 展示状态；未知时返回 null
     */
    public static AdminProcessingTaskDisplayStatus fromCode(String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue == null) {
            return null;
        }
        for (AdminProcessingTaskDisplayStatus status : values()) {
            if (status.code.equals(normalizedValue)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断原始状态码是否匹配当前状态。
     *
     * @param value 原始状态码
     * @return 是否匹配
     */
    public boolean matches(String value) {
        return code.equals(normalize(value));
    }

    /**
     * 规范化状态码。
     *
     * @param value 原始状态码
     * @return 规范化状态码
     */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 判断是否为成功完成状态。
     *
     * @param value 原始状态码
     * @return 是否成功完成
     */
    public static boolean isSucceeded(String value) {
        String normalizedValue = normalize(value);
        return SUCCEEDED.code.equals(normalizedValue) || SKIPPED_NO_CHANGE.code.equals(normalizedValue);
    }

    /**
     * 判断是否为失败状态。
     *
     * @param value 原始状态码
     * @return 是否失败
     */
    public static boolean isFailed(String value) {
        String normalizedValue = normalize(value);
        return FAILED.code.equals(normalizedValue) || STALLED.code.equals(normalizedValue);
    }

    /**
     * 判断是否为运行中状态。
     *
     * @param value 原始状态码
     * @return 是否运行中
     */
    public static boolean isRunningLike(String value) {
        AdminProcessingTaskDisplayStatus status = fromCode(value);
        return status != null && status.processingActive;
    }

    /**
     * 获取稳定状态码。
     *
     * @return 稳定状态码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取展示文案。
     *
     * @return 展示文案
     */
    public String getLabel() {
        return label;
    }

    /**
     * 获取展示色调。
     *
     * @return 展示色调
     */
    public String getTone() {
        return tone;
    }

    /**
     * 判断是否仍在处理。
     *
     * @return 是否仍在处理
     */
    public boolean isProcessingActive() {
        return processingActive;
    }

    /**
     * 判断是否需要人工处理。
     *
     * @return 是否需要人工处理
     */
    public boolean isRequiresManualAction() {
        return requiresManualAction;
    }

    /**
     * 获取通知色调。
     *
     * @return 通知色调
     */
    public String getNoticeTone() {
        return noticeTone;
    }
}
