package com.xbk.lattice.query.service;

/**
 * 答案覆盖状态
 *
 * 职责：描述结构化证据卡要点在最终答案中的覆盖程度
 *
 * @author xiexu
 */
public enum AnswerCoverageStatus {

    NOT_APPLICABLE("not_applicable"),

    COVERED("covered"),

    PARTIAL("partial"),

    MISSING("missing");

    private final String value;

    /**
     * 创建答案覆盖状态。
     *
     * @param value 外部展示值
     */
    AnswerCoverageStatus(String value) {
        this.value = value;
    }

    /**
     * 获取外部展示值。
     *
     * @return 外部展示值
     */
    public String value() {
        return value;
    }

    /**
     * 按外部展示值解析答案覆盖状态。
     *
     * @param value 外部展示值
     * @return 答案覆盖状态
     */
    public static AnswerCoverageStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AnswerCoverageStatus value must not be blank");
        }
        String normalizedValue = value.trim();
        for (AnswerCoverageStatus status : values()) {
            if (status.value.equals(normalizedValue)) {
                return status;
            }
        }
        return AnswerCoverageStatus.valueOf(normalizedValue.toUpperCase());
    }
}
