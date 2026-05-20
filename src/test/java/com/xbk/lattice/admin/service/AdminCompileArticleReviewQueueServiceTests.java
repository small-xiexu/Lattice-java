package com.xbk.lattice.admin.service;

import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.compiler.service.ArticlePersistSupport;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleReviewAuditJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleReviewAuditRecord;
import com.xbk.lattice.infra.persistence.CompileArticleReviewQueueJdbcRepository;
import com.xbk.lattice.infra.persistence.CompileArticleReviewQueueRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AdminCompileArticleReviewQueueService 测试
 *
 * 职责：验证编译人工确认队列 approve/reject 的最小发布闭环
 *
 * @author xiexu
 */
class AdminCompileArticleReviewQueueServiceTests {

    /**
     * 验证人工确认发布会写入正式文章、重建 chunk、刷新向量索引、写审计并更新队列状态。
     */
    @Test
    void shouldApproveQueueDraftAndPublishArticleWithAudit() {
        FakeCompileArticleReviewQueueJdbcRepository queueRepository =
                new FakeCompileArticleReviewQueueJdbcRepository(queueRecord(1L, "needs_human_review"));
        FakeMutableArticleJdbcRepository articleJdbcRepository = new FakeMutableArticleJdbcRepository();
        RecordingArticlePersistSupport articlePersistSupport = new RecordingArticlePersistSupport(articleJdbcRepository);
        FakeArticleReviewAuditJdbcRepository auditJdbcRepository = new FakeArticleReviewAuditJdbcRepository();
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository(List.of(
                new SourceFileRecord(101L, 7L, "docs/source.md", "docs/source.md", null, "preview", "md", 7L, "content", "{}", false, "docs/source.md")
        ));
        AdminCompileArticleReviewQueueService service = service(
                queueRepository,
                articleJdbcRepository,
                articlePersistSupport,
                auditJdbcRepository,
                sourceFileJdbcRepository
        );

        CompileArticleReviewQueueActionResult result = service.approve(
                1L,
                new CompileArticleReviewQueueActionRequest("reviewer-a", "确认可发布", "needs_human_review")
        );

        ArticleRecord publishedArticle = articleJdbcRepository.findByArticleKey("source-alpha--concept-alpha")
                .orElseThrow();
        CompileArticleReviewQueueRecord updatedQueueRecord = queueRepository.findById(1L).orElseThrow();
        assertThat(result.getPreviousReviewStatus()).isEqualTo("needs_human_review");
        assertThat(result.getAuditId()).isEqualTo(1L);
        assertThat(updatedQueueRecord.getReviewStatus()).isEqualTo("published");
        assertThat(updatedQueueRecord.getPublishedArticleKey()).isEqualTo("source-alpha--concept-alpha");
        assertThat(publishedArticle.getReviewStatus()).isEqualTo("passed");
        assertThat(publishedArticle.getLifecycle()).isEqualTo("ACTIVE");
        assertThat(publishedArticle.getContent()).contains("review_status: passed");
        assertThat(publishedArticle.getMetadataJson()).contains("\"humanReview\"");
        assertThat(articlePersistSupport.getPersistedArticles()).hasSize(1);
        assertThat(articlePersistSupport.getSourceFileIdsByPath()).containsEntry("docs/source.md", 101L);
        assertThat(articlePersistSupport.getRebuiltArticles()).hasSize(1);
        assertThat(articlePersistSupport.getVectorIndexedArticles()).hasSize(1);
        assertThat(articlePersistSupport.getVectorIndexedArticles().get(0).getArticle()).isSameAs(publishedArticle);
        assertThat(articlePersistSupport.getVectorIndexedArticles().get(0).getArticle().getReviewStatus())
                .isEqualTo("passed");
        assertThat(auditJdbcRepository.getSavedRecords()).hasSize(1);
        assertThat(auditJdbcRepository.getSavedRecords().get(0).getAction())
                .isEqualTo("compile_review_queue_approve");
        assertThat(auditJdbcRepository.getSavedRecords().get(0).getPreviousReviewStatus())
                .isEqualTo("needs_human_review");
        assertThat(auditJdbcRepository.getSavedRecords().get(0).getNextReviewStatus()).isEqualTo("passed");
    }

