package com.xbk.lattice.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.article.service.ArticleMarkdownSupport;
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
import com.xbk.lattice.query.domain.ReviewResult;
import com.xbk.lattice.shared.json.JsonMappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 管理侧编译人工确认队列服务
 *
 * 职责：查询编译草稿人工确认队列，并在确认通过后发布为正式文章
 *
 * @author xiexu
 */
@Slf4j
@Service
public class AdminCompileArticleReviewQueueService {

    private static final String STATUS_NEEDS_HUMAN_REVIEW = "needs_human_review";

    private static final String STATUS_PUBLISHED = "published";

    private static final String STATUS_REJECTED = "rejected";

    private static final String REVIEW_STATUS_PASSED = "passed";

    private static final String LIFECYCLE_ACTIVE = "ACTIVE";

    private static final String AUDIT_ACTION_APPROVE = "compile_review_queue_approve";

    private static final String AUDIT_ACTION_REJECT = "compile_review_queue_reject";

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.moduleAwareMapper();

    private final CompileArticleReviewQueueJdbcRepository compileArticleReviewQueueJdbcRepository;

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ArticlePersistSupport articlePersistSupport;

    private final ArticleReviewAuditJdbcRepository articleReviewAuditJdbcRepository;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    private final TransactionTemplate vectorRefreshTransactionTemplate;

    /**
     * 创建管理侧编译人工确认队列服务。
     *
     * @param compileArticleReviewQueueJdbcRepository 队列仓储
     * @param articleJdbcRepository 文章仓储
     * @param articlePersistSupport 文章落库支撑服务
     * @param articleReviewAuditJdbcRepository 审计仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param transactionManagerProvider 事务管理器提供者
     */
    @Autowired
    public AdminCompileArticleReviewQueueService(
            CompileArticleReviewQueueJdbcRepository compileArticleReviewQueueJdbcRepository,
            ArticleJdbcRepository articleJdbcRepository,
            ArticlePersistSupport articlePersistSupport,
            ArticleReviewAuditJdbcRepository articleReviewAuditJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider
    ) {
        this(
                compileArticleReviewQueueJdbcRepository,
                articleJdbcRepository,
                articlePersistSupport,
                articleReviewAuditJdbcRepository,
                sourceFileJdbcRepository,
                buildVectorRefreshTransactionTemplate(transactionManagerProvider)
        );
    }

    /**
     * 创建管理侧编译人工确认队列服务。
     *
     * @param compileArticleReviewQueueJdbcRepository 队列仓储
     * @param articleJdbcRepository 文章仓储
     * @param articlePersistSupport 文章落库支撑服务
     * @param articleReviewAuditJdbcRepository 审计仓储
     * @param sourceFileJdbcRepository 源文件仓储
     */
    public AdminCompileArticleReviewQueueService(
            CompileArticleReviewQueueJdbcRepository compileArticleReviewQueueJdbcRepository,
            ArticleJdbcRepository articleJdbcRepository,
            ArticlePersistSupport articlePersistSupport,
            ArticleReviewAuditJdbcRepository articleReviewAuditJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository
    ) {
        this(
                compileArticleReviewQueueJdbcRepository,
                articleJdbcRepository,
                articlePersistSupport,
                articleReviewAuditJdbcRepository,
                sourceFileJdbcRepository,
                (TransactionTemplate) null
        );
    }

    /**
     * 创建管理侧编译人工确认队列服务。
     *
     * @param compileArticleReviewQueueJdbcRepository 队列仓储
     * @param articleJdbcRepository 文章仓储
     * @param articlePersistSupport 文章落库支撑服务
     * @param articleReviewAuditJdbcRepository 审计仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param vectorRefreshTransactionTemplate 向量刷新事务模板
     */
    private AdminCompileArticleReviewQueueService(
            CompileArticleReviewQueueJdbcRepository compileArticleReviewQueueJdbcRepository,
            ArticleJdbcRepository articleJdbcRepository,
            ArticlePersistSupport articlePersistSupport,
            ArticleReviewAuditJdbcRepository articleReviewAuditJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            TransactionTemplate vectorRefreshTransactionTemplate
    ) {
        this.compileArticleReviewQueueJdbcRepository = compileArticleReviewQueueJdbcRepository;
        this.articleJdbcRepository = articleJdbcRepository;
        this.articlePersistSupport = articlePersistSupport;
        this.articleReviewAuditJdbcRepository = articleReviewAuditJdbcRepository;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
        this.vectorRefreshTransactionTemplate = vectorRefreshTransactionTemplate;
    }

