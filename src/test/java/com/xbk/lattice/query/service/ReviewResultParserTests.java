package com.xbk.lattice.query.service;

import com.xbk.lattice.llm.service.PromptCacheWritePolicy;
import com.xbk.lattice.query.domain.ReviewIssue;
import com.xbk.lattice.query.domain.ReviewResult;
import com.xbk.lattice.query.domain.ReviewStatus;
import com.xbk.lattice.query.domain.ReviewerPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReviewResultParser 测试
 *
 * 职责：验证审查结果的标准解析与降级解析行为
 *
 * @author xiexu
 */
class ReviewResultParserTests {

    /**
     * 验证标准 JSON 审查结果可被正常解析。
     */
    @Test
    void shouldParseStructuredReviewJson() {
        ReviewResultParser reviewResultParser = new ReviewResultParser();

        ReviewResult reviewResult = reviewResultParser.parse("""
                {"approved":true,"rewriteRequired":false,"riskLevel":"LOW","issues":[],"userFacingRewriteHints":[],"cacheWritePolicy":"WRITE"}
                """);

        assertThat(reviewResult.isPass()).isTrue();
        assertThat(reviewResult.getStatus()).isEqualTo(ReviewStatus.PASSED);
        assertThat(reviewResult.getIssues()).isEmpty();
    }

    /**
     * 验证 typed review payload 可兼容解析为 ReviewResult。
     */
    @Test
    void shouldParseTypedReviewPayload() {
        ReviewResultParser reviewResultParser = new ReviewResultParser();

        ReviewResult reviewResult = reviewResultParser.parse("""
                {
                  "approved": false,
                  "rewriteRequired": true,
                  "riskLevel": "HIGH",
                  "issues": [
                    {
                      "severity": "HIGH",
                      "category": "GROUNDING",
                      "description": "答案缺少来源定位"
                    }
                  ],
                  "userFacingRewriteHints": [
                    "补充 payment/analyze.json 的来源标注"
                  ],
                  "cacheWritePolicy": "SKIP_WRITE"
                }
                """);

        assertThat(reviewResult.isPass()).isFalse();
        assertThat(reviewResult.getStatus()).isEqualTo(ReviewStatus.ISSUES_FOUND);
        assertThat(reviewResult.getIssues()).extracting(ReviewIssue::getCategory).containsExactly("GROUNDING");
        assertThat(reviewResult.getIssues()).extracting(ReviewIssue::getDescription).containsExactly("答案缺少来源定位");
    }

    /**
     * 验证 typed review payload 即使没有 issues，也会从 rewrite hint 收敛出最小问题。
     */
    @Test
    void shouldCreateIssueFromRewriteHintsWhenTypedPayloadHasNoIssues() {
        ReviewResultParser reviewResultParser = new ReviewResultParser();

        ReviewResult reviewResult = reviewResultParser.parse("""
                {
                  "approved": false,
                  "rewriteRequired": true,
                  "riskLevel": "MEDIUM",
                  "issues": [],
                  "userFacingRewriteHints": [
                    "补充订单服务为什么需要异步解耦的证据"
                  ],
                  "cacheWritePolicy": "SKIP_WRITE"
                }
                """);

        assertThat(reviewResult.isPass()).isFalse();
        assertThat(reviewResult.getStatus()).isEqualTo(ReviewStatus.ISSUES_FOUND);
        assertThat(reviewResult.getIssues()).hasSize(1);
        assertThat(reviewResult.getIssues()).extracting(ReviewIssue::getCategory).containsExactly("REWRITE_REQUIRED");
        assertThat(reviewResult.getIssues()).extracting(ReviewIssue::getDescription)
                .containsExactly("补充订单服务为什么需要异步解耦的证据");
    }

    /**
     * 验证 parser 可直接提取统一 reviewer payload 与 cache policy。
     */
    @Test
    void shouldParseReviewerPayloadAndResolveCachePolicy() {
        ReviewResultParser reviewResultParser = new ReviewResultParser();

        ReviewerPayload reviewerPayload = reviewResultParser.parsePayload("""
                {
                  "approved": true,
                  "rewriteRequired": false,
                  "riskLevel": "LOW",
                  "issues": [],
                  "userFacingRewriteHints": [],
                  "cacheWritePolicy": "WRITE"
                }
                """);

        assertThat(reviewerPayload).isNotNull();
        assertThat(reviewerPayload.isApproved()).isTrue();
        assertThat(reviewerPayload.getCacheWritePolicy()).isEqualTo(PromptCacheWritePolicy.WRITE);
        assertThat(reviewResultParser.resolvePromptCacheWritePolicy("""
                {
                  "approved": false,
                  "rewriteRequired": true,
                  "riskLevel": "HIGH",
                  "issues": [],
                  "userFacingRewriteHints": ["请补齐来源"],
                  "cacheWritePolicy": "SKIP_WRITE"
                }
                """)).isEqualTo(PromptCacheWritePolicy.SKIP_WRITE);
    }

    /**
     * 验证格式错误的文本审查结果仍可通过关键词救援出 issue。
     */
    @Test
    void shouldRescueIssuesFromMalformedReviewText() {
        ReviewResultParser reviewResultParser = new ReviewResultParser();

        ReviewResult reviewResult = reviewResultParser.parse("""
                审查结论：
                Issue：缺少明确来源标注，答案里写了 retry=3，但没有指出来自 payment/analyze.json
                """);

        assertThat(reviewResult.isPass()).isFalse();
        assertThat(reviewResult.getStatus()).isEqualTo(ReviewStatus.PARSE_RESCUED);
        assertThat(reviewResult.getIssues()).hasSize(1);
        assertThat(reviewResult.getIssues().get(0).getCategory()).isEqualTo("PARSE_RESCUED");
    }
}
