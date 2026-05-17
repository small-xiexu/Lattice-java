package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.InMemoryCompileWorkingSetStore;
import com.xbk.lattice.compiler.service.ArticlePersistSupport;
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
 * PersistArticlesNode 测试
 *
 * 职责：验证正式落库门禁只允许审查通过文章进入 query-facing persist 链路
 *
 * @author xiexu
 */
class PersistArticlesNodeTests {

    /**
     * 验证允许落库人工复核配置开启时，正式 persist 仍只接收 passed 文章。
     */
    @Test
    void shouldPersistOnlyPassedArticlesWhenAcceptedRefContainsMixedReviewStatuses() {
        CompileGraphStateMapper stateMapper = new CompileGraphStateMapper();
        InMemoryCompileWorkingSetStore workingSetStore = new InMemoryCompileWorkingSetStore();
        RecordingArticlePersistSupport articlePersistSupport = new RecordingArticlePersistSupport();
        PersistArticlesNode persistArticlesNode = new PersistArticlesNode(
                stateMapper,
                workingSetStore,
                articlePersistSupport
        );
        ArticleReviewEnvelope passedArticle = reviewEnvelope("approved-concept", "passed", ReviewResult.passed());
        ReviewIssue reviewIssue = new ReviewIssue("HIGH", "EVIDENCE", "requires manual review");
        ReviewResult needsHumanReviewResult = ReviewResult.issuesFound(List.of(reviewIssue));
        ArticleReviewEnvelope needsHumanReviewArticle = reviewEnvelope(
                "review-needed-concept",
                "needs_human_review",
                needsHumanReviewResult
        );
        String jobId = "job-persist-gate";
        List<ArticleReviewEnvelope> acceptedArticles = List.of(passedArticle, needsHumanReviewArticle);
        String acceptedArticlesRef = workingSetStore.saveAcceptedArticles(jobId, acceptedArticles);
        String needsHumanReviewArticlesRef = workingSetStore.saveNeedsHumanReviewArticles(
                jobId,
                List.of(needsHumanReviewArticle)
        );
        CompileGraphState state = new CompileGraphState();
        state.setJobId(jobId);
        state.setAcceptedArticlesRef(acceptedArticlesRef);
        state.setNeedsHumanReviewArticlesRef(needsHumanReviewArticlesRef);
        state.setAllowPersistNeedsHumanReview(true);

        persistArticlesNode.execute(new OverAllState(stateMapper.toMap(state)));

        assertThat(articlePersistSupport.getPersistJobId()).isEqualTo(jobId);
        List<ArticleReviewEnvelope> persistedArticles = articlePersistSupport.getPersistedArticles();
        assertThat(persistedArticles)
                .extracting(article -> article.getArticle().getConceptId())
                .containsExactly("approved-concept");
        assertThat(persistedArticles)
                .extracting(ArticleReviewEnvelope::getReviewStatus)
                .containsExactly("passed");
        assertThat(persistedArticles)
                .noneMatch(article -> "needs_human_review".equals(article.getReviewStatus()));
        assertThat(articlePersistSupport.getRebuiltArticles()).isEqualTo(persistedArticles);
    }

    /**
     * 构造审查包裹。
     *
     * @param conceptId 概念标识
     * @param reviewStatus 审查状态
     * @param reviewResult 审查结果
     * @return 审查包裹
     */
    private ArticleReviewEnvelope reviewEnvelope(String conceptId, String reviewStatus, ReviewResult reviewResult) {
        ArticleReviewEnvelope reviewEnvelope = new ArticleReviewEnvelope();
        reviewEnvelope.setArticle(article(conceptId, reviewStatus));
        reviewEnvelope.setReviewStatus(reviewStatus);
        reviewEnvelope.setReviewResult(reviewResult);
        return reviewEnvelope;
    }

    /**
     * 构造文章记录。
     *
     * @param conceptId 概念标识
     * @param reviewStatus 审查状态
     * @return 文章记录
     */
    private ArticleRecord article(String conceptId, String reviewStatus) {
        return new ArticleRecord(
                conceptId,
                conceptId,
                "# " + conceptId + "\n\nreview_status: " + reviewStatus,
                "ACTIVE",
                OffsetDateTime.parse("2026-05-17T00:00:00+08:00"),
                List.of("docs/source.md"),
                "{}",
                "",
                List.of(),
                List.of(),
                List.of(),
                "medium",
                reviewStatus
        );
    }

    /**
     * 记录落库调用的测试替身。
     *
     * 职责：捕获 PersistArticlesNode 传入正式落库链路的审查包裹集合
     *
     * @author xiexu
     */
    private static final class RecordingArticlePersistSupport extends ArticlePersistSupport {

        private String persistJobId;

        private List<ArticleReviewEnvelope> persistedArticles = List.of();

        private List<ArticleReviewEnvelope> rebuiltArticles = List.of();

        /**
         * 创建记录型落库支撑替身。
         */
        private RecordingArticlePersistSupport() {
            super(null, null, null, null, null, null, null);
        }

        /**
         * 记录正式落库入参。
         *
         * @param jobId 作业标识
         * @param reviewedArticles 审查后文章集合
         * @param sourceId 资料源主键
         * @param sourceCode 资料源编码
         * @param sourceFileIdsByPath 源文件主键映射
         * @return 记录的文章数量
         */
        @Override
        public int persistArticles(
                String jobId,
                List<ArticleReviewEnvelope> reviewedArticles,
                Long sourceId,
                String sourceCode,
                Map<String, Long> sourceFileIdsByPath
        ) {
            this.persistJobId = jobId;
            this.persistedArticles = new ArrayList<ArticleReviewEnvelope>(reviewedArticles);
            return reviewedArticles.size();
        }

        /**
         * 记录重建分块入参。
         *
         * @param reviewedArticles 已落库文章集合
         */
        @Override
        public void rebuildArticleChunks(List<ArticleReviewEnvelope> reviewedArticles) {
            this.rebuiltArticles = new ArrayList<ArticleReviewEnvelope>(reviewedArticles);
        }

        /**
         * 获取落库作业标识。
         *
         * @return 落库作业标识
         */
        private String getPersistJobId() {
            return persistJobId;
        }

        /**
         * 获取记录的落库文章集合。
         *
         * @return 落库文章集合
         */
        private List<ArticleReviewEnvelope> getPersistedArticles() {
            return persistedArticles;
        }

        /**
         * 获取记录的重建分块文章集合。
         *
         * @return 重建分块文章集合
         */
        private List<ArticleReviewEnvelope> getRebuiltArticles() {
            return rebuiltArticles;
        }
    }
}
