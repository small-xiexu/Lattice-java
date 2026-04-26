package com.xbk.lattice.query.evidence.domain;

/**
 * 证据锚点校验状态
 *
 * 职责：标识锚点当前的合法性/可信度状态
 *
 * @author xiexu
 */
public enum EvidenceAnchorValidationStatus {

    RAW,

    VERIFIED,

    DEMOTED,

    SKIPPED
}
