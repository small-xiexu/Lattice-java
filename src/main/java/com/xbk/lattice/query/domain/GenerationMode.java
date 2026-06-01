package com.xbk.lattice.query.domain;

/**
 * 答案生成模式。
 *
 * <p>标识答案最终来自 LLM、降级兜底还是规则拼装——影响答案缓存策略和质量评估。
 *
 * @author xiexu
 */
public enum GenerationMode {

    /** LLM 直接生成。 */
    LLM,

    /** 降级兜底（LLM 不可用或返回不可用结果时使用备用策略）。 */
    FALLBACK,

    /** 规则拼装（无 LLM 参与，由检索结果直接格式化）。 */
    RULE_BASED
}
