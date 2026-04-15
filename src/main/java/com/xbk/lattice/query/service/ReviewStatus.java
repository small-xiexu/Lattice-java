package com.xbk.lattice.query.service;

/**
 * 审查状态
 *
 * 职责：表示单轮审查的最终状态
 *
 * @author xiexu
 */
public enum ReviewStatus {
    PASSED,
    ISSUES_FOUND,
    PARSE_RESCUED,
    PARSE_FAILED,
    TIMEOUT_FALLBACK
}
