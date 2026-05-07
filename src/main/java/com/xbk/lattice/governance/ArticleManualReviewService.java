package com.xbk.lattice.governance;

import com.xbk.lattice.article.service.ArticleIdentityResolver;
import com.xbk.lattice.article.service.ArticleMarkdownSupport;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleReviewAuditJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleReviewAuditRecord;
import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 文章人工复核服务
 *
 * 职责：编排人工确认通过、提交修正、审计留痕和快照保存
 *
 * @author xiexu
 */
@Service
public class ArticleManualReviewService {

    public static final String ACTION_APPROVE = "approve";

    public static final String ACTION_REQUEST_CHANGES = "request_changes";

    private static final String REVIEW_STATUS_PASSED = "passed";

    private static final String REVIEW_STATUS_NEEDS_REVIEW = "needs_review";

    private static final String SNAPSHOT_REASON_MANUAL_APPROVE = "manual_review_approve";

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ArticleIdentityResolver articleIdentityResolver;

    private final ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository;

    private final ArticleReviewAuditJdbcRepository articleReviewAuditJdbcRepository;

    private final ArticleCorrectionService articleCorrectionService;

    /**
     * 创建文章人工复核服务。
     *
     * @param articleJdbcRepository 文章仓储
     * @param articleIdentityResolver 文章身份解析服务
     * @param articleSnapshotJdbcRepository 文章快照仓储
     * @param articleReviewAuditJdbcRepository 人工复核审计仓储
     * @param articleCorrectionService 文章纠错服务
     */
    public ArticleManualReviewService(
            ArticleJdbcRepository articleJdbcRepository,
            ArticleIdentityResolver articleIdentityResolver,
            ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository,
            ArticleReviewAuditJdbcRepository articleReviewAuditJdbcRepository,
            ArticleCorrectionService articleCorrectionService
    ) {
        this.articleJdbcRepository = articleJdbcRepository;
        this.articleIdentityResolver = articleIdentityResolver;
        this.articleSnapshotJdbcRepository = articleSnapshotJdbcRepository;
        this.articleReviewAuditJdbcRepository = articleReviewAuditJdbcRepository;
        this.articleCorrectionService = articleCorrectionService;
    }

    /**
     * 人工确认文章通过复核。
     *
     * @param articleId 文章唯一键或概念标识
     * @param request 复核请求
     * @return 复核结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ArticleManualReviewResult approve(String articleId, ArticleManualReviewRequest request) {
        ArticleRecord articleRecord = resolveArticle(articleId, request);
        assertExpectedStatus(articleRecord, request);
        OffsetDateTime reviewedAt = OffsetDateTime.now();
        String previousReviewStatus = articleRecord.getReviewStatus();
        String normalizedContent = ArticleMarkdownSupport.normalizeReviewStatus(
                articleRecord.getContent(),
                REVIEW_STATUS_PASSED
        );
        ArticleRecord updatedRecord = articleRecord.copy(
                articleRecord.getTitle(),
                normalizedContent,
                articleRecord.getLifecycle(),
                articleRecord.getCompiledAt(),
                articleRecord.getSourcePaths(),
                articleRecord.getMetadataJson(),
                articleRecord.getSummary(),
                articleRecord.getReferentialKeywords(),
                articleRecord.getDependsOn(),
                articleRecord.getRelated(),
                articleRecord.getConfidence(),
                REVIEW_STATUS_PASSED
        );
        articleJdbcRepository.upsert(updatedRecord);
        articleSnapshotJdbcRepository.save(ArticleSnapshotRecord.fromArticle(
                updatedRecord,
                SNAPSHOT_REASON_MANUAL_APPROVE,
                reviewedAt
        ));
        ArticleReviewAuditRecord savedAudit = saveAudit(
                updatedRecord,
                ACTION_APPROVE,
                previousReviewStatus,
                REVIEW_STATUS_PASSED,
                request,
                reviewedAt,
                "{}"
        );
        return toResult(updatedRecord, previousReviewStatus, REVIEW_STATUS_PASSED, request, reviewedAt, savedAudit);
    }

    /**
     * 提交文章修正请求。
     *
     * @param articleId 文章唯一键或概念标识
     * @param request 复核请求
     * @return 复核结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ArticleManualReviewResult requestChanges(String articleId, ArticleManualReviewRequest request) {
        ArticleRecord articleRecord = resolveArticle(articleId, request);
        assertExpectedStatus(articleRecord, request);
        String correctionSummary = requireText(
                request == null ? null : request.getCorrectionSummary(),
                "correctionSummary must not be blank"
        );
        OffsetDateTime reviewedAt = OffsetDateTime.now();
        String previousReviewStatus = articleRecord.getReviewStatus();
        ArticleCorrectionResult correctionResult = articleCorrectionService.correct(
                articleId,
                request == null ? null : request.getSourceId(),
                correctionSummary
        );
        ArticleReviewAuditRecord savedAudit = saveAudit(
                articleRecord,
                ACTION_REQUEST_CHANGES,
                previousReviewStatus,
                REVIEW_STATUS_NEEDS_REVIEW,
                request,
                reviewedAt,
                buildCorrectionMetadataJson(correctionResult)
        );
        return new ArticleManualReviewResult(
                articleRecord.getSourceId(),
                articleRecord.getArticleKey(),
                articleRecord.getConceptId(),
                previousReviewStatus,
                REVIEW_STATUS_NEEDS_REVIEW,
                normalizeText(request == null ? null : request.getReviewedBy()),
                reviewedAt,
                savedAudit.getId()
        );
    }

    /**
     * 查询文章人工复核审计历史。
     *
     * @param articleId 文章唯一键或概念标识
     * @param sourceId 资料源主键
     * @return 审计记录列表
     */
    public List<ArticleReviewAuditRecord> listAudits(String articleId, Long sourceId) {
        ArticleRecord articleRecord = articleIdentityResolver.require(articleId, sourceId);
        return articleReviewAuditJdbcRepository.findByArticle(articleRecord);
    }

