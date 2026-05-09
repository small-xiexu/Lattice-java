package com.xbk.lattice.query.service;

/**
 * 精确查值题偏向 deterministic fallback 的通用原因。
 *
 * 职责：标识模型答案为什么没有被直接保留为 LLM 成功结果
 *
 * @author xiexu
 */
enum ExactLookupPreferenceReason {

    /**
     * 不需要 deterministic fallback。
     */
    NONE,

    /**
     * 模型答案语义不是成功。
     */
    OUTCOME_NOT_SUCCESS,

    /**
     * 模型答案包含过度保守表达。
     */
    OVERCAUTIOUS_PHRASE,

    /**
     * 模型答案未覆盖贴题证据形态。
     */
    GROUNDING_MISMATCH
}
