package com.xbk.lattice.query.domain;

/**
 * 审查状态。
 *
 * <p>表示单轮审查的最终状态——驱动后续 auto-fix 或人工复核流程。
 *
 * @author xiexu
 */
public enum ReviewStatus {

    /** 审查通过，无问题。 */
    PASSED,

    /** 发现问题（需 auto-fix 或人工复核）。 */
    ISSUES_FOUND,

    /** JSON 解析失败但通过启发式修复恢复。 */
    PARSE_RESCUED,

    /** JSON 解析失败且无法恢复。 */
    PARSE_FAILED,

    /** 审查调用超时，使用乐观兜底。 */
    TIMEOUT_FALLBACK
}
