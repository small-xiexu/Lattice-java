package com.xbk.lattice.query.citation;

/**
 * Citation 校验状态
 *
 * 职责：表示单条引用在规则校验后的结果
 *
 * @author xiexu
 */
public enum CitationValidationStatus {
    VERIFIED,
    DEMOTED,
    SKIPPED,
    NOT_FOUND
}
