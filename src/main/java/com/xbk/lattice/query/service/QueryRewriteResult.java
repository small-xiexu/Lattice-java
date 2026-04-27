package com.xbk.lattice.query.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询改写结果
 *
 * 职责：承载 Query Preparation 阶段的原问题、改写问题与命中规则
 *
 * @author xiexu
 */
public class QueryRewriteResult {

    private final String originalQuestion;

    private final String rewrittenQuestion;

    private final List<String> matchedRuleCodes;

    private final boolean rewriteApplied;

    private final String auditRef;

    /**
     * 创建查询改写结果。
     *
     * @param originalQuestion 原始问题
     * @param rewrittenQuestion 改写后问题
     * @param matchedRuleCodes 命中规则编码
     * @param rewriteApplied 是否发生改写
     * @param auditRef 改写审计引用
     */
    public QueryRewriteResult(
            String originalQuestion,
            String rewrittenQuestion,
            List<String> matchedRuleCodes,
            boolean rewriteApplied,
            String auditRef
    ) {
        this.originalQuestion = originalQuestion;
        this.rewrittenQuestion = rewrittenQuestion;
        this.matchedRuleCodes = matchedRuleCodes == null ? List.of() : new ArrayList<String>(matchedRuleCodes);
        this.rewriteApplied = rewriteApplied;
        this.auditRef = auditRef;
    }

    /**
     * 创建未改写结果。
     *
     * @param question 查询问题
     * @return 未改写结果
     */
    public static QueryRewriteResult unchanged(String question) {
        return new QueryRewriteResult(question, question, List.of(), false, null);
    }

    /**
     * 绑定审计引用。
     *
     * @param newAuditRef 审计引用
     * @return 新结果
     */
    public QueryRewriteResult withAuditRef(String newAuditRef) {
        return new QueryRewriteResult(
                originalQuestion,
                rewrittenQuestion,
                matchedRuleCodes,
                rewriteApplied,
                newAuditRef
        );
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
     * 返回改写后问题。
     *
     * @return 改写后问题
     */
    public String getRewrittenQuestion() {
        return rewrittenQuestion;
    }

    /**
     * 返回命中规则编码。
     *
     * @return 命中规则编码
     */
    public List<String> getMatchedRuleCodes() {
        return new ArrayList<String>(matchedRuleCodes);
    }

    /**
     * 返回是否发生改写。
     *
     * @return 是否发生改写
     */
    public boolean isRewriteApplied() {
        return rewriteApplied;
    }

    /**
     * 返回改写审计引用。
     *
     * @return 改写审计引用
     */
    public String getAuditRef() {
        return auditRef;
    }
}
