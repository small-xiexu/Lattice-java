package com.xbk.lattice.query.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * 审查结果。
 *
 * <p>表示单轮审查的结论、状态与问题集合——通过 5 个 static factory 编码审查状态机语义。
 *
 * @author xiexu
 */
@Getter
public class ReviewResult {

    /** 是否通过。 */
    private final boolean pass;
    /** 审查状态。 */
    private final ReviewStatus status;
    /** 审查问题列表。 */
    private final List<ReviewIssue> issues;

    @JsonCreator
    public ReviewResult(
            @JsonProperty("pass") boolean pass,
            @JsonProperty("status") ReviewStatus status,
            @JsonProperty("issues") List<ReviewIssue> issues
    ) {
        this.pass = pass;
        this.status = status;
        this.issues = issues;
    }

    public static ReviewResult passed() { return new ReviewResult(true, ReviewStatus.PASSED, List.of()); }
    public static ReviewResult issuesFound(List<ReviewIssue> issues) { return new ReviewResult(false, ReviewStatus.ISSUES_FOUND, issues); }
    public static ReviewResult parseRescued(List<ReviewIssue> issues) { return new ReviewResult(false, ReviewStatus.PARSE_RESCUED, issues); }
    public static ReviewResult parseFailed() { return new ReviewResult(false, ReviewStatus.PARSE_FAILED, List.of()); }
    public static ReviewResult timeoutFallback() { return new ReviewResult(false, ReviewStatus.TIMEOUT_FALLBACK, List.of()); }
}
