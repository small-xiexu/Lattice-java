package com.xbk.lattice.query.domain;

/**
 * 答案生成模式
 *
 * 职责：标识答案最终来自 LLM、降级兜底还是规则拼装
 *
 * @author xiexu
 */
public enum GenerationMode {

    LLM,

    FALLBACK,

    RULE_BASED
}
