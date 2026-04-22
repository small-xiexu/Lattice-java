package com.xbk.lattice.query.domain;

import com.xbk.lattice.llm.service.PromptCacheWritePolicy;

import java.util.List;

/**
 * Reviewer 审查载荷
 *
 * 职责：承载 Query / compile reviewer 结构化输出的最小语义
 *
 * @author xiexu
 */
public class ReviewerPayload {

    private final boolean approved;

    private final boolean rewriteRequired;

    private final String riskLevel;

    private final List<ReviewIssue> issues;

    private final List<String> userFacingRewriteHints;

    private final PromptCacheWritePolicy cacheWritePolicy;

    /**
     * 创建 Reviewer 审查载荷。
     *
     * @param approved 是否通过
     * @param rewriteRequired 是否需要重写
     * @param riskLevel 风险等级
     * @param issues 审查问题
     * @param userFacingRewriteHints 用户可见的重写提示
     * @param cacheWritePolicy L1 prompt cache 写策略
     */
    public ReviewerPayload(
            boolean approved,
            boolean rewriteRequired,
            String riskLevel,
            List<ReviewIssue> issues,
            List<String> userFacingRewriteHints,
            PromptCacheWritePolicy cacheWritePolicy
    ) {
        this.approved = approved;
        this.rewriteRequired = rewriteRequired;
        this.riskLevel = riskLevel;
        this.issues = issues;
        this.userFacingRewriteHints = userFacingRewriteHints;
        this.cacheWritePolicy = cacheWritePolicy;
    }

    /**
     * 返回是否通过。
     *
     * @return 是否通过
     */
    public boolean isApproved() {
        return approved;
    }

    /**
     * 返回是否需要重写。
     *
     * @return 是否需要重写
     */
    public boolean isRewriteRequired() {
        return rewriteRequired;
    }

    /**
     * 返回风险等级。
     *
     * @return 风险等级
     */
    public String getRiskLevel() {
        return riskLevel;
    }

    /**
     * 返回审查问题列表。
     *
     * @return 审查问题列表
     */
    public List<ReviewIssue> getIssues() {
        return issues;
    }

    /**
     * 返回用户可见的重写提示。
     *
     * @return 用户可见的重写提示
     */
    public List<String> getUserFacingRewriteHints() {
        return userFacingRewriteHints;
    }

    /**
     * 返回 L1 prompt cache 写策略。
     *
     * @return L1 prompt cache 写策略
     */
    public PromptCacheWritePolicy getCacheWritePolicy() {
        return cacheWritePolicy;
    }
}
