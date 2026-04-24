package com.xbk.lattice.query.graph;

import com.xbk.lattice.query.citation.CitationCheckOptions;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.service.QueryReviewProperties;
import com.xbk.lattice.query.domain.ReviewResult;
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

    private static final CitationCheckOptions CITATION_CHECK_OPTIONS = CitationCheckOptions.defaults();

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
        if (queryReviewProperties.isRewriteEnabled()
                && (reviewResult == null || !reviewResult.isPass())
                && state.getRewriteAttemptCount() < state.getMaxRewriteRounds()) {
            return "rewrite_answer";
        }
        return "claim_segment";
    }

    /**
     * 决定 Citation 检查后的后续节点。
     *
     * @param state 当前图状态
     * @param report Citation 检查报告
     * @return 路由键
     */
    public String routeAfterCitationCheck(QueryGraphState state, CitationCheckReport report) {
        if (report == null) {
            return "persist_response";
        }
        if (report.isNoCitation()) {
            return "persist_response";
        }
        if (report.getDemotedCount() > 0 || report.getCoverageRate() < CITATION_CHECK_OPTIONS.getMinCitationCoverage()) {
            if (state.getCitationRepairAttemptCount() < CITATION_CHECK_OPTIONS.getMaxRepairRounds()) {
                return "citation_repair";
            }
        }
        return "persist_response";
    }
}
