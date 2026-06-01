package com.xbk.lattice.query.evidence.domain;

/**
 * 证据锚点校验状态。
 *
 * <p>标识锚点当前的合法性/可信度状态——驱动证据平面中的锚点过滤和展示标签。
 *
 * @author xiexu
 */
public enum EvidenceAnchorValidationStatus {

    /** 原始未校验。 */
    RAW,

    /** 已通过校验。 */
    VERIFIED,

    /** 被降级（校验发现问题）。 */
    DEMOTED,

    /** 已跳过校验。 */
    SKIPPED
}
