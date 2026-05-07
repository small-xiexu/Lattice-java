package com.xbk.lattice.governance;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.article.service.ArticleIdentityResolver;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleReviewAuditJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleReviewAuditRecord;
import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionRecord;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.query.service.PendingQueryManager;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ArticleManualReviewService 测试
 *
 * 职责：验证人工复核服务的通用状态流转、快照、frontmatter 与审计副作用
 *
 * @author xiexu
 */
class ArticleManualReviewServiceTests {

    /**
     * 验证人工确认通过会更新状态、同步正文 frontmatter、保存快照和审计。
     */
    @Test
    void shouldApproveArticleAndPersistSnapshotAndAudit() {
        FakeMutableArticleJdbcRepository articleJdbcRepository = new FakeMutableArticleJdbcRepository(List.of(
                article("source-1:concept-alpha", 1L, "concept-alpha", "needs_human_review")
        ));
        FakeArticleSnapshotJdbcRepository articleSnapshotJdbcRepository = new FakeArticleSnapshotJdbcRepository();
        FakeArticleReviewAuditJdbcRepository articleReviewAuditJdbcRepository = new FakeArticleReviewAuditJdbcRepository();
        ArticleManualReviewService service = service(
                articleJdbcRepository,
                articleSnapshotJdbcRepository,
                articleReviewAuditJdbcRepository,
                new FakeArticleCorrectionService(articleJdbcRepository)
        );

        ArticleManualReviewResult result = service.approve(
                "source-1:concept-alpha",
                new ArticleManualReviewRequest(
                        1L,
                        "reviewer",
                        "证据一致",
                        "needs_human_review",
                        null
                )
        );

        ArticleRecord updated = articleJdbcRepository.findByArticleKey("source-1:concept-alpha").orElseThrow();
        assertThat(result.getPreviousReviewStatus()).isEqualTo("needs_human_review");
        assertThat(result.getReviewStatus()).isEqualTo("passed");
        assertThat(result.getReviewedBy()).isEqualTo("reviewer");
        assertThat(result.getAuditId()).isEqualTo(1L);
        assertThat(updated.getReviewStatus()).isEqualTo("passed");
        assertThat(updated.getContent()).contains("review_status: passed");
        assertThat(articleSnapshotJdbcRepository.savedRecords).hasSize(1);
        assertThat(articleSnapshotJdbcRepository.savedRecords.get(0).getSnapshotReason()).isEqualTo("manual_review_approve");
        assertThat(articleSnapshotJdbcRepository.savedRecords.get(0).getReviewStatus()).isEqualTo("passed");
        assertThat(articleReviewAuditJdbcRepository.savedRecords).hasSize(1);
        assertThat(articleReviewAuditJdbcRepository.savedRecords.get(0).getAction()).isEqualTo("approve");
        assertThat(articleReviewAuditJdbcRepository.savedRecords.get(0).getPreviousReviewStatus())
                .isEqualTo("needs_human_review");
        assertThat(articleReviewAuditJdbcRepository.savedRecords.get(0).getNextReviewStatus()).isEqualTo("passed");
        assertThat(articleReviewAuditJdbcRepository.savedRecords.get(0).getComment()).isEqualTo("证据一致");
    }

