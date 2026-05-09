package com.xbk.lattice.query.service;

/**
 * 精确查值题答案 grounding 判定状态。
 *
 * 职责：给通用 grounding 保护提供可观测的失败分类
 *
 * @author xiexu
 */
enum ExactLookupGroundingStatus {

    /**
     * 答案覆盖了当前问题所需的贴题证据形态。
     */
    GROUNDED,

    /**
     * 缺少必要路径形态。
     */
    MISSING_PATH_SHAPE,

    /**
     * 缺少数字。
     */
    MISSING_DIGIT,

    /**
     * 缺少必要数值形态。
     */
    MISSING_NUMERIC_SHAPE,

    /**
     * 缺少批次或序号形态。
     */
    MISSING_BATCH_OR_ORDINAL,

    /**
     * 缺少状态形态。
     */
    MISSING_STATUS,

    /**
     * 缺少流程形态。
     */
    MISSING_FLOW,

    /**
     * 缺少修正或状态结论形态。
     */
    MISSING_CORRECTION_OR_STATUS,

    /**
     * 缺少强约束形态。
     */
    MISSING_STRONG_CONSTRAINT,

    /**
     * 缺少规则约束形态。
     */
    MISSING_RULE_CONSTRAINT,

    /**
     * 缺少变更跟踪形态。
     */
    MISSING_CHANGE_TRACKING,

    /**
     * 缺少复合精确题的多维证据覆盖。
     */
    MISSING_COMPOUND_DIMENSIONS
}
