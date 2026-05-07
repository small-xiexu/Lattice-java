package com.xbk.lattice.query.evidence.domain;

/**
 * 事实证据卡类型
 *
 * 职责：定义结构化证据卡的通用事实形态
 *
 * @author xiexu
 */
public enum FactCardType {

    FACT_ENUM,

    FACT_COMPARE,

    FACT_SEQUENCE,

    FACT_STATUS,

    FACT_POLICY;

    /**
     * 按数据库值解析证据卡类型。
     *
     * @param value 数据库值
     * @return 证据卡类型
     */
    public static FactCardType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("FactCardType value must not be blank");
        }
        return FactCardType.valueOf(value.trim().toUpperCase());
    }
}
