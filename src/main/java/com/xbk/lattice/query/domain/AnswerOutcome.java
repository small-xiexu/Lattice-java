package com.xbk.lattice.query.domain;

/**
 * 问答结果语义
 *
 * 职责：标识 Query 主链对最终答案的业务语义归类
 *
 * @author xiexu
 */
public enum AnswerOutcome {

    SUCCESS,

    INSUFFICIENT_EVIDENCE,

    NO_RELEVANT_KNOWLEDGE,

    PARTIAL_ANSWER,

    MODEL_FAILURE
}