    /**
     * 查询人工确认队列。
     *
     * @param status 队列状态
     * @param limit 返回上限
     * @return 队列记录列表
     */
    public List<CompileArticleReviewQueueRecord> list(String status, int limit) {
        return compileArticleReviewQueueJdbcRepository.list(status, limit);
    }

    /**
     * 查询队列详情。
     *
     * @param id 队列主键
     * @return 队列记录
     */
    public CompileArticleReviewQueueRecord get(long id) {
        return requireQueueRecord(id);
    }

    /**
     * 人工确认发布草稿。
     *
     * @param id 队列主键
     * @param request 动作请求
     * @return 动作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public CompileArticleReviewQueueActionResult approve(
            long id,
            CompileArticleReviewQueueActionRequest request
    ) {
        CompileArticleReviewQueueRecord queueRecord = requireQueueRecord(id);
        if (STATUS_PUBLISHED.equalsIgnoreCase(queueRecord.getReviewStatus())) {
            return new CompileArticleReviewQueueActionResult(queueRecord, STATUS_PUBLISHED, 0L);
        }
        assertPending(queueRecord, request);
        assertNoArticleConflict(queueRecord);
        OffsetDateTime reviewedAt = OffsetDateTime.now();
        ArticleRecord articleRecord = toApprovedArticle(queueRecord, reviewedAt);
        ArticleReviewEnvelope reviewEnvelope = approvedEnvelope(articleRecord);
        List<ArticleReviewEnvelope> reviewedArticles = List.of(reviewEnvelope);
        articlePersistSupport.persistArticles(
                queueRecord.getJobId(),
                reviewedArticles,
                queueRecord.getSourceId(),
                queueRecord.getSourceCode(),
                resolveSourceFileIdsByPath(queueRecord)
        );
        articlePersistSupport.rebuildArticleChunks(reviewedArticles);
        ArticleReviewAuditRecord savedAudit = saveAudit(
                articleRecord,
                AUDIT_ACTION_APPROVE,
                STATUS_NEEDS_HUMAN_REVIEW,
                REVIEW_STATUS_PASSED,
                request,
                reviewedAt,
                buildAuditMetadata(queueRecord)
        );
        boolean updated = compileArticleReviewQueueJdbcRepository.markPublished(
                queueRecord.getId(),
                normalizeText(request == null ? null : request.getReviewedBy()),
                reviewedAt,
                normalizeText(request == null ? null : request.getComment()),
                articleRecord.getArticleKey()
        );
        if (!updated) {
            throw new IllegalStateException("compile review queue status changed: id=" + queueRecord.getId());
        }
        scheduleVectorRefreshAfterPublication(articleRecord.getArticleKey(), reviewedArticles);
        CompileArticleReviewQueueRecord updatedQueueRecord = requireQueueRecord(id);
        return new CompileArticleReviewQueueActionResult(
                updatedQueueRecord,
                STATUS_NEEDS_HUMAN_REVIEW,
                savedAudit.getId()
        );
    }

    /**
     * 在发布事务提交后刷新向量索引。
     *
     * @param articleKey 文章唯一键
     * @param reviewedArticles 审查后文章集合
     */
    private void scheduleVectorRefreshAfterPublication(
            String articleKey,
            List<ArticleReviewEnvelope> reviewedArticles
    ) {
        List<ArticleReviewEnvelope> fallbackReviewedArticles = List.copyOf(reviewedArticles);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            refreshPublishedArticleVectorIndex(articleKey, fallbackReviewedArticles);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            /**
             * 在发布事务提交后执行向量索引刷新。
             */
            @Override
            public void afterCommit() {
                refreshPublishedArticleVectorIndex(articleKey, fallbackReviewedArticles);
            }
        });
    }

    /**
     * 使用正式落库后的文章记录刷新向量索引。
     *
     * @param articleKey 文章唯一键
     * @param fallbackReviewedArticles 兜底文章集合
     */
    private void refreshPublishedArticleVectorIndex(
            String articleKey,
            List<ArticleReviewEnvelope> fallbackReviewedArticles
    ) {
        if (vectorRefreshTransactionTemplate != null) {
            vectorRefreshTransactionTemplate.executeWithoutResult(
                    status -> doRefreshPublishedArticleVectorIndex(articleKey, fallbackReviewedArticles)
            );
            return;
        }
        doRefreshPublishedArticleVectorIndex(articleKey, fallbackReviewedArticles);
    }

    private void doRefreshPublishedArticleVectorIndex(
            String articleKey,
            List<ArticleReviewEnvelope> fallbackReviewedArticles
    ) {
        try {
            ArticleRecord publishedArticle = articleJdbcRepository.findByArticleKey(articleKey)
                    .orElseGet(() -> fallbackReviewedArticles.get(0).getArticle());
            ArticleReviewEnvelope reviewEnvelope = approvedEnvelope(publishedArticle);
            articlePersistSupport.refreshVectorIndex(List.of(reviewEnvelope));
        }
        catch (RuntimeException ex) {
            log.warn("Human review approved vector refresh failed for articleKey: {}", articleKey, ex);
        }
    }

    /**
     * 创建向量刷新独立事务模板。
     *
     * @param transactionManagerProvider 事务管理器提供者
     * @return 事务模板
     */
    private static TransactionTemplate buildVectorRefreshTransactionTemplate(
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider
    ) {
        if (transactionManagerProvider == null) {
            return null;
        }
        PlatformTransactionManager transactionManager = transactionManagerProvider.getIfAvailable();
        if (transactionManager == null) {
            return null;
        }
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }

    /**
     * 人工驳回草稿。
     *
     * @param id 队列主键
     * @param request 动作请求
     * @return 动作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public CompileArticleReviewQueueActionResult reject(
            long id,
            CompileArticleReviewQueueActionRequest request
    ) {
        CompileArticleReviewQueueRecord queueRecord = requireQueueRecord(id);
        if (STATUS_REJECTED.equalsIgnoreCase(queueRecord.getReviewStatus())) {
            return new CompileArticleReviewQueueActionResult(queueRecord, STATUS_REJECTED, 0L);
        }
        assertPending(queueRecord, request);
        OffsetDateTime reviewedAt = OffsetDateTime.now();
        ArticleRecord draftArticle = toDraftArticle(queueRecord);
        ArticleReviewAuditRecord savedAudit = saveAudit(
                draftArticle,
                AUDIT_ACTION_REJECT,
                STATUS_NEEDS_HUMAN_REVIEW,
                STATUS_REJECTED,
                request,
                reviewedAt,
                buildAuditMetadata(queueRecord)
        );
        boolean updated = compileArticleReviewQueueJdbcRepository.markRejected(
                queueRecord.getId(),
                normalizeText(request == null ? null : request.getReviewedBy()),
                reviewedAt,
                normalizeText(request == null ? null : request.getComment())
        );
        if (!updated) {
            throw new IllegalStateException("compile review queue status changed: id=" + queueRecord.getId());
        }
        CompileArticleReviewQueueRecord updatedQueueRecord = requireQueueRecord(id);
        return new CompileArticleReviewQueueActionResult(
                updatedQueueRecord,
                STATUS_NEEDS_HUMAN_REVIEW,
                savedAudit.getId()
        );
    }

    private CompileArticleReviewQueueRecord requireQueueRecord(long id) {
        return compileArticleReviewQueueJdbcRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("compile review queue item not found: " + id));
    }

    private void assertPending(
            CompileArticleReviewQueueRecord queueRecord,
            CompileArticleReviewQueueActionRequest request
    ) {
        String expectedStatus = normalizeStatus(request == null ? null : request.getExpectedReviewStatus());
        if (expectedStatus != null && !expectedStatus.equals(normalizeStatus(queueRecord.getReviewStatus()))) {
            throw new IllegalStateException(
                    "compile review queue status changed: expected=" + expectedStatus
                            + ", actual=" + queueRecord.getReviewStatus()
            );
        }
        if (!STATUS_NEEDS_HUMAN_REVIEW.equalsIgnoreCase(queueRecord.getReviewStatus())) {
            throw new IllegalStateException(
                    "compile review queue item is not pending human review: " + queueRecord.getReviewStatus()
            );
        }
    }

    private void assertNoArticleConflict(CompileArticleReviewQueueRecord queueRecord) {
        if (articleJdbcRepository.findByArticleKey(queueRecord.getArticleKey()).isPresent()) {
            throw new IllegalStateException("article already exists: " + queueRecord.getArticleKey());
        }
        Long sourceId = queueRecord.getSourceId();
        if (sourceId == null) {
            return;
        }
        if (articleJdbcRepository.findBySourceIdAndConceptId(sourceId, queueRecord.getConceptId()).isPresent()) {
            throw new IllegalStateException(
                    "article already exists for source and concept: " + queueRecord.getConceptId()
            );
        }
    }

    private ArticleReviewEnvelope approvedEnvelope(ArticleRecord articleRecord) {
        ArticleReviewEnvelope reviewEnvelope = new ArticleReviewEnvelope();
        reviewEnvelope.setArticle(articleRecord);
        reviewEnvelope.setReviewResult(ReviewResult.passed());
        reviewEnvelope.setReviewStatus(REVIEW_STATUS_PASSED);
        return reviewEnvelope;
    }

    private ArticleRecord toApprovedArticle(
            CompileArticleReviewQueueRecord queueRecord,
            OffsetDateTime reviewedAt
    ) {
        String normalizedContent = ArticleMarkdownSupport.normalizeReviewStatus(
                queueRecord.getContent(),
                REVIEW_STATUS_PASSED
        );
        String metadataJson = mergeHumanReviewMetadata(queueRecord, reviewedAt);
        ArticleRecord articleRecord = toDraftArticle(queueRecord);
        return articleRecord.copy(
                articleRecord.getTitle(),
                normalizedContent,
                LIFECYCLE_ACTIVE,
                articleRecord.getCompiledAt(),
                articleRecord.getSourcePaths(),
                metadataJson,
                articleRecord.getSummary(),
                articleRecord.getReferentialKeywords(),
                articleRecord.getDependsOn(),
                articleRecord.getRelated(),
                articleRecord.getConfidence(),
                REVIEW_STATUS_PASSED
        );
    }

    private ArticleRecord toDraftArticle(CompileArticleReviewQueueRecord queueRecord) {
        return new ArticleRecord(
                queueRecord.getSourceId(),
                queueRecord.getArticleKey(),
                queueRecord.getConceptId(),
                queueRecord.getTitle(),
                queueRecord.getContent(),
                queueRecord.getLifecycle(),
                queueRecord.getCompiledAt(),
                queueRecord.getSourcePaths(),
                queueRecord.getMetadataJson(),
                "",
                List.of(),
                List.of(),
                List.of(),
                "medium",
                queueRecord.getReviewStatus()
        );
    }

    private Map<String, Long> resolveSourceFileIdsByPath(CompileArticleReviewQueueRecord queueRecord) {
        Map<String, Long> sourceFileIdsByPath = new LinkedHashMap<String, Long>();
        if (queueRecord.getSourceId() == null || queueRecord.getSourcePaths() == null) {
            return sourceFileIdsByPath;
        }
        for (String sourcePath : queueRecord.getSourcePaths()) {
            if (sourcePath == null || sourcePath.isBlank()) {
                continue;
            }
            SourceFileRecord sourceFileRecord = sourceFileJdbcRepository
                    .findBySourceIdAndRelativePath(queueRecord.getSourceId(), sourcePath)
                    .orElse(null);
            if (sourceFileRecord == null || sourceFileRecord.getId() == null) {
                throw new IllegalStateException("source file id missing for article path: " + sourcePath);
            }
            sourceFileIdsByPath.put(sourcePath, sourceFileRecord.getId());
        }
        return sourceFileIdsByPath;
    }

    private ArticleReviewAuditRecord saveAudit(
            ArticleRecord articleRecord,
            String action,
            String previousReviewStatus,
            String nextReviewStatus,
            CompileArticleReviewQueueActionRequest request,
            OffsetDateTime reviewedAt,
            String metadataJson
    ) {
        ArticleReviewAuditRecord auditRecord = ArticleReviewAuditRecord.fromArticle(
                articleRecord,
                action,
                previousReviewStatus,
                nextReviewStatus,
                normalizeText(request == null ? null : request.getComment()),
                normalizeText(request == null ? null : request.getReviewedBy()),
                reviewedAt,
                metadataJson
        );
        return articleReviewAuditJdbcRepository.save(auditRecord);
    }

    private String mergeHumanReviewMetadata(
            CompileArticleReviewQueueRecord queueRecord,
            OffsetDateTime reviewedAt
    ) {
        ObjectNode metadataNode = readObjectNode(queueRecord.getMetadataJson());
        ObjectNode humanReviewNode = OBJECT_MAPPER.createObjectNode();
        humanReviewNode.put("source", "compile_review_queue");
        humanReviewNode.put("queueId", queueRecord.getId());
        if (reviewedAt != null) {
            humanReviewNode.put("reviewedAt", reviewedAt.toString());
        }
        metadataNode.set("humanReview", humanReviewNode);
        return metadataNode.toString();
    }

    private String buildAuditMetadata(CompileArticleReviewQueueRecord queueRecord) {
        ObjectNode metadataNode = OBJECT_MAPPER.createObjectNode();
        metadataNode.put("source", "compile_review_queue");
        metadataNode.put("queueId", queueRecord.getId());
        metadataNode.put("jobId", queueRecord.getJobId());
        return metadataNode.toString();
    }

    private ObjectNode readObjectNode(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            JsonNode metadataNode = OBJECT_MAPPER.readTree(metadataJson);
            if (metadataNode instanceof ObjectNode) {
                return (ObjectNode) metadataNode;
            }
            return OBJECT_MAPPER.createObjectNode();
        }
        catch (Exception ex) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private String normalizeStatus(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return null;
        }
        return normalizedValue.toLowerCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) {
            return null;
        }
        return trimmedValue;
    }

}