    /**
     * 验证人工驳回只更新队列和审计，不写入正式文章。
     */
    @Test
    void shouldRejectQueueDraftWithoutPublishingArticle() {
        FakeCompileArticleReviewQueueJdbcRepository queueRepository =
                new FakeCompileArticleReviewQueueJdbcRepository(queueRecord(2L, "needs_human_review"));
        FakeMutableArticleJdbcRepository articleJdbcRepository = new FakeMutableArticleJdbcRepository();
        RecordingArticlePersistSupport articlePersistSupport = new RecordingArticlePersistSupport(articleJdbcRepository);
        FakeArticleReviewAuditJdbcRepository auditJdbcRepository = new FakeArticleReviewAuditJdbcRepository();
        AdminCompileArticleReviewQueueService service = service(
                queueRepository,
                articleJdbcRepository,
                articlePersistSupport,
                auditJdbcRepository,
                new FakeSourceFileJdbcRepository(List.of())
        );

        CompileArticleReviewQueueActionResult result = service.reject(
                2L,
                new CompileArticleReviewQueueActionRequest("reviewer-b", "拒绝发布", "needs_human_review")
        );

        CompileArticleReviewQueueRecord updatedQueueRecord = queueRepository.findById(2L).orElseThrow();
        assertThat(result.getAuditId()).isEqualTo(1L);
        assertThat(updatedQueueRecord.getReviewStatus()).isEqualTo("rejected");
        assertThat(articleJdbcRepository.findAll()).isEmpty();
        assertThat(articlePersistSupport.getPersistedArticles()).isEmpty();
        assertThat(articlePersistSupport.getRebuiltArticles()).isEmpty();
        assertThat(articlePersistSupport.getVectorIndexedArticles()).isEmpty();
        assertThat(auditJdbcRepository.getSavedRecords()).hasSize(1);
        assertThat(auditJdbcRepository.getSavedRecords().get(0).getAction())
                .isEqualTo("compile_review_queue_reject");
    }

