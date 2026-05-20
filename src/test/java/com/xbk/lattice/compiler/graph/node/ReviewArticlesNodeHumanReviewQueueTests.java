package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.InMemoryCompileWorkingSetStore;
import com.xbk.lattice.compiler.graph.ReviewDecisionPolicy;
import com.xbk.lattice.compiler.service.ArticleCompileSupport;
import com.xbk.lattice.compiler.service.CompileArticleReviewQueueService;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.CompileArticleReviewQueueJdbcRepository;
import com.xbk.lattice.infra.persistence.CompileArticleReviewQueueRecord;
import com.xbk.lattice.query.domain.ReviewIssue;
import com.xbk.lattice.query.domain.ReviewResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReviewArticlesNode 人工确认队列测试
 *
 * 职责：验证最终 needs_human_review 草稿会写入持久化人工确认队列
 *
 * @author xiexu
 */
class ReviewArticlesNodeHumanReviewQueueTests {

    /**
     * 验证达到最大修复轮数后，待人工确认草稿会入队而不是进入正式文章落库。
     */
    @Test
    void shouldEnqueueNeedsHumanReviewDraftsAfterReviewRoundLimit() {
        ReviewIssue reviewIssue = new ReviewIssue("HIGH", "GROUNDING", "仍缺少来源支撑");
        RecordingArticleCompileSupport articleCompileSupport = new RecordingArticleCompileSupport(
                ReviewResult.issuesFound(List.of(reviewIssue))
        );
        RecordingCompileArticleReviewQueueJdbcRepository queueJdbcRepository =
                new RecordingCompileArticleReviewQueueJdbcRepository();
        CompileArticleReviewQueueService queueService = new CompileArticleReviewQueueService(queueJdbcRepository);
        InMemoryCompileWorkingSetStore workingSetStore = new InMemoryCompileWorkingSetStore();
        ReviewArticlesNode reviewArticlesNode = new ReviewArticlesNode(
                new CompileGraphStateMapper(),
                workingSetStore,
                articleCompileSupport,
                new ReviewDecisionPolicy(),
                fixedObjectProvider(queueService)
        );
        CompileGraphState state = new CompileGraphState();
        state.setJobId("job-human-review-queue");
        state.setSourceId(21L);
        state.setSourceCode("source-alpha");
        state.setAutoFixEnabled(true);
        state.setFixAttemptCount(1);
        state.setMaxFixRounds(1);
        state.setDraftArticlesRef(workingSetStore.saveDraftArticles(
                state.getJobId(),
                List.of(article("source-alpha--concept-alpha", 21L, "concept-alpha", "pending"))
        ));

        reviewArticlesNode.execute(new OverAllState(new CompileGraphStateMapper().toMap(state)));

        assertThat(queueJdbcRepository.getSavedRecords()).hasSize(1);
        CompileArticleReviewQueueRecord savedRecord = queueJdbcRepository.getSavedRecords().get(0);
        assertThat(savedRecord.getJobId()).isEqualTo("job-human-review-queue");
        assertThat(savedRecord.getSourceId()).isEqualTo(21L);
        assertThat(savedRecord.getSourceCode()).isEqualTo("source-alpha");
        assertThat(savedRecord.getArticleKey()).isEqualTo("source-alpha--concept-alpha");
        assertThat(savedRecord.getConceptId()).isEqualTo("concept-alpha");
        assertThat(savedRecord.getReviewStatus()).isEqualTo("needs_human_review");
        assertThat(savedRecord.getContent()).contains("review_status: needs_human_review");
        assertThat(savedRecord.getReviewIssuesJson()).contains("GROUNDING");
        assertThat(savedRecord.getFixAttemptCount()).isEqualTo(1);
        assertThat(savedRecord.getMaxFixRounds()).isEqualTo(1);
    }

    private ArticleRecord article(String articleKey, Long sourceId, String conceptId, String reviewStatus) {
        return new ArticleRecord(
                sourceId,
                articleKey,
                conceptId,
                "Concept Alpha",
                """
                        ---
                        title: "Concept Alpha"
                        summary: "Generic summary"
                        sources: ["docs/source.md"]
                        review_status: %s
                        ---

                        # Concept Alpha

                        Generic content.
                        """.formatted(reviewStatus),
                "ACTIVE",
                OffsetDateTime.parse("2026-05-20T08:00:00+08:00"),
                List.of("docs/source.md"),
                "{}",
                "Generic summary",
                List.of(),
                List.of(),
                List.of(),
                "medium",
                reviewStatus
        );
    }

