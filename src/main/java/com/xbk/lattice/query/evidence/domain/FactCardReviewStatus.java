package com.xbk.lattice.query.evidence.domain;

/**
 * 事实证据卡审查状态。
 *
 * <p>定义证据卡在 query 使用前的质量门禁状态——各值对应数据库中的存储值。
 *
 * @author xiexu
 */
public enum FactCardReviewStatus {

    /** 有效（数据库值: valid）。 */
    VALID("valid"),

    /** 不完整（数据库值: incomplete）。 */
    INCOMPLETE("incomplete"),

    /** 存在冲突（数据库值: conflict）。 */
    CONFLICT("conflict"),

    /** 低置信度（数据库值: low_confidence）。 */
    LOW_CONFIDENCE("low_confidence"),

    /** 需人工复核（数据库值: needs_human_review）。 */
    NEEDS_HUMAN_REVIEW("needs_human_review");

    private final String databaseValue;

    FactCardReviewStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

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