    /**
     * 验证提交修正复用现有纠错服务，并写入 request_changes 审计。
     */
    @Test
    void shouldRequestChangesThroughCorrectionServiceAndAudit() {
        FakeMutableArticleJdbcRepository articleJdbcRepository = new FakeMutableArticleJdbcRepository(List.of(
                article("source-1:concept-alpha", 1L, "concept-alpha", "needs_human_review")
        ));
        FakeArticleReviewAuditJdbcRepository articleReviewAuditJdbcRepository = new FakeArticleReviewAuditJdbcRepository();
        FakeArticleCorrectionService articleCorrectionService = new FakeArticleCorrectionService(articleJdbcRepository);
        ArticleManualReviewService service = service(
                articleJdbcRepository,
                new FakeArticleSnapshotJdbcRepository(),
                articleReviewAuditJdbcRepository,
                articleCorrectionService
        );

        ArticleManualReviewResult result = service.requestChanges(
                "source-1:concept-alpha",
                new ArticleManualReviewRequest(
                        1L,
                        "reviewer",
                        "需要补证据",
                        "needs_human_review",
                        "补充来源说明"
                )
        );

        ArticleRecord updated = articleJdbcRepository.findByArticleKey("source-1:concept-alpha").orElseThrow();
        assertThat(articleCorrectionService.calledArticleId).isEqualTo("source-1:concept-alpha");
        assertThat(articleCorrectionService.calledSourceId).isEqualTo(1L);
        assertThat(articleCorrectionService.calledCorrectionSummary).isEqualTo("补充来源说明");
        assertThat(result.getReviewStatus()).isEqualTo("needs_review");
        assertThat(updated.getReviewStatus()).isEqualTo("needs_review");
        assertThat(updated.getContent()).contains("review_status: needs_review");
        assertThat(articleReviewAuditJdbcRepository.savedRecords).hasSize(1);
        assertThat(articleReviewAuditJdbcRepository.savedRecords.get(0).getAction()).isEqualTo("request_changes");
        assertThat(articleReviewAuditJdbcRepository.savedRecords.get(0).getPreviousReviewStatus())
                .isEqualTo("needs_human_review");
        assertThat(articleReviewAuditJdbcRepository.savedRecords.get(0).getNextReviewStatus()).isEqualTo("needs_review");
        assertThat(articleReviewAuditJdbcRepository.savedRecords.get(0).getMetadataJson())
                .contains("\"validationSupported\":true");
    }

