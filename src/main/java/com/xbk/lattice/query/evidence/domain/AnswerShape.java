package com.xbk.lattice.query.evidence.domain;

/**
 * 答案形态
 *
 * 职责：定义 query 期望答案采用的结构化表达方式
 *
 * @author xiexu
 */
public enum AnswerShape {

    ENUM,

    COMPARE,

    SEQUENCE,

    STATUS,

    POLICY,

    GENERAL;

    /**
     * 按数据库值解析答案形态。
     *
     * @param value 数据库值
     * @return 答案形态
     */
    public static AnswerShape fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AnswerShape value must not be blank");
        }
        return AnswerShape.valueOf(value.trim().toUpperCase());
    }
}
