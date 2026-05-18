package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.InMemoryCompileWorkingSetStore;
import com.xbk.lattice.compiler.graph.ReviewDecisionPolicy;
import com.xbk.lattice.compiler.graph.ReviewPartition;
import com.xbk.lattice.compiler.service.ArticleCompileSupport;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.query.domain.ReviewIssue;
import com.xbk.lattice.query.domain.ReviewResult;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReviewArticlesNode 审查模式测试。
 *
 * 职责：验证 StateGraph 审查节点在每一轮 Reviewer 都保留 job 作用域
 *
 * @author xiexu
 */
class ReviewArticlesNodeReviewModeTests {

    /**
     * 验证默认草稿审查会携带 job 作用域进入 Reviewer。
     */
    @Test
    void shouldReviewDraftArticlesWithJobScope() {
        RecordingArticleCompileSupport articleCompileSupport = new RecordingArticleCompileSupport(ReviewResult.passed());
        InMemoryCompileWorkingSetStore workingSetStore = new InMemoryCompileWorkingSetStore();
        ReviewArticlesNode reviewArticlesNode = createNode(workingSetStore, articleCompileSupport);
        CompileGraphState state = baseState("job-rule-default");
        state.setDraftArticlesRef(workingSetStore.saveDraftArticles(state.getJobId(), List.of(article("draft-rule"))));

        Map<String, Object> stateDelta = reviewArticlesNode.execute(new OverAllState(
                new CompileGraphStateMapper().toMap(state)
        ));

        assertThat(articleCompileSupport.getObservedScopeIds()).containsExactly("job-rule-default");
        CompileGraphState updatedState = new CompileGraphStateMapper().fromMap(stateDelta);
        assertThat(updatedState.getAcceptedCount()).isEqualTo(1);
    }

    /**
     * 验证 Fixer 回到 Reviewer 后仍携带同一个 job 作用域。
     */
    @Test
    void shouldReviewFixedArticlesWithSameJobScopeAfterFixerRound() {
        ReviewIssue reviewIssue = new ReviewIssue("HIGH", "GROUNDING", "仍需补证据");
        RecordingArticleCompileSupport articleCompileSupport = new RecordingArticleCompileSupport(
                ReviewResult.issuesFound(List.of(reviewIssue))
        );
        InMemoryCompileWorkingSetStore workingSetStore = new InMemoryCompileWorkingSetStore();
        ReviewArticlesNode reviewArticlesNode = createNode(workingSetStore, articleCompileSupport);
        ArticleReviewEnvelope fixedEnvelope = new ArticleReviewEnvelope();
        fixedEnvelope.setArticle(article("fixed-article"));
        fixedEnvelope.setReviewResult(ReviewResult.issuesFound(List.of(reviewIssue)));
        fixedEnvelope.setReviewStatus("pending");
        CompileGraphState state = baseState("job-llm-fixed");
        state.setFixAttemptCount(1);
        state.setMaxFixRounds(1);
        state.setReviewedArticlesRef(workingSetStore.saveReviewedArticles(
                state.getJobId(),
                List.of(fixedEnvelope)
        ));

        Map<String, Object> stateDelta = reviewArticlesNode.execute(new OverAllState(
                new CompileGraphStateMapper().toMap(state)
        ));

        assertThat(articleCompileSupport.getObservedScopeIds()).containsExactly("job-llm-fixed");
        CompileGraphState updatedState = new CompileGraphStateMapper().fromMap(stateDelta);
        ReviewPartition reviewPartition = workingSetStore.loadReviewPartition(updatedState.getReviewPartitionRef());
        assertThat(reviewPartition.getNeedsHumanReview()).hasSize(1);
        assertThat(reviewPartition.getAccepted()).isEmpty();
    }

    private ReviewArticlesNode createNode(
            InMemoryCompileWorkingSetStore workingSetStore,
            ArticleCompileSupport articleCompileSupport
    ) {
        return new ReviewArticlesNode(
                new CompileGraphStateMapper(),
                workingSetStore,
                articleCompileSupport,
                new ReviewDecisionPolicy()
        );
    }

    private CompileGraphState baseState(String jobId) {
        CompileGraphState state = new CompileGraphState();
        state.setJobId(jobId);
        state.setAutoFixEnabled(true);
        state.setMaxFixRounds(1);
        return state;
    }

    private ArticleRecord article(String conceptId) {
        return new ArticleRecord(
                conceptId,
                conceptId,
                "# " + conceptId,
                "ACTIVE",
                OffsetDateTime.now(),
                List.of("docs/source.md"),
                "{}",
                "",
                List.of(),
                List.of(),
                List.of(),
                "medium",
                "pending"
        );
    }

    /**
     * 记录 reviewMode 的文章编译支撑替身。
     *
     * @author xiexu
     */
    private static class RecordingArticleCompileSupport extends ArticleCompileSupport {

        private final ReviewResult reviewResult;

        private final List<String> observedScopeIds = new ArrayList<String>();

        /**
         * 创建记录型支撑替身。
         *
         * @param reviewResult 审查结果
         */
        private RecordingArticleCompileSupport(ReviewResult reviewResult) {
            super(null, null, null, null, null);
            this.reviewResult = reviewResult;
        }

        /**
         * 记录 job 作用域并返回固定审查结果。
         *
         * @param draftArticles 草稿文章集合
         * @param scopeId 作用域标识
         * @param scene 场景
         * @return 审查结果集合
         */
        @Override
        public List<ArticleReviewEnvelope> reviewDraftArticles(
                List<ArticleRecord> draftArticles,
                String scopeId,
                String scene
        ) {
            observedScopeIds.add(scopeId);
            List<ArticleReviewEnvelope> envelopes = new ArrayList<ArticleReviewEnvelope>();
            for (ArticleRecord draftArticle : draftArticles) {
                ArticleReviewEnvelope envelope = new ArticleReviewEnvelope();
                envelope.setArticle(draftArticle);
                envelope.setReviewResult(reviewResult);
                envelope.setReviewStatus(reviewResult.isPass() ? "passed" : "pending");
                envelopes.add(envelope);
            }
            return envelopes;
        }

        /**
         * 返回观测到的 job 作用域。
         *
         * @return job 作用域集合
         */
        private List<String> getObservedScopeIds() {
            return observedScopeIds;
        }
    }
}
