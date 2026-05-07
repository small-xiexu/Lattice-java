package com.xbk.lattice.query.evidence.domain;

/**
 * 事实证据卡审查状态
 *
 * 职责：定义证据卡在 query 使用前的质量门禁状态
 *
 * @author xiexu
 */
public enum FactCardReviewStatus {

    VALID("valid"),

    INCOMPLETE("incomplete"),

    CONFLICT("conflict"),

    LOW_CONFIDENCE("low_confidence"),

    NEEDS_HUMAN_REVIEW("needs_human_review");

    private final String databaseValue;

    /**
     * 创建事实证据卡审查状态。
     *
     * @param databaseValue 数据库存储值
     */
    FactCardReviewStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    /**
     * 获取数据库存储值。
     *
     * @return 数据库存储值
     */
    public String databaseValue() {
        return databaseValue;
    }

    /**
     * 按数据库值解析审查状态。
     *
     * @param value 数据库值
     * @return 审查状态
     */
    public static FactCardReviewStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("FactCardReviewStatus value must not be blank");
        }
        for (FactCardReviewStatus status : values()) {
            if (status.databaseValue.equals(value.trim())) {
                return status;
            }
        }
        return FactCardReviewStatus.valueOf(value.trim().toUpperCase());
    }
}
