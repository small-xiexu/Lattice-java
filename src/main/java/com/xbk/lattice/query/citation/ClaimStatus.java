package com.xbk.lattice.query.citation;

/**
 * Claim 状态
 *
 * 职责：标识答案片段在引用核验后的收敛状态
 *
 * @author xiexu
 */
public enum ClaimStatus {
    VERIFIED,
    PARTIAL,
    UNSUPPORTED,
    NO_CITATION
}
