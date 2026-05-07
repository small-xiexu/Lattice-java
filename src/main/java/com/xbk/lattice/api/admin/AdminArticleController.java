package com.xbk.lattice.api.admin;

import com.xbk.lattice.admin.service.AdminArticleQueryService;
import com.xbk.lattice.governance.ArticleCorrectionResult;
import com.xbk.lattice.governance.ArticleCorrectionService;
import com.xbk.lattice.governance.ArticleHotspotRefreshResult;
import com.xbk.lattice.governance.ArticleHotspotRefreshService;
import com.xbk.lattice.governance.ArticleManualReviewRequest;
import com.xbk.lattice.governance.ArticleManualReviewResult;
import com.xbk.lattice.governance.ArticleManualReviewService;
import com.xbk.lattice.governance.LifecycleService;
import com.xbk.lattice.governance.domain.LifecycleTransitionResult;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleReviewAuditRecord;
import com.xbk.lattice.infra.persistence.ArticleUsageStatsRecord;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 管理侧文章控制器
 *
 * 职责：暴露文章浏览与生命周期管理接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/articles")
public class AdminArticleController {

    private final AdminArticleQueryService adminArticleQueryService;

    private final LifecycleService lifecycleService;

    private final ArticleCorrectionService articleCorrectionService;

    private final ArticleManualReviewService articleManualReviewService;

    private final ArticleHotspotRefreshService articleHotspotRefreshService;

    /**
     * 创建管理侧文章控制器。
     *
     * @param adminArticleQueryService 管理侧文章查询服务
     * @param lifecycleService 生命周期服务
     * @param articleCorrectionService 文章纠错服务
     * @param articleManualReviewService 文章人工复核服务
     * @param articleHotspotRefreshService 文章热点刷新服务
     */
    public AdminArticleController(
            AdminArticleQueryService adminArticleQueryService,
            LifecycleService lifecycleService,
            ArticleCorrectionService articleCorrectionService,
            ArticleManualReviewService articleManualReviewService,
            ArticleHotspotRefreshService articleHotspotRefreshService
    ) {
        this.adminArticleQueryService = adminArticleQueryService;
        this.lifecycleService = lifecycleService;
        this.articleCorrectionService = articleCorrectionService;
        this.articleManualReviewService = articleManualReviewService;
        this.articleHotspotRefreshService = articleHotspotRefreshService;
    }

