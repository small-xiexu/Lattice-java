package com.xbk.lattice.query.evidence.domain;

/**
 * 事实值类型
 *
 * 职责：标识 FactFinding 的值语义，供综合与校验阶段区分处理
 *
 * @author xiexu
 */
public enum FactValueType {

    NUMBER,

    BOOLEAN,

    STRING,

    ENUM,

    RANGE
}
