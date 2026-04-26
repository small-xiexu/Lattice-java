package com.xbk.lattice.query.citation;

/**
 * Citation 校验来源
 *
 * 职责：标识 claim-level citation 校验由规则还是 LLM judge 给出
 *
 * @author xiexu
 */
public enum CitationValidationSource {

    RULE,

    LLM_JUDGE
}
