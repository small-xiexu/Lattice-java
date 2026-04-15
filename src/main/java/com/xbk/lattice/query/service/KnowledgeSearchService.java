package com.xbk.lattice.query.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识检索服务
 *
 * 职责：为 MCP 与治理侧提供不生成答案的多路融合检索能力
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class KnowledgeSearchService {

    private final FtsSearchService ftsSearchService;

    private final RefKeySearchService refKeySearchService;

    private final SourceSearchService sourceSearchService;

    private final ContributionSearchService contributionSearchService;

    private final RrfFusionService rrfFusionService;

    /**
     * 创建知识检索服务。
     *
     * @param ftsSearchService 文章 FTS 检索
     * @param refKeySearchService referential keywords 检索
     * @param sourceSearchService 源文件检索
     * @param contributionSearchService contribution 检索
     * @param rrfFusionService RRF 融合服务
     */
    public KnowledgeSearchService(
            FtsSearchService ftsSearchService,
            RefKeySearchService refKeySearchService,
            SourceSearchService sourceSearchService,
            ContributionSearchService contributionSearchService,
            RrfFusionService rrfFusionService
    ) {
        this.ftsSearchService = ftsSearchService;
        this.refKeySearchService = refKeySearchService;
        this.sourceSearchService = sourceSearchService;
        this.contributionSearchService = contributionSearchService;
        this.rrfFusionService = rrfFusionService;
    }

    /**
     * 执行多路融合检索。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @return 融合命中列表
     */
    public List<QueryArticleHit> search(String question, int limit) {
        int safeLimit = limit <= 0 ? 5 : limit;
        List<QueryArticleHit> ftsHits = ftsSearchService.search(question, safeLimit);
        List<QueryArticleHit> refKeyHits = refKeySearchService.search(question, safeLimit);
        List<QueryArticleHit> sourceHits = sourceSearchService.search(question, safeLimit);
        List<QueryArticleHit> contributionHits = contributionSearchService.search(question, safeLimit);
        return rrfFusionService.fuse(
                List.of(ftsHits, refKeyHits, sourceHits, contributionHits),
                safeLimit
        );
    }
}
