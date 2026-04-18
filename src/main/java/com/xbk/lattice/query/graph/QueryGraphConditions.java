package com.xbk.lattice.query.graph;

import com.xbk.lattice.query.service.QueryReviewProperties;
import com.xbk.lattice.query.service.ReviewResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 问答图条件路由
 *
 * 职责：集中声明 Query Graph 的条件边判断逻辑
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class QueryGraphConditions {

    private final QueryReviewProperties queryReviewProperties;

    /**
     * 创建问答图条件路由。
     *
     * @param queryReviewProperties 查询审查配置
     */
    public QueryGraphConditions(QueryReviewProperties queryReviewProperties) {
        this.queryReviewProperties = queryReviewProperties;
    }

    /**
     * 决定缓存检查后的后续节点。
     *
     * @param state 当前图状态
     * @return 路由键
     */
    public String routeAfterCacheCheck(QueryGraphState state) {
        if (state.isCacheHit()) {
            return "finalize_response";
        }
        return "dispatch_retrieval";
    }

    /**
     * 决定融合后的后续节点。
     *
     * @param state 当前图状态
     * @return 路由键
     */
    public String routeAfterFuseCandidates(QueryGraphState state) {
        if (state.isHasFusedHits()) {
            return "answer_question";
        }
        return "finalize_response";
    }

    /**
     * 决定审查后的后续节点。
     *
     * @param state 当前图状态
     * @param reviewResult 审查结果
     * @return 路由键
     */
    public String routeAfterReview(QueryGraphState state, ReviewResult reviewResult) {
        if (reviewResult != null && reviewResult.isPass()) {
            return "cache_response";
        }
        if (queryReviewProperties.isRewriteEnabled()
                && state.getRewriteAttemptCount() < state.getMaxRewriteRounds()) {
            return "rewrite_answer";
        }
        return "finalize_response";
    }
}
