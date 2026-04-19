package com.xbk.lattice.compiler.graph;

import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.query.domain.ReviewIssue;
import com.xbk.lattice.query.domain.ReviewResult;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReviewDecisionPolicy 测试
 *
 * 职责：验证审查分区与失败子集回环决策契约
 *
 * @author xiexu
 */
class ReviewDecisionPolicyTests {

    private final ReviewDecisionPolicy reviewDecisionPolicy = new ReviewDecisionPolicy();

    /**
     * 验证自动修复可用时，只把失败子集送入 fix_review_issues。
     */
    @Test
    void shouldPartitionAcceptedAndFixableArticlesWhenAutoFixIsAvailable() {
        CompileGraphState state = new CompileGraphState();
        state.setAutoFixEnabled(true);
        state.setFixAttemptCount(0);
        state.setMaxFixRounds(1);

        ReviewPartition reviewPartition = reviewDecisionPolicy.partition(
                state,
                List.of(
                        createEnvelope("passed-article", ReviewResult.passed()),
                        createEnvelope("fixable-article", ReviewResult.issuesFound(List.of(
                                new ReviewIssue("HIGH", "GROUNDING", "缺少 grounding 证据")
                        )))
                )
        );

        assertThat(reviewPartition.getAccepted()).hasSize(1);
        assertThat(reviewPartition.getAccepted().get(0).getArticle().getConceptId()).isEqualTo("passed-article");
        assertThat(reviewPartition.getFixable()).hasSize(1);
        assertThat(reviewPartition.getFixable().get(0).getArticle().getConceptId()).isEqualTo("fixable-article");
        assertThat(reviewPartition.getNeedsHumanReview()).isEmpty();
        assertThat(reviewDecisionPolicy.decide(state, reviewPartition)).isEqualTo("fix_review_issues");
    }

    /**
     * 验证自动修复已关闭或耗尽时，失败文章会归入 needs_human_review 并直接收口到持久化阶段。
     */
    @Test
    void shouldSendFailedArticlesToHumanReviewWhenAutoFixIsExhausted() {
        CompileGraphState state = new CompileGraphState();
        state.setAutoFixEnabled(true);
        state.setFixAttemptCount(1);
        state.setMaxFixRounds(1);

        ReviewPartition reviewPartition = reviewDecisionPolicy.partition(
                state,
                List.of(createEnvelope("needs-human-review", ReviewResult.issuesFound(List.of(
                        new ReviewIssue("HIGH", "WEAK_ANSWER", "仍需人工确认")
                ))))
        );

        assertThat(reviewPartition.getAccepted()).isEmpty();
        assertThat(reviewPartition.getFixable()).isEmpty();
        assertThat(reviewPartition.getNeedsHumanReview()).hasSize(1);
        assertThat(reviewPartition.getNeedsHumanReview().get(0).getArticle().getConceptId()).isEqualTo("needs-human-review");
        assertThat(reviewDecisionPolicy.decide(state, reviewPartition)).isEqualTo("persist_articles");
    }

    private ArticleReviewEnvelope createEnvelope(String conceptId, ReviewResult reviewResult) {
        ArticleReviewEnvelope articleReviewEnvelope = new ArticleReviewEnvelope();
        articleReviewEnvelope.setArticle(new ArticleRecord(
                conceptId,
                conceptId,
                "# " + conceptId,
                "active",
                OffsetDateTime.now()
        ));
        articleReviewEnvelope.setReviewResult(reviewResult);
        return articleReviewEnvelope;
    }
}