    /**
     * 验证并发状态不一致时拒绝复核并不产生副作用。
     */
    @Test
    void shouldRejectReviewWhenExpectedStatusChanged() {
        FakeMutableArticleJdbcRepository articleJdbcRepository = new FakeMutableArticleJdbcRepository(List.of(
                article("source-1:concept-alpha", 1L, "concept-alpha", "passed")
        ));
        FakeArticleSnapshotJdbcRepository articleSnapshotJdbcRepository = new FakeArticleSnapshotJdbcRepository();
        FakeArticleReviewAuditJdbcRepository articleReviewAuditJdbcRepository = new FakeArticleReviewAuditJdbcRepository();
        ArticleManualReviewService service = service(
                articleJdbcRepository,
                articleSnapshotJdbcRepository,
                articleReviewAuditJdbcRepository,
                new FakeArticleCorrectionService(articleJdbcRepository)
        );

        assertThatThrownBy(() -> service.approve(
                "source-1:concept-alpha",
                new ArticleManualReviewRequest(
                        1L,
                        "reviewer",
                        "通过",
                        "needs_human_review",
                        null
                )
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("review status changed");

        assertThat(articleJdbcRepository.findByArticleKey("source-1:concept-alpha").orElseThrow().getReviewStatus())
                .isEqualTo("passed");
        assertThat(articleSnapshotJdbcRepository.savedRecords).isEmpty();
        assertThat(articleReviewAuditJdbcRepository.savedRecords).isEmpty();
    }

    /**
     * 验证复核通过后总览和质量统计会随文章状态下降待复核数量。
     */
    @Test
    void shouldUpdateOverviewAndQualityMetricsAfterApproval() {
        FakeMutableArticleJdbcRepository articleJdbcRepository = new FakeMutableArticleJdbcRepository(List.of(
                article("source-1:concept-alpha", 1L, "concept-alpha", "needs_human_review"),
                article("source-1:concept-beta", 1L, "concept-beta", "passed")
        ));
        ArticleManualReviewService service = service(
                articleJdbcRepository,
                new FakeArticleSnapshotJdbcRepository(),
                new FakeArticleReviewAuditJdbcRepository(),
                new FakeArticleCorrectionService(articleJdbcRepository)
        );

        service.approve(
                "source-1:concept-alpha",
                new ArticleManualReviewRequest(1L, "reviewer", "通过", "needs_human_review", null)
        );

        StatusSnapshot statusSnapshot = new StatusService(
                articleJdbcRepository,
                new FakeSourceFileJdbcRepository(List.of()),
                new FakeContributionJdbcRepository(List.of()),
                new FixedPendingQueryManager(List.of())
        ).snapshot();
        QualityMetricsReport qualityMetricsReport = new QualityMetricsService(
                articleJdbcRepository,
                new FakeContributionJdbcRepository(List.of()),
                new FakeSourceFileJdbcRepository(List.of())
        ).measure();

        assertThat(statusSnapshot.getReviewPendingArticleCount()).isZero();
        assertThat(qualityMetricsReport.getPassedArticles()).isEqualTo(2);
        assertThat(qualityMetricsReport.getNeedsHumanReviewArticles()).isZero();
    }

    /**
     * 验证可按文章身份查询复核历史。
     */
    @Test
    void shouldListAuditsByArticleIdentity() {
        FakeMutableArticleJdbcRepository articleJdbcRepository = new FakeMutableArticleJdbcRepository(List.of(
                article("source-1:concept-alpha", 1L, "concept-alpha", "needs_human_review")
        ));
        FakeArticleReviewAuditJdbcRepository articleReviewAuditJdbcRepository = new FakeArticleReviewAuditJdbcRepository();
        ArticleManualReviewService service = service(
                articleJdbcRepository,
                new FakeArticleSnapshotJdbcRepository(),
                articleReviewAuditJdbcRepository,
                new FakeArticleCorrectionService(articleJdbcRepository)
        );
        service.approve(
                "source-1:concept-alpha",
                new ArticleManualReviewRequest(1L, "reviewer", "通过", "needs_human_review", null)
        );

        List<ArticleReviewAuditRecord> auditRecords = service.listAudits("source-1:concept-alpha", 1L);

        assertThat(auditRecords).hasSize(1);
        assertThat(auditRecords.get(0).getAction()).isEqualTo("approve");
    }

    private ArticleManualReviewService service(
            FakeMutableArticleJdbcRepository articleJdbcRepository,
            FakeArticleSnapshotJdbcRepository articleSnapshotJdbcRepository,
            FakeArticleReviewAuditJdbcRepository articleReviewAuditJdbcRepository,
            FakeArticleCorrectionService articleCorrectionService
    ) {
        return new ArticleManualReviewService(
                articleJdbcRepository,
                new ArticleIdentityResolver(articleJdbcRepository),
                articleSnapshotJdbcRepository,
                articleReviewAuditJdbcRepository,
                articleCorrectionService
        );
    }

    private ArticleRecord article(String articleKey, Long sourceId, String conceptId, String reviewStatus) {
        return new ArticleRecord(
                sourceId,
                articleKey,
                conceptId,
                "Concept " + conceptId,
                """
                        ---
                        title: "Concept"
                        summary: "Generic summary"
                        sources: ["source.md"]
                        review_status: %s
                        ---

                        # Concept

                        Generic content.
                        """.formatted(reviewStatus),
                "active",
                OffsetDateTime.parse("2026-05-05T10:00:00+08:00"),
                List.of("source.md"),
                "{}",
                "Generic summary",
                List.of("generic-key"),
                List.of(),
                List.of(),
                "medium",
                reviewStatus
        );
    }

    /**
     * 可变文章仓储替身。
     *
     * 职责：按 articleKey、sourceId + conceptId、conceptId 提供内存文章读写
     *
     * @author xiexu
     */
    private static class FakeMutableArticleJdbcRepository extends ArticleJdbcRepository {

        private final Map<String, ArticleRecord> recordsByArticleKey = new LinkedHashMap<String, ArticleRecord>();

        private FakeMutableArticleJdbcRepository(List<ArticleRecord> articleRecords) {
            super(new JdbcTemplate());
            for (ArticleRecord articleRecord : articleRecords) {
                upsert(articleRecord);
            }
        }

        /**
         * 保存文章。
         *
         * @param articleRecord 文章记录
         */
        @Override
        public void upsert(ArticleRecord articleRecord) {
            recordsByArticleKey.put(articleRecord.getArticleKey(), articleRecord);
        }

        /**
         * 按文章唯一键查询。
         *
         * @param articleKey 文章唯一键
         * @return 文章记录
         */
        @Override
        public Optional<ArticleRecord> findByArticleKey(String articleKey) {
            return Optional.ofNullable(recordsByArticleKey.get(articleKey));
        }

        /**
         * 按资料源与概念标识查询。
         *
         * @param sourceId 资料源主键
         * @param conceptId 概念标识
         * @return 文章记录
         */
        @Override
        public Optional<ArticleRecord> findBySourceIdAndConceptId(Long sourceId, String conceptId) {
            for (ArticleRecord articleRecord : recordsByArticleKey.values()) {
                if (conceptId.equals(articleRecord.getConceptId()) && sourceId.equals(articleRecord.getSourceId())) {
                    return Optional.of(articleRecord);
                }
            }
            return Optional.empty();
        }

        /**
         * 按概念标识查询。
         *
         * @param conceptId 概念标识
         * @return 文章记录
         */
        @Override
        public Optional<ArticleRecord> findByConceptId(String conceptId) {
            for (ArticleRecord articleRecord : recordsByArticleKey.values()) {
                if (conceptId.equals(articleRecord.getConceptId())) {
                    return Optional.of(articleRecord);
                }
            }
            return Optional.empty();
        }

        /**
         * 查询全部文章。
         *
         * @return 文章列表
         */
        @Override
        public List<ArticleRecord> findAll() {
            return List.copyOf(recordsByArticleKey.values());
        }
    }

    /**
     * 文章快照仓储替身。
     *
     * 职责：记录保存过的快照
     *
     * @author xiexu
     */
    private static class FakeArticleSnapshotJdbcRepository extends ArticleSnapshotJdbcRepository {

        private final List<ArticleSnapshotRecord> savedRecords = new ArrayList<ArticleSnapshotRecord>();

        private FakeArticleSnapshotJdbcRepository() {
            super(new JdbcTemplate());
        }

        /**
         * 保存快照。
         *
         * @param articleSnapshotRecord 文章快照
         */
        @Override
        public void save(ArticleSnapshotRecord articleSnapshotRecord) {
            savedRecords.add(articleSnapshotRecord);
        }
    }

    /**
     * 人工复核审计仓储替身。
     *
     * 职责：记录保存过的复核审计，并支持按文章查询
     *
     * @author xiexu
     */
    private static class FakeArticleReviewAuditJdbcRepository extends ArticleReviewAuditJdbcRepository {

        private final List<ArticleReviewAuditRecord> savedRecords = new ArrayList<ArticleReviewAuditRecord>();

        private FakeArticleReviewAuditJdbcRepository() {
            super(new JdbcTemplate());
        }

        /**
         * 保存审计。
         *
         * @param articleReviewAuditRecord 审计记录
         * @return 保存后的审计
         */
        @Override
        public ArticleReviewAuditRecord save(ArticleReviewAuditRecord articleReviewAuditRecord) {
            ArticleReviewAuditRecord savedRecord = new ArticleReviewAuditRecord(
                    savedRecords.size() + 1L,
                    articleReviewAuditRecord.getSourceId(),
                    articleReviewAuditRecord.getArticleKey(),
                    articleReviewAuditRecord.getConceptId(),
                    articleReviewAuditRecord.getAction(),
                    articleReviewAuditRecord.getPreviousReviewStatus(),
                    articleReviewAuditRecord.getNextReviewStatus(),
                    articleReviewAuditRecord.getComment(),
                    articleReviewAuditRecord.getReviewedBy(),
                    articleReviewAuditRecord.getReviewedAt(),
                    articleReviewAuditRecord.getMetadataJson()
            );
            savedRecords.add(savedRecord);
            return savedRecord;
        }

        /**
         * 按文章查询审计。
         *
         * @param articleRecord 文章记录
         * @return 审计列表
         */
        @Override
        public List<ArticleReviewAuditRecord> findByArticle(ArticleRecord articleRecord) {
            List<ArticleReviewAuditRecord> matchedRecords = new ArrayList<ArticleReviewAuditRecord>();
            for (ArticleReviewAuditRecord savedRecord : savedRecords) {
                if (articleRecord.getArticleKey().equals(savedRecord.getArticleKey())) {
                    matchedRecords.add(savedRecord);
                }
            }
            return matchedRecords;
        }
    }

    /**
     * 文章纠错服务替身。
     *
     * 职责：确认人工复核提交修正时复用纠错入口
     *
     * @author xiexu
     */
    private static class FakeArticleCorrectionService extends ArticleCorrectionService {

        private final FakeMutableArticleJdbcRepository articleJdbcRepository;

        private String calledArticleId;

        private Long calledSourceId;

        private String calledCorrectionSummary;

        private FakeArticleCorrectionService(FakeMutableArticleJdbcRepository articleJdbcRepository) {
            super(null, null, null, null, null);
            this.articleJdbcRepository = articleJdbcRepository;
        }

        /**
         * 执行替身纠错。
         *
         * @param articleId 文章唯一键或概念标识
         * @param sourceId 可选资料源主键
         * @param correctionSummary 纠错摘要
         * @return 纠错结果
         */
        @Override
        public ArticleCorrectionResult correct(String articleId, Long sourceId, String correctionSummary) {
            this.calledArticleId = articleId;
            this.calledSourceId = sourceId;
            this.calledCorrectionSummary = correctionSummary;
            ArticleRecord articleRecord = articleJdbcRepository.findByArticleKey(articleId)
                    .or(() -> articleJdbcRepository.findBySourceIdAndConceptId(sourceId, articleId))
                    .or(() -> articleJdbcRepository.findByConceptId(articleId))
                    .orElseThrow();
            ArticleRecord updatedRecord = articleRecord.copy(
                    articleRecord.getTitle(),
                    articleRecord.getContent().replace("review_status: needs_human_review", "review_status: needs_review"),
                    articleRecord.getLifecycle(),
                    articleRecord.getCompiledAt(),
                    articleRecord.getSourcePaths(),
                    articleRecord.getMetadataJson(),
                    articleRecord.getSummary(),
                    articleRecord.getReferentialKeywords(),
                    articleRecord.getDependsOn(),
                    articleRecord.getRelated(),
                    articleRecord.getConfidence(),
                    "needs_review"
            );
            articleJdbcRepository.upsert(updatedRecord);
            return new ArticleCorrectionResult(
                    updatedRecord.getSourceId(),
                    updatedRecord.getArticleKey(),
                    updatedRecord.getConceptId(),
                    updatedRecord.getContent(),
                    List.of(),
                    true
            );
        }
    }

    /**
     * 源文件仓储替身。
     *
     * 职责：为状态和质量统计提供固定源文件集合
     *
     * @author xiexu
     */
    private static class FakeSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final List<SourceFileRecord> records;

        private FakeSourceFileJdbcRepository(List<SourceFileRecord> records) {
            super(new JdbcTemplate());
            this.records = records;
        }

        /**
         * 查询全部源文件。
         *
         * @return 源文件列表
         */
        @Override
        public List<SourceFileRecord> findAll() {
            return records;
        }
    }

    /**
     * 贡献仓储替身。
     *
     * 职责：为状态和质量统计提供固定贡献集合
     *
     * @author xiexu
     */
    private static class FakeContributionJdbcRepository extends ContributionJdbcRepository {

        private final List<ContributionRecord> records;

        private FakeContributionJdbcRepository(List<ContributionRecord> records) {
            super(new JdbcTemplate());
            this.records = records;
        }

        /**
         * 查询全部贡献。
         *
         * @return 贡献列表
         */
        @Override
        public List<ContributionRecord> findAll() {
            return records;
        }
    }

    /**
     * pending 查询管理器替身。
     *
     * 职责：为状态统计提供固定 pending 集合
     *
     * @author xiexu
     */
    private static class FixedPendingQueryManager implements PendingQueryManager {

        private final List<PendingQueryRecord> records;

        private FixedPendingQueryManager(List<PendingQueryRecord> records) {
            this.records = records;
        }

        /**
         * 创建 pending 查询。
         *
         * @param question 问题
         * @param queryResponse 查询响应
         * @return pending 记录
         */
        @Override
        public PendingQueryRecord createPendingQuery(String question, QueryResponse queryResponse) {
            throw new UnsupportedOperationException();
        }

        /**
         * 修正 pending 查询。
         *
         * @param queryId 查询标识
         * @param correction 修正内容
         * @return pending 记录
         */
        @Override
        public PendingQueryRecord correct(String queryId, String correction) {
            throw new UnsupportedOperationException();
        }

        /**
         * 确认 pending 查询。
         *
         * @param queryId 查询标识
         */
        @Override
        public void confirm(String queryId) {
            throw new UnsupportedOperationException();
        }

        /**
         * 丢弃 pending 查询。
         *
         * @param queryId 查询标识
         */
        @Override
        public void discard(String queryId) {
            throw new UnsupportedOperationException();
        }

        /**
         * 查询单条 pending。
         *
         * @param queryId 查询标识
         * @return pending 记录
         */
        @Override
        public PendingQueryRecord findPendingQuery(String queryId) {
            throw new UnsupportedOperationException();
        }

        /**
         * 查询 pending 列表。
         *
         * @return pending 列表
         */
        @Override
        public List<PendingQueryRecord> listPendingQueries() {
            return records;
        }
    }
}
