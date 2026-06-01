package com.xbk.lattice.query.domain;

/**
 * 问答结果语义。
 *
 * <p>标识 Query 主链对最终答案的业务语义归类——决定前端展示的成功/失败/部分/无结果状态。
 *
 * @author xiexu
 */
public enum AnswerOutcome {

    /** 成功生成完整答案。 */
    SUCCESS,

    /** 证据不足，无法给出确定性答案。 */
    INSUFFICIENT_EVIDENCE,

    /** 知识库中无相关知识，无法回答。 */
    NO_RELEVANT_KNOWLEDGE,

    /** 部分回答——部分子问题可回答，部分不可。 */
    PARTIAL_ANSWER,

    /** 模型调用失败（超时、限流、错误响应等）。 */
    MODEL_FAILURE
}
