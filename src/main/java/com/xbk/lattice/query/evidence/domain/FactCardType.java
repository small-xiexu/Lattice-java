package com.xbk.lattice.query.evidence.domain;

/**
 * 事实证据卡类型。
 *
 * <p>定义结构化证据卡的通用事实形态——决定卡片的展示布局和回答呈现方式。
 *
 * @author xiexu
 */
public enum FactCardType {

    /** 枚举列举型（列出可选字段、类型等）。 */
    FACT_ENUM,

    /** 对比差异型（比较版本/方案的异同）。 */
    FACT_COMPARE,

    /** 步骤流程型（按顺序说明处理步骤）。 */
    FACT_SEQUENCE,

    /** 状态查询型（查询当前状态/阶段）。 */
    FACT_STATUS,

    /** 规则政策型（查询策略、约束等规范性内容）。 */
    FACT_POLICY;

    public static FactCardType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("FactCardType value must not be blank");
        }
        return FactCardType.valueOf(value.trim().toUpperCase());
    }
}