    /**
     * 解析文章。
     *
     * @param articleId 文章唯一键或概念标识
     * @param request 复核请求
     * @return 文章记录
     */
    private ArticleRecord resolveArticle(String articleId, ArticleManualReviewRequest request) {
        Long sourceId = request == null ? null : request.getSourceId();
        return articleIdentityResolver.require(articleId, sourceId);
    }

    /**
     * 校验期望状态。
     *
     * @param articleRecord 文章记录
     * @param request 复核请求
     */
    private void assertExpectedStatus(ArticleRecord articleRecord, ArticleManualReviewRequest request) {
        String expectedReviewStatus = normalizeStatus(request == null ? null : request.getExpectedReviewStatus());
        if (expectedReviewStatus == null) {
            return;
        }
        String currentReviewStatus = normalizeStatus(articleRecord.getReviewStatus());
        if (!expectedReviewStatus.equals(currentReviewStatus)) {
            throw new IllegalStateException(
                    "review status changed: expected=" + expectedReviewStatus + ", actual=" + currentReviewStatus
            );
        }
    }

    /**
     * 保存审计记录。
     *
     * @param articleRecord 文章记录
     * @param action 复核动作
     * @param previousReviewStatus 复核前状态
     * @param nextReviewStatus 复核后状态
     * @param request 复核请求
     * @param reviewedAt 复核时间
     * @param metadataJson 元数据 JSON
     * @return 保存后的审计
     */
    private ArticleReviewAuditRecord saveAudit(
            ArticleRecord articleRecord,
            String action,
            String previousReviewStatus,
            String nextReviewStatus,
            ArticleManualReviewRequest request,
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

    /**
     * 转换为复核结果。
     *
     * @param articleRecord 文章记录
     * @param previousReviewStatus 复核前状态
     * @param nextReviewStatus 复核后状态
     * @param request 复核请求
     * @param reviewedAt 复核时间
     * @param savedAudit 保存后的审计
     * @return 复核结果
     */
    private ArticleManualReviewResult toResult(
            ArticleRecord articleRecord,
            String previousReviewStatus,
            String nextReviewStatus,
            ArticleManualReviewRequest request,
            OffsetDateTime reviewedAt,
            ArticleReviewAuditRecord savedAudit
    ) {
        return new ArticleManualReviewResult(
                articleRecord.getSourceId(),
                articleRecord.getArticleKey(),
                articleRecord.getConceptId(),
                previousReviewStatus,
                nextReviewStatus,
                normalizeText(request == null ? null : request.getReviewedBy()),
                reviewedAt,
                savedAudit.getId()
        );
    }

    /**
     * 构造纠错元数据 JSON。
     *
     * @param correctionResult 纠错结果
     * @return 元数据 JSON
     */
    private String buildCorrectionMetadataJson(ArticleCorrectionResult correctionResult) {
        if (correctionResult == null) {
            return "{}";
        }
        return "{\"validationSupported\":" + correctionResult.isValidationSupported() + "}";
    }

    /**
     * 要求文本非空。
     *
     * @param value 文本
     * @param message 异常消息
     * @return 规范化文本
     */
    private String requireText(String value, String message) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            throw new IllegalArgumentException(message);
        }
        return normalizedValue;
    }

    /**
     * 规范化状态文本。
     *
     * @param value 原始文本
     * @return 规范化状态
     */
    private String normalizeStatus(String value) {
        String normalizedValue = normalizeText(value);
        return normalizedValue == null ? null : normalizedValue.toLowerCase(Locale.ROOT);
    }

    /**
     * 规范化文本。
     *
     * @param value 原始文本
     * @return 规范化文本
     */
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
