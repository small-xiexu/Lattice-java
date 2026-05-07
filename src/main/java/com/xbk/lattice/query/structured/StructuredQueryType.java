package com.xbk.lattice.query.structured;

/**
 * 结构化查询类型
 *
 * 职责：描述当前结构化查询计划的执行形态
 *
 * @author xiexu
 */
public enum StructuredQueryType {

    ROW_LOOKUP,

    COUNT,

    GROUP_BY,

    ROW_COMPARE
}