    /**
     * 验证正式文章键已存在时，发布会明确拒绝而不是覆盖。
     */
    @Test
    void shouldRejectApproveWhenArticleKeyAlreadyExists() {
        FakeCompileArticleReviewQueueJdbcRepository queueRepository =
                new FakeCompileArticleReviewQueueJdbcRepository(queueRecord(3L, "needs_human_review"));
        FakeMutableArticleJdbcRepository articleJdbcRepository = new FakeMutableArticleJdbcRepository();
        articleJdbcRepository.upsert(article("source-alpha--concept-alpha", 7L, "concept-alpha", "passed"));
        RecordingArticlePersistSupport articlePersistSupport = new RecordingArticlePersistSupport(articleJdbcRepository);
        AdminCompileArticleReviewQueueService service = service(
                queueRepository,
                articleJdbcRepository,
                articlePersistSupport,
                new FakeArticleReviewAuditJdbcRepository(),
                new FakeSourceFileJdbcRepository(List.of())
        );

        assertThatThrownBy(() -> service.approve(
                3L,
                new CompileArticleReviewQueueActionRequest("reviewer-c", "冲突", "needs_human_review")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("article already exists");

        assertThat(queueRepository.findById(3L).orElseThrow().getReviewStatus()).isEqualTo("needs_human_review");
        assertThat(articlePersistSupport.getPersistedArticles()).isEmpty();
    }

    private AdminCompileArticleReviewQueueService service(
            FakeCompileArticleReviewQueueJdbcRepository queueRepository,
            FakeMutableArticleJdbcRepository articleJdbcRepository,
            RecordingArticlePersistSupport articlePersistSupport,
            FakeArticleReviewAuditJdbcRepository auditJdbcRepository,
            FakeSourceFileJdbcRepository sourceFileJdbcRepository
    ) {
        return new AdminCompileArticleReviewQueueService(
                queueRepository,
                articleJdbcRepository,
                articlePersistSupport,
                auditJdbcRepository,
                sourceFileJdbcRepository
        );
    }

    private CompileArticleReviewQueueRecord queueRecord(long id, String reviewStatus) {
        return new CompileArticleReviewQueueRecord(
                id,
                "job-human-review",
                7L,
                "source-alpha",
                "concept-alpha",
                "source-alpha--concept-alpha",
                "Concept Alpha",
                """
                        ---
                        title: "Concept Alpha"
                        summary: "Generic summary"
                        sources: ["docs/source.md"]
                        review_status: needs_human_review
                        ---

                        # Concept Alpha

                        Generic content.
                        """,
                "ACTIVE",
                OffsetDateTime.parse("2026-05-20T08:00:00+08:00"),
                List.of("docs/source.md"),
                "{\"existing\":true}",
                reviewStatus,
                "llm",
                "llm",
                "[{\"severity\":\"HIGH\",\"category\":\"GROUNDING\",\"description\":\"缺少来源\"}]",
                1,
                1,
                OffsetDateTime.parse("2026-05-20T08:01:00+08:00"),
                OffsetDateTime.parse("2026-05-20T08:01:00+08:00"),
                null,
                null,
                null,
                null
        );
    }

    private ArticleRecord article(String articleKey, Long sourceId, String conceptId, String reviewStatus) {
        return new ArticleRecord(
                sourceId,
                articleKey,
                conceptId,
                "Concept Alpha",
                "# Concept Alpha\n\nreview_status: " + reviewStatus,
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

    /**
     * 队列仓储替身。
     *
     * @author xiexu
     */
    private static class FakeCompileArticleReviewQueueJdbcRepository
            extends CompileArticleReviewQueueJdbcRepository {

        private final Map<Long, CompileArticleReviewQueueRecord> recordsById =
                new LinkedHashMap<Long, CompileArticleReviewQueueRecord>();

        /**
         * 创建队列仓储替身。
         *
         * @param queueRecord 初始队列记录
         */
        private FakeCompileArticleReviewQueueJdbcRepository(CompileArticleReviewQueueRecord queueRecord) {
            super(null);
            recordsById.put(queueRecord.getId(), queueRecord);
        }

        /**
         * 按主键查询队列记录。
         *
         * @param id 队列主键
         * @return 队列记录
         */
        @Override
        public Optional<CompileArticleReviewQueueRecord> findById(long id) {
            return Optional.ofNullable(recordsById.get(id));
        }

        /**
         * 标记已发布。
         *
         * @param id 队列主键
         * @param reviewedBy 复核人
         * @param reviewedAt 复核时间
         * @param reviewComment 复核意见
         * @param publishedArticleKey 发布后的文章唯一键
         * @return 是否更新成功
         */
        @Override
        public boolean markPublished(
                long id,
                String reviewedBy,
                OffsetDateTime reviewedAt,
                String reviewComment,
                String publishedArticleKey
        ) {
            CompileArticleReviewQueueRecord currentRecord = recordsById.get(id);
            if (currentRecord == null || !"needs_human_review".equals(currentRecord.getReviewStatus())) {
                return false;
            }
            recordsById.put(id, copyWithReviewState(
                    currentRecord,
                    "published",
                    reviewedBy,
                    reviewedAt,
                    reviewComment,
                    publishedArticleKey
            ));
            return true;
        }

        /**
         * 标记已驳回。
         *
         * @param id 队列主键
         * @param reviewedBy 复核人
         * @param reviewedAt 复核时间
         * @param reviewComment 复核意见
         * @return 是否更新成功
         */
        @Override
        public boolean markRejected(
                long id,
                String reviewedBy,
                OffsetDateTime reviewedAt,
                String reviewComment
        ) {
            CompileArticleReviewQueueRecord currentRecord = recordsById.get(id);
            if (currentRecord == null || !"needs_human_review".equals(currentRecord.getReviewStatus())) {
                return false;
            }
            recordsById.put(id, copyWithReviewState(
                    currentRecord,
                    "rejected",
                    reviewedBy,
                    reviewedAt,
                    reviewComment,
                    null
            ));
            return true;
        }

        private CompileArticleReviewQueueRecord copyWithReviewState(
                CompileArticleReviewQueueRecord record,
                String reviewStatus,
                String reviewedBy,
                OffsetDateTime reviewedAt,
                String reviewComment,
                String publishedArticleKey
        ) {
            return new CompileArticleReviewQueueRecord(
                    record.getId(),
                    record.getJobId(),
                    record.getSourceId(),
                    record.getSourceCode(),
                    record.getConceptId(),
                    record.getArticleKey(),
                    record.getTitle(),
                    record.getContent(),
                    record.getLifecycle(),
                    record.getCompiledAt(),
                    record.getSourcePaths(),
                    record.getMetadataJson(),
                    reviewStatus,
                    record.getReviewRoute(),
                    record.getReviewerModel(),
                    record.getReviewIssuesJson(),
                    record.getFixAttemptCount(),
                    record.getMaxFixRounds(),
                    record.getCreatedAt(),
                    reviewedAt,
                    reviewedBy,
                    reviewedAt,
                    reviewComment,
                    publishedArticleKey
            );
        }
    }

    /**
     * 可变文章仓储替身。
     *
     * @author xiexu
     */
    private static class FakeMutableArticleJdbcRepository extends ArticleJdbcRepository {

        private final Map<String, ArticleRecord> recordsByArticleKey = new LinkedHashMap<String, ArticleRecord>();

        /**
         * 创建文章仓储替身。
         */
        private FakeMutableArticleJdbcRepository() {
            super(null);
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
         * 按资料源和概念查询。
         *
         * @param sourceId 资料源主键
         * @param conceptId 概念标识
         * @return 文章记录
         */
        @Override
        public Optional<ArticleRecord> findBySourceIdAndConceptId(Long sourceId, String conceptId) {
            for (ArticleRecord articleRecord : recordsByArticleKey.values()) {
                if (sourceId.equals(articleRecord.getSourceId()) && conceptId.equals(articleRecord.getConceptId())) {
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
     * 记录文章落库副作用的支撑替身。
     *
     * @author xiexu
     */
    private static class RecordingArticlePersistSupport extends ArticlePersistSupport {

        private final FakeMutableArticleJdbcRepository articleJdbcRepository;

        private List<ArticleReviewEnvelope> persistedArticles = List.of();

        private List<ArticleReviewEnvelope> rebuiltArticles = List.of();

        private List<ArticleReviewEnvelope> vectorIndexedArticles = List.of();

        private Map<String, Long> sourceFileIdsByPath = Map.of();

        /**
         * 创建文章落库支撑替身。
         *
         * @param articleJdbcRepository 文章仓储
         */
        private RecordingArticlePersistSupport(FakeMutableArticleJdbcRepository articleJdbcRepository) {
            super(null, null, null, null, null, null, null);
            this.articleJdbcRepository = articleJdbcRepository;
        }

        /**
         * 记录并保存正式文章。
         *
         * @param jobId 作业标识
         * @param reviewedArticles 审查后文章集合
         * @param sourceId 资料源主键
         * @param sourceCode 资料源编码
         * @param sourceFileIdsByPath 源文件主键映射
         * @return 已保存数量
         */
        @Override
        public int persistArticles(
                String jobId,
                List<ArticleReviewEnvelope> reviewedArticles,
                Long sourceId,
                String sourceCode,
                Map<String, Long> sourceFileIdsByPath
        ) {
            this.persistedArticles = new ArrayList<ArticleReviewEnvelope>(reviewedArticles);
            this.sourceFileIdsByPath = new LinkedHashMap<String, Long>(sourceFileIdsByPath);
            for (ArticleReviewEnvelope reviewedArticle : reviewedArticles) {
                articleJdbcRepository.upsert(reviewedArticle.getArticle());
            }
            return reviewedArticles.size();
        }

        /**
         * 记录重建 chunk 入参。
         *
         * @param reviewedArticles 已落库文章集合
         */
        @Override
        public void rebuildArticleChunks(List<ArticleReviewEnvelope> reviewedArticles) {
            this.rebuiltArticles = new ArrayList<ArticleReviewEnvelope>(reviewedArticles);
        }

        /**
         * 记录向量索引入参。
         *
         * @param reviewedArticles 已落库文章集合
         */
        @Override
        public void refreshVectorIndex(List<ArticleReviewEnvelope> reviewedArticles) {
            this.vectorIndexedArticles = new ArrayList<ArticleReviewEnvelope>(reviewedArticles);
        }

        /**
         * 获取保存文章。
         *
         * @return 保存文章
         */
        private List<ArticleReviewEnvelope> getPersistedArticles() {
            return persistedArticles;
        }

        /**
         * 获取重建 chunk 文章。
         *
         * @return 重建 chunk 文章
         */
        private List<ArticleReviewEnvelope> getRebuiltArticles() {
            return rebuiltArticles;
        }

        /**
         * 获取向量索引文章。
         *
         * @return 向量索引文章
         */
        private List<ArticleReviewEnvelope> getVectorIndexedArticles() {
            return vectorIndexedArticles;
        }

        /**
         * 获取源文件主键映射。
         *
         * @return 源文件主键映射
         */
        private Map<String, Long> getSourceFileIdsByPath() {
            return sourceFileIdsByPath;
        }
    }

    /**
     * 审计仓储替身。
     *
     * @author xiexu
     */
    private static class FakeArticleReviewAuditJdbcRepository extends ArticleReviewAuditJdbcRepository {

        private final List<ArticleReviewAuditRecord> savedRecords = new ArrayList<ArticleReviewAuditRecord>();

        /**
         * 创建审计仓储替身。
         */
        private FakeArticleReviewAuditJdbcRepository() {
            super(null);
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
         * 获取保存过的审计。
         *
         * @return 审计列表
         */
        private List<ArticleReviewAuditRecord> getSavedRecords() {
            return savedRecords;
        }
    }

    /**
     * 源文件仓储替身。
     *
     * @author xiexu
     */
    private static class FakeSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final Map<String, SourceFileRecord> recordsBySourceAndPath =
                new LinkedHashMap<String, SourceFileRecord>();

        /**
         * 创建源文件仓储替身。
         *
         * @param sourceFileRecords 源文件记录
         */
        private FakeSourceFileJdbcRepository(List<SourceFileRecord> sourceFileRecords) {
            super(null);
            for (SourceFileRecord sourceFileRecord : sourceFileRecords) {
                recordsBySourceAndPath.put(key(sourceFileRecord.getSourceId(), sourceFileRecord.getRelativePath()), sourceFileRecord);
            }
        }

        /**
         * 按资料源和相对路径查询。
         *
         * @param sourceId 资料源主键
         * @param relativePath 相对路径
         * @return 源文件记录
         */
        @Override
        public Optional<SourceFileRecord> findBySourceIdAndRelativePath(Long sourceId, String relativePath) {
            return Optional.ofNullable(recordsBySourceAndPath.get(key(sourceId, relativePath)));
        }

        private String key(Long sourceId, String relativePath) {
            return sourceId + "::" + relativePath;
        }
    }
}
