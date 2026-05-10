package com.xbk.lattice.query.evidence.domain;

/**
 * 答案形态
 * <p>
 * 职责：定义 query 期望答案采用的结构化表达方式
 *
 * @author xiexu
 */
public enum AnswerShape {

    /**
     * 枚举列举型 — 列出可选字段、类型、项目等，如"有哪些类型""包含哪些字段"
     */
    ENUM,

    /**
     * 对比差异型 — 比较两个版本/方案的异同，如"新旧版本有何差异""A vs B"
     */
    COMPARE,

    /**
     * 步骤流程型 — 按顺序说明处理步骤，如"流程有哪些步骤""请按顺序说明"
     */
    SEQUENCE,

    /**
     * 状态查询型 — 查询某事物当前所处的状态/阶段
     */
    STATUS,

    /**
     * 规则政策型 — 查询策略、规定、约束条件等规范性内容
     */
    POLICY,

    /**
     * 通用兜底型 — 无法归入上述任一形态的自由文本答案
     */
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
