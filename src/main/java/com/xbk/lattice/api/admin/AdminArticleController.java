package com.xbk.lattice.api.admin;

import com.xbk.lattice.admin.service.AdminArticleQueryService;
import com.xbk.lattice.governance.ArticleCorrectionResult;
import com.xbk.lattice.governance.ArticleCorrectionService;
import com.xbk.lattice.governance.LifecycleService;
import com.xbk.lattice.governance.domain.LifecycleTransitionResult;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
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
@Profile("jdbc")
@RequestMapping("/api/v1/admin/articles")
public class AdminArticleController {

    private final AdminArticleQueryService adminArticleQueryService;

    private final LifecycleService lifecycleService;

    private final ArticleCorrectionService articleCorrectionService;

    /**
     * 创建管理侧文章控制器。
     *
     * @param adminArticleQueryService 管理侧文章查询服务
     * @param lifecycleService 生命周期服务
     * @param articleCorrectionService 文章纠错服务
     */
    public AdminArticleController(
            AdminArticleQueryService adminArticleQueryService,
            LifecycleService lifecycleService,
            ArticleCorrectionService articleCorrectionService
    ) {
        this.adminArticleQueryService = adminArticleQueryService;
        this.lifecycleService = lifecycleService;
        this.articleCorrectionService = articleCorrectionService;
    }

    /**
     * 返回管理侧文章列表。
     *
     * @param query 关键字
     * @param lifecycle 生命周期
     * @return 文章列表
     */
    @GetMapping
    public AdminArticleListResponse list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String lifecycle,
            @RequestParam(required = false) Long sourceId
    ) {
        List<ArticleRecord> articleRecords = adminArticleQueryService.list(query, lifecycle, sourceId);
        List<AdminArticleSummaryResponse> items = new ArrayList<AdminArticleSummaryResponse>();
        for (ArticleRecord articleRecord : articleRecords) {
            items.add(new AdminArticleSummaryResponse(
                    articleRecord.getSourceId(),
                    articleRecord.getArticleKey(),
                    articleRecord.getConceptId(),
                    articleRecord.getTitle(),
                    articleRecord.getLifecycle(),
                    articleRecord.getReviewStatus(),
                    articleRecord.getCompiledAt() == null ? null : articleRecord.getCompiledAt().toString(),
                    articleRecord.getSummary(),
                    articleRecord.getSourcePaths().size(),
                    articleRecord.getSourcePaths().isEmpty() ? null : articleRecord.getSourcePaths().get(0)
            ));
        }
        return new AdminArticleListResponse(items.size(), items);
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
        return new AdminArticleDetailResponse(
                articleRecord.getSourceId(),
                articleRecord.getArticleKey(),
                articleRecord.getConceptId(),
                articleRecord.getTitle(),
                articleRecord.getContent(),
                articleRecord.getLifecycle(),
                articleRecord.getCompiledAt() == null ? null : articleRecord.getCompiledAt().toString(),
                articleRecord.getSummary(),
                articleRecord.getReviewStatus(),
                articleRecord.getConfidence(),
                articleRecord.getSourcePaths(),
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
            @PathVariable String conceptId,
            @PathVariable String action,
            @RequestBody AdminLifecycleRequest lifecycleRequest
    ) {
        String normalizedAction = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if ("deprecate".equals(normalizedAction)) {
            return lifecycleService.deprecate(conceptId, lifecycleRequest.getReason(), lifecycleRequest.getUpdatedBy());
        }
        if ("archive".equals(normalizedAction)) {
            return lifecycleService.archive(conceptId, lifecycleRequest.getReason(), lifecycleRequest.getUpdatedBy());
        }
        if ("activate".equals(normalizedAction)) {
            return lifecycleService.activate(conceptId, lifecycleRequest.getReason(), lifecycleRequest.getUpdatedBy());
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
            @PathVariable String conceptId,
            @RequestBody AdminArticleCorrectionRequest request
    ) {
        return articleCorrectionService.correct(conceptId, request.getCorrectionSummary());
    }
}
