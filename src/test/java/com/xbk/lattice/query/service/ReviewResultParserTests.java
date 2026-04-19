package com.xbk.lattice.query.service;

import com.xbk.lattice.query.domain.ReviewResult;
import com.xbk.lattice.query.domain.ReviewStatus;
import org.junit.jupiter.api.Test;

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
                {"pass":true,"issues":[]}
                """);

        assertThat(reviewResult.isPass()).isTrue();
        assertThat(reviewResult.getStatus()).isEqualTo(ReviewStatus.PASSED);
        assertThat(reviewResult.getIssues()).isEmpty();
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
