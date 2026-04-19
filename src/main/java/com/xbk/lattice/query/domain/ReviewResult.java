package com.xbk.lattice.query.domain;

import java.util.List;

/**
 * 审查结果
 *
 * 职责：表示单轮审查的结论、状态与问题集合
 *
 * @author xiexu
 */
public class ReviewResult {

    private final boolean pass;

    private final ReviewStatus status;

    private final List<ReviewIssue> issues;

    /**
     * 创建审查结果。
     *
     * @param pass 是否通过
     * @param status 审查状态
     * @param issues 审查问题
     */
    public ReviewResult(boolean pass, ReviewStatus status, List<ReviewIssue> issues) {
        this.pass = pass;
        this.status = status;
        this.issues = issues;
    }

    /**
     * 创建通过结果。
     *
     * @return 通过结果
     */
    public static ReviewResult passed() {
        return new ReviewResult(true, ReviewStatus.PASSED, List.of());
    }

    /**
     * 创建发现问题的结果。
     *
     * @param issues 审查问题
     * @return 审查结果
     */
    public static ReviewResult issuesFound(List<ReviewIssue> issues) {
        return new ReviewResult(false, ReviewStatus.ISSUES_FOUND, issues);
    }

    /**
     * 创建解析救援结果。
     *
     * @param issues 审查问题
     * @return 审查结果
     */
    public static ReviewResult parseRescued(List<ReviewIssue> issues) {
        return new ReviewResult(false, ReviewStatus.PARSE_RESCUED, issues);
    }

    /**
     * 创建解析失败后的乐观通过结果。
     *
     * @return 审查结果
     */
    public static ReviewResult parseFailed() {
        return new ReviewResult(true, ReviewStatus.PARSE_FAILED, List.of());
    }

    /**
     * 创建超时兜底结果。
     *
     * @return 审查结果
     */
    public static ReviewResult timeoutFallback() {
        return new ReviewResult(true, ReviewStatus.TIMEOUT_FALLBACK, List.of());
    }

    /**
     * 是否通过。
     *
     * @return 是否通过
     */
    public boolean isPass() {
        return pass;
    }

    /**
     * 获取审查状态。
     *
     * @return 审查状态
     */
    public ReviewStatus getStatus() {
        return status;
    }

    /**
     * 获取审查问题。
     *
     * @return 审查问题
     */
    public List<ReviewIssue> getIssues() {
        return issues;
    }
}
