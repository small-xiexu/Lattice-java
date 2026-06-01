package com.xbk.lattice.query.evidence.domain;

/**
 * 事实值类型。
 *
 * <p>标识 FactFinding 的值语义——供综合与校验阶段按类型区分处理（比较、范围匹配等）。
 *
 * @author xiexu
 */
public enum FactValueType {

    /** 数值类型。 */
    NUMBER,

    /** 布尔类型。 */
    BOOLEAN,

    /** 字符串类型。 */
    STRING,

    /** 枚举类型。 */
    ENUM,

    /** 范围类型。 */
    RANGE
}
