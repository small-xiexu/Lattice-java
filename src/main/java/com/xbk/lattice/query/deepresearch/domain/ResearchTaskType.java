package com.xbk.lattice.query.deepresearch.domain;

/**
 * 研究任务类型。
 *
 * <p>标识 Deep Research Planner 输出的任务意图——决定检索策略、证据抽取方式和综合方法。
 *
 * @author xiexu
 */
public enum ResearchTaskType {

    /** 事实查找——从知识库检索具体的事实/数据点。 */
    FACT_LOOKUP,

    /** 对比分析——比较不同实体/方案/时间点的差异。 */
    COMPARE,

    /** 因果推理——探究问题的根因或影响链。 */
    CAUSE,

    /** 规则政策——检索和解释约束性规则、政策或规范。 */
    POLICY,

    /** 综合归纳——整合多层研究结果生成整体结论。 */
    SYNTHESIS
}
