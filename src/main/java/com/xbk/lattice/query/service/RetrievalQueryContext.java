package com.xbk.lattice.query.service;

import com.xbk.lattice.query.evidence.domain.AnswerShape;

/**
 * 检索查询上下文
 *
 * 职责：把原始问题、归一化问题、改写结果、意图与检索策略传递给检索入口
 *
 * @author xiexu
 */
public class RetrievalQueryContext {

    private final String queryId;

    private final String originalQuestion;

    private final String normalizedQuestion;

    private final QueryRewriteResult queryRewriteResult;

    private final QueryIntent queryIntent;

    private final AnswerShape answerShape;

    private final RetrievalStrategy retrievalStrategy;

    /**
     * 创建检索查询上下文。
     *
     * @param queryId 查询标识
     * @param originalQuestion 原始问题
     * @param normalizedQuestion 归一化问题
     * @param queryRewriteResult 查询改写结果
     * @param queryIntent 查询意图
     * @param answerShape 答案形态
     * @param retrievalStrategy 检索策略
     */
    public RetrievalQueryContext(
            String queryId,
            String originalQuestion,
            String normalizedQuestion,
            QueryRewriteResult queryRewriteResult,
            QueryIntent queryIntent,
            AnswerShape answerShape,
            RetrievalStrategy retrievalStrategy
    ) {
        this.queryId = queryId;
        this.originalQuestion = originalQuestion;
        this.normalizedQuestion = normalizedQuestion;
        this.queryRewriteResult = queryRewriteResult;
        this.queryIntent = queryIntent == null ? QueryIntent.GENERAL : queryIntent;
        this.answerShape = answerShape == null ? AnswerShape.GENERAL : answerShape;
        this.retrievalStrategy = retrievalStrategy;
    }

    /**
     * 创建检索查询上下文。
     *
     * @param queryId 查询标识
     * @param originalQuestion 原始问题
     * @param normalizedQuestion 归一化问题
     * @param queryRewriteResult 查询改写结果
     * @param queryIntent 查询意图
     * @param retrievalStrategy 检索策略
     */
    public RetrievalQueryContext(
            String queryId,
            String originalQuestion,
            String normalizedQuestion,
            QueryRewriteResult queryRewriteResult,
            QueryIntent queryIntent,
            RetrievalStrategy retrievalStrategy
    ) {
        this(
                queryId,
                originalQuestion,
                normalizedQuestion,
                queryRewriteResult,
                queryIntent,
                AnswerShape.GENERAL,
                retrievalStrategy
        );
    }

    /**
     * 创建检索查询上下文。
     *
     * @param originalQuestion 原始问题
     * @param normalizedQuestion 归一化问题
     * @param queryRewriteResult 查询改写结果
     * @param queryIntent 查询意图
     * @param answerShape 答案形态
     * @param retrievalStrategy 检索策略
     */
    public RetrievalQueryContext(
            String originalQuestion,
            String normalizedQuestion,
            QueryRewriteResult queryRewriteResult,
            QueryIntent queryIntent,
            AnswerShape answerShape,
            RetrievalStrategy retrievalStrategy
    ) {
        this(null, originalQuestion, normalizedQuestion, queryRewriteResult, queryIntent, answerShape, retrievalStrategy);
    }

    /**
     * 创建检索查询上下文。
     *
     * @param originalQuestion 原始问题
     * @param normalizedQuestion 归一化问题
     * @param queryRewriteResult 查询改写结果
     * @param queryIntent 查询意图
     * @param retrievalStrategy 检索策略
     */
    public RetrievalQueryContext(
            String originalQuestion,
            String normalizedQuestion,
            QueryRewriteResult queryRewriteResult,
            QueryIntent queryIntent,
            RetrievalStrategy retrievalStrategy
    ) {
        this(null, originalQuestion, normalizedQuestion, queryRewriteResult, queryIntent, AnswerShape.GENERAL, retrievalStrategy);
    }

    /**
     * 返回查询标识。
     *
     * @return 查询标识
     */
    public String getQueryId() {
        return queryId;
    }

    /**
     * 返回原始问题。
     *
     * @return 原始问题
     */
    public String getOriginalQuestion() {
        return originalQuestion;
    }

    /**
     * 返回归一化问题。
     *
     * @return 归一化问题
     */
    public String getNormalizedQuestion() {
        return normalizedQuestion;
    }

    /**
     * 返回查询改写结果。
     *
     * @return 查询改写结果
     */
    public QueryRewriteResult getQueryRewriteResult() {
        return queryRewriteResult;
    }

    /**
     * 返回查询意图。
     *
     * @return 查询意图
     */
    public QueryIntent getQueryIntent() {
        return queryIntent;
    }

    /**
     * 返回答案形态。
     *
     * @return 答案形态
     */
    public AnswerShape getAnswerShape() {
        return answerShape;
    }

    /**
     * 返回检索策略。
     *
     * @return 检索策略
     */
    public RetrievalStrategy getRetrievalStrategy() {
        return retrievalStrategy;
    }

    /**
     * 返回有效检索问题。
     *
     * @return 有效检索问题
     */
    public String getRetrievalQuestion() {
        if (retrievalStrategy != null && retrievalStrategy.getRetrievalQuestion() != null) {
            return retrievalStrategy.getRetrievalQuestion();
        }
        if (queryRewriteResult != null && queryRewriteResult.getRewrittenQuestion() != null) {
            return queryRewriteResult.getRewrittenQuestion();
        }
        return normalizedQuestion;
    }
}