    private ObjectProvider<CompileArticleReviewQueueService> fixedObjectProvider(
            CompileArticleReviewQueueService queueService
    ) {
        return new ObjectProvider<CompileArticleReviewQueueService>() {

            /**
             * 返回固定队列服务。
             *
             * @return 队列服务
             */
            @Override
            public CompileArticleReviewQueueService getObject(Object... args) {
                return queueService;
            }

            /**
             * 返回固定队列服务。
             *
             * @return 队列服务
             */
            @Override
            public CompileArticleReviewQueueService getIfAvailable() {
                return queueService;
            }

            /**
             * 返回固定队列服务。
             *
             * @param defaultSupplier 默认供应器
             * @return 队列服务
             */
            @Override
            public CompileArticleReviewQueueService getIfAvailable(
                    Supplier<CompileArticleReviewQueueService> defaultSupplier
            ) {
                return queueService;
            }

            /**
             * 返回固定队列服务。
             *
             * @return 队列服务
             */
            @Override
            public CompileArticleReviewQueueService getIfUnique() {
                return queueService;
            }

            /**
             * 返回固定队列服务。
             *
             * @param defaultSupplier 默认供应器
             * @return 队列服务
             */
            @Override
            public CompileArticleReviewQueueService getIfUnique(
                    Supplier<CompileArticleReviewQueueService> defaultSupplier
            ) {
                return queueService;
            }

            /**
             * 对固定队列服务执行消费。
             *
             * @param dependencyConsumer 消费器
             */
            @Override
            public void ifAvailable(Consumer<CompileArticleReviewQueueService> dependencyConsumer) {
                dependencyConsumer.accept(queueService);
            }

            /**
             * 对固定队列服务执行唯一消费。
             *
             * @param dependencyConsumer 消费器
             */
            @Override
            public void ifUnique(Consumer<CompileArticleReviewQueueService> dependencyConsumer) {
                dependencyConsumer.accept(queueService);
            }

            /**
             * 返回固定队列服务。
             *
             * @return 队列服务
             */
            @Override
            public CompileArticleReviewQueueService getObject() {
                return queueService;
            }

            /**
             * 返回服务流。
             *
             * @return 服务流
             */
            @Override
            public Stream<CompileArticleReviewQueueService> stream() {
                return Stream.of(queueService);
            }

            /**
             * 返回有序服务流。
             *
             * @return 服务流
             */
            @Override
            public Stream<CompileArticleReviewQueueService> orderedStream() {
                return Stream.of(queueService);
            }
        };
    }

    /**
     * 固定审查结果的文章编译支撑替身。
     *
     * @author xiexu
     */
    private static class RecordingArticleCompileSupport extends ArticleCompileSupport {

        private final ReviewResult reviewResult;

        /**
         * 创建文章编译支撑替身。
         *
         * @param reviewResult 审查结果
         */
        private RecordingArticleCompileSupport(ReviewResult reviewResult) {
            super(null, null, null, null, null);
            this.reviewResult = reviewResult;
        }

        /**
         * 返回固定审查结果。
         *
         * @param draftArticles 草稿文章集合
         * @param scopeId 作用域标识
         * @param scene 场景
         * @return 审查包裹集合
         */
        @Override
        public List<ArticleReviewEnvelope> reviewDraftArticles(
                List<ArticleRecord> draftArticles,
                String scopeId,
                String scene
        ) {
            List<ArticleReviewEnvelope> envelopes = new ArrayList<ArticleReviewEnvelope>();
            for (ArticleRecord draftArticle : draftArticles) {
                ArticleReviewEnvelope envelope = new ArticleReviewEnvelope();
                envelope.setArticle(draftArticle);
                envelope.setReviewResult(reviewResult);
                envelope.setReviewStatus(reviewResult.isPass() ? "passed" : "pending");
                envelope.setReviewerRoute("llm");
                envelopes.add(envelope);
            }
            return envelopes;
        }
    }

    /**
     * 记录队列写入的仓储替身。
     *
     * @author xiexu
     */
    private static class RecordingCompileArticleReviewQueueJdbcRepository
            extends CompileArticleReviewQueueJdbcRepository {

        private final List<CompileArticleReviewQueueRecord> savedRecords =
                new ArrayList<CompileArticleReviewQueueRecord>();

        /**
         * 创建记录型队列仓储替身。
         */
        private RecordingCompileArticleReviewQueueJdbcRepository() {
            super(null);
        }

        /**
         * 记录待人工确认草稿。
         *
         * @param record 队列记录
         */
        @Override
        public void upsertPending(CompileArticleReviewQueueRecord record) {
            savedRecords.add(record);
        }

        /**
         * 返回保存过的记录。
         *
         * @return 保存记录
         */
        private List<CompileArticleReviewQueueRecord> getSavedRecords() {
            return savedRecords;
        }

        /**
         * 按主键查询队列记录。
         *
         * @param id 队列主键
         * @return 空结果
         */
        @Override
        public Optional<CompileArticleReviewQueueRecord> findById(long id) {
            return Optional.empty();
        }
    }
}