    /**
     * 返回管理侧文章列表。
     *
     * @param query 关键字
     * @param lifecycle 生命周期
     * @param sourceId 资料源主键
     * @param reviewStatus 复核状态
     * @param riskLevel 风险等级
     * @param riskReason 风险原因
     * @param isHotspot 是否热点
     * @param requiresResultVerification 是否需要结果抽检
     * @return 文章列表
     */
    @GetMapping
    public AdminArticleListResponse list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String lifecycle,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String riskReason,
            @RequestParam(required = false) Boolean isHotspot,
            @RequestParam(required = false) Boolean requiresResultVerification
    ) {
        List<ArticleRecord> articleRecords = adminArticleQueryService.list(
                query,
                lifecycle,
                sourceId,
                reviewStatus,
                riskLevel,
                riskReason,
                isHotspot,
                requiresResultVerification
        );
        List<AdminArticleSummaryResponse> items = new ArrayList<AdminArticleSummaryResponse>();
        for (ArticleRecord articleRecord : articleRecords) {
            List<String> sourcePaths = articleRecord.getSourcePaths() == null
                    ? Collections.<String>emptyList()
                    : articleRecord.getSourcePaths();
            String compiledAt = articleRecord.getCompiledAt() == null ? null : articleRecord.getCompiledAt().toString();
            String createdAt = articleRecord.getCreatedAt() == null ? null : articleRecord.getCreatedAt().toString();
            String updatedAt = articleRecord.getUpdatedAt() == null ? null : articleRecord.getUpdatedAt().toString();
            int resolvedSourceCount = sourcePaths.size();
            String primarySourcePath = sourcePaths.isEmpty() ? null : sourcePaths.get(0);
            items.add(new AdminArticleSummaryResponse(
                    articleRecord.getSourceId(),
                    articleRecord.getArticleKey(),
                    articleRecord.getConceptId(),
                    articleRecord.getTitle(),
                    articleRecord.getLifecycle(),
                    articleRecord.getReviewStatus(),
                    articleRecord.getRiskLevel(),
                    articleRecord.getRiskReasons(),
                    articleRecord.isHotspot(),
                    articleRecord.isRequiresResultVerification(),
                    compiledAt,
                    createdAt,
                    updatedAt,
                    articleRecord.getSummary(),
                    resolvedSourceCount,
                    primarySourcePath,
                    sourcePaths,
                    primarySourcePath
            ));
        }
        return new AdminArticleListResponse(items.size(), items);
    }

    /**
     * 刷新文章热点统计并生成待抽检队列。
     *
     * @param request 热点刷新请求
     * @return 热点刷新结果
     */
    @PostMapping("/hotspots/refresh")
    public AdminArticleHotspotRefreshResponse refreshHotspots(
            @Valid @RequestBody(required = false) AdminArticleHotspotRefreshRequest request
    ) {
        AdminArticleHotspotRefreshRequest safeRequest = request == null
                ? new AdminArticleHotspotRefreshRequest()
                : request;
        Integer requestedThreshold = safeRequest.getHeatScoreThreshold();
        Integer requestedLimit = safeRequest.getLimit();
        int heatScoreThreshold = requestedThreshold == null
                ? ArticleHotspotRefreshService.DEFAULT_HEAT_SCORE_THRESHOLD
                : requestedThreshold.intValue();
        int limit = requestedLimit == null ? 0 : requestedLimit.intValue();
        ArticleHotspotRefreshResult result = articleHotspotRefreshService.refresh(heatScoreThreshold, limit);
        return toHotspotRefreshResponse(result);
    }

    /**
     * 返回单篇文章详情。
     *
     * @param articleId 文章唯一键或概念标识
     * @param sourceId 可选资料源主键
     * @return 文章详情
     */
    @GetMapping("/{articleId}")
    public AdminArticleDetailResponse detail(
            @PathVariable String articleId,
            @RequestParam(required = false) Long sourceId
    ) {
        ArticleRecord articleRecord = adminArticleQueryService.get(articleId, sourceId);
        List<String> sourcePaths = articleRecord.getSourcePaths() == null
                ? Collections.<String>emptyList()
                : articleRecord.getSourcePaths();
        String compiledAt = articleRecord.getCompiledAt() == null ? null : articleRecord.getCompiledAt().toString();
        String createdAt = articleRecord.getCreatedAt() == null ? null : articleRecord.getCreatedAt().toString();
        String updatedAt = articleRecord.getUpdatedAt() == null ? null : articleRecord.getUpdatedAt().toString();
        int resolvedSourceCount = sourcePaths.size();
        String primarySourcePath = sourcePaths.isEmpty() ? null : sourcePaths.get(0);
        return new AdminArticleDetailResponse(
                articleRecord.getSourceId(),
                articleRecord.getArticleKey(),
                articleRecord.getConceptId(),
                articleRecord.getTitle(),
                articleRecord.getContent(),
                articleRecord.getLifecycle(),
                compiledAt,
                createdAt,
                updatedAt,
                articleRecord.getSummary(),
                articleRecord.getReviewStatus(),
                articleRecord.getRiskLevel(),
                articleRecord.getRiskReasons(),
                articleRecord.isHotspot(),
                articleRecord.isRequiresResultVerification(),
                articleRecord.getConfidence(),
                resolvedSourceCount,
                primarySourcePath,
                sourcePaths,
                articleRecord.getReferentialKeywords(),
                articleRecord.getDependsOn(),
                articleRecord.getRelated(),
                articleRecord.getMetadataJson()
        );
    }

    /**
     * 切换文章生命周期。
     *
     * @param conceptId 概念标识
     * @param action 生命周期动作
     * @param lifecycleRequest 生命周期请求
     * @return 切换结果
     */
    @PostMapping("/{conceptId}/lifecycle/{action}")
    public LifecycleTransitionResult transitionLifecycle(
            @PathVariable("conceptId") String articleId,
            @PathVariable String action,
            @RequestBody AdminLifecycleRequest lifecycleRequest,
            @RequestParam(required = false) Long sourceId
    ) {
        String normalizedAction = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if ("deprecate".equals(normalizedAction)) {
            return lifecycleService.deprecate(articleId, sourceId, lifecycleRequest.getReason(), lifecycleRequest.getUpdatedBy());
        }
        if ("archive".equals(normalizedAction)) {
            return lifecycleService.archive(articleId, sourceId, lifecycleRequest.getReason(), lifecycleRequest.getUpdatedBy());
        }
        if ("activate".equals(normalizedAction)) {
            return lifecycleService.activate(articleId, sourceId, lifecycleRequest.getReason(), lifecycleRequest.getUpdatedBy());
        }
        throw new IllegalArgumentException("unsupported lifecycle action: " + action);
    }

    /**
     * 纠正单篇文章。
     *
     * @param conceptId 概念标识
     * @param request 纠错请求
     * @return 纠错结果
     */
    @PostMapping("/{conceptId}/correct")
    public ArticleCorrectionResult correct(
            @PathVariable("conceptId") String articleId,
            @RequestBody AdminArticleCorrectionRequest request,
            @RequestParam(required = false) Long sourceId
    ) {
        return articleCorrectionService.correct(articleId, sourceId, request.getCorrectionSummary());
    }

    /**
     * 人工确认文章通过复核。
     *
     * @param articleId 文章唯一键或概念标识
     * @param request 复核请求
     * @return 复核结果
     */
    @PostMapping("/{articleId}/review/approve")
    public AdminArticleReviewResponse approve(
            @PathVariable String articleId,
            @RequestBody AdminArticleReviewRequest request
    ) {
        ArticleManualReviewResult result = articleManualReviewService.approve(articleId, toServiceRequest(request));
        return toReviewResponse(result);
    }

    /**
     * 提交文章修正请求。
     *
     * @param articleId 文章唯一键或概念标识
     * @param request 复核请求
     * @return 复核结果
     */
    @PostMapping("/{articleId}/review/request-changes")
    public AdminArticleReviewResponse requestChanges(
            @PathVariable String articleId,
            @RequestBody AdminArticleReviewRequest request
    ) {
        ArticleManualReviewResult result = articleManualReviewService.requestChanges(articleId, toServiceRequest(request));
        return toReviewResponse(result);
    }

    /**
     * 查询文章人工复核历史。
     *
     * @param articleId 文章唯一键或概念标识
     * @param sourceId 资料源主键
     * @return 复核历史
     */
    @GetMapping("/{articleId}/review/audits")
    public AdminArticleReviewAuditListResponse listReviewAudits(
            @PathVariable String articleId,
            @RequestParam(required = false) Long sourceId
    ) {
        List<ArticleReviewAuditRecord> auditRecords = articleManualReviewService.listAudits(articleId, sourceId);
        List<AdminArticleReviewAuditResponse> items = new ArrayList<AdminArticleReviewAuditResponse>();
        for (ArticleReviewAuditRecord auditRecord : auditRecords) {
            items.add(toAuditResponse(auditRecord));
        }
        return new AdminArticleReviewAuditListResponse(items.size(), items);
    }

    /**
     * 转换为服务层复核请求。
     *
     * @param request Admin 请求
     * @return 服务层请求
     */
    private ArticleManualReviewRequest toServiceRequest(AdminArticleReviewRequest request) {
        AdminArticleReviewRequest safeRequest = request == null ? new AdminArticleReviewRequest() : request;
        return new ArticleManualReviewRequest(
                safeRequest.getSourceId(),
                safeRequest.getReviewedBy(),
                safeRequest.getComment(),
                safeRequest.getExpectedReviewStatus(),
                safeRequest.getCorrectionSummary()
        );
    }

    /**
     * 转换为复核响应。
     *
     * @param result 复核结果
     * @return 响应
     */
    private AdminArticleReviewResponse toReviewResponse(ArticleManualReviewResult result) {
        String reviewedAt = result.getReviewedAt() == null ? null : result.getReviewedAt().toString();
        return new AdminArticleReviewResponse(
                result.getSourceId(),
                result.getArticleKey(),
                result.getConceptId(),
                result.getPreviousReviewStatus(),
                result.getReviewStatus(),
                result.getReviewedBy(),
                reviewedAt,
                result.getAuditId()
        );
    }

    /**
     * 转换为审计响应。
     *
     * @param auditRecord 审计记录
     * @return 响应
     */
    private AdminArticleReviewAuditResponse toAuditResponse(ArticleReviewAuditRecord auditRecord) {
        String reviewedAt = auditRecord.getReviewedAt() == null ? null : auditRecord.getReviewedAt().toString();
        return new AdminArticleReviewAuditResponse(
                auditRecord.getId(),
                auditRecord.getSourceId(),
                auditRecord.getArticleKey(),
                auditRecord.getConceptId(),
                auditRecord.getAction(),
                auditRecord.getPreviousReviewStatus(),
                auditRecord.getNextReviewStatus(),
                auditRecord.getComment(),
                auditRecord.getReviewedBy(),
                reviewedAt,
                auditRecord.getMetadataJson()
        );
    }

    /**
     * 转换为热点刷新响应。
     *
     * @param result 热点刷新结果
     * @return 响应
     */
    private AdminArticleHotspotRefreshResponse toHotspotRefreshResponse(ArticleHotspotRefreshResult result) {
        List<AdminArticleUsageStatsResponse> candidates = new ArrayList<AdminArticleUsageStatsResponse>();
        for (ArticleUsageStatsRecord candidate : result.getCandidates()) {
            candidates.add(toUsageStatsResponse(candidate));
        }
        return new AdminArticleHotspotRefreshResponse(
                result.getRebuiltStatsCount(),
                result.getHotspotCandidateCount(),
                result.getUpdatedArticleCount(),
                result.getHeatScoreThreshold(),
                candidates
        );
    }

    /**
     * 转换为文章使用热度统计响应。
     *
     * @param record 文章使用热度统计
     * @return 响应
     */
    private AdminArticleUsageStatsResponse toUsageStatsResponse(ArticleUsageStatsRecord record) {
        String updatedAt = record.getUpdatedAt() == null ? null : record.getUpdatedAt().toString();
        return new AdminArticleUsageStatsResponse(
                record.getArticleKey(),
                record.getConceptId(),
                record.getRetrievalHitCount(),
                record.getCitationCount(),
                record.getAnswerFeedbackCount(),
                record.getManualMarkCount(),
                record.getHeatScore(),
                record.getSourcePaths(),
                updatedAt
        );
    }
}
