package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryArticleResponse;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.api.query.QuerySourceResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 查询门面服务
 *
 * 职责：串联最小查询闭环的检索、融合和答案生成
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class QueryFacadeService {

    private static final int TOP_K = 5;

    private final FtsSearchService ftsSearchService;

    private final RefKeySearchService refKeySearchService;

    private final SourceSearchService sourceSearchService;

    private final ContributionSearchService contributionSearchService;

    private final VectorSearchService vectorSearchService;

    private final RrfFusionService rrfFusionService;

    private final AnswerGenerationService answerGenerationService;

    private final QueryCacheStore queryCacheStore;

    private final ReviewerAgent reviewerAgent;

    private final PendingQueryManager pendingQueryManager;

    /**
     * 创建查询门面服务。
     *
     * @param ftsSearchService FTS 检索服务
     * @param refKeySearchService 引用词检索服务
     * @param sourceSearchService 源文件检索服务
     * @param contributionSearchService Contribution 检索服务
     * @param vectorSearchService 向量检索服务
     * @param rrfFusionService RRF 融合服务
     * @param answerGenerationService 答案生成服务
     * @param queryCacheStore 查询缓存存储
     * @param reviewerAgent ReviewerAgent
     * @param pendingQueryManager PendingQuery 管理器
     */
    @Autowired
    public QueryFacadeService(
            FtsSearchService ftsSearchService,
            RefKeySearchService refKeySearchService,
            SourceSearchService sourceSearchService,
            ContributionSearchService contributionSearchService,
            VectorSearchService vectorSearchService,
            RrfFusionService rrfFusionService,
            AnswerGenerationService answerGenerationService,
            QueryCacheStore queryCacheStore,
            ReviewerAgent reviewerAgent,
            PendingQueryManager pendingQueryManager
    ) {
        this.ftsSearchService = ftsSearchService;
        this.refKeySearchService = refKeySearchService;
        this.sourceSearchService = sourceSearchService;
        this.contributionSearchService = contributionSearchService;
        this.vectorSearchService = vectorSearchService;
        this.rrfFusionService = rrfFusionService;
        this.answerGenerationService = answerGenerationService;
        this.queryCacheStore = queryCacheStore;
        this.reviewerAgent = reviewerAgent;
        this.pendingQueryManager = pendingQueryManager;
    }

    /**
     * 创建查询门面服务。
     *
     * @param ftsSearchService FTS 检索服务
     * @param refKeySearchService 引用词检索服务
     * @param rrfFusionService RRF 融合服务
     * @param answerGenerationService 答案生成服务
     * @param queryCacheStore 查询缓存存储
     * @param reviewerAgent ReviewerAgent
     * @param pendingQueryManager PendingQuery 管理器
     */
    public QueryFacadeService(
            FtsSearchService ftsSearchService,
            RefKeySearchService refKeySearchService,
            RrfFusionService rrfFusionService,
            AnswerGenerationService answerGenerationService,
            QueryCacheStore queryCacheStore,
            ReviewerAgent reviewerAgent,
            PendingQueryManager pendingQueryManager
    ) {
        this(
                ftsSearchService,
                refKeySearchService,
                new SourceSearchService(null),
                new ContributionSearchService(null),
                new VectorSearchService(),
                rrfFusionService,
                answerGenerationService,
                queryCacheStore,
                reviewerAgent,
                pendingQueryManager
        );
    }

    /**
     * 执行最小知识查询。
     *
     * @param question 查询问题
     * @return 查询响应
     */
    public QueryResponse query(String question) {
        String cacheKey = normalizeQuestion(question);
        Optional<QueryResponse> cachedResponse = queryCacheStore.get(cacheKey);
        if (cachedResponse.isPresent()) {
            return attachPendingQuery(question, cachedResponse.get());
        }

        List<QueryArticleHit> ftsHits = ftsSearchService.search(question, TOP_K);
        List<QueryArticleHit> refKeyHits = refKeySearchService.search(question, TOP_K);
        List<QueryArticleHit> sourceHits = sourceSearchService.search(question, TOP_K);
        List<QueryArticleHit> contributionHits = contributionSearchService.search(question, TOP_K);
        List<QueryArticleHit> vectorHits = vectorSearchService.search(question, TOP_K);
        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(
                List.of(ftsHits, refKeyHits, sourceHits, contributionHits, vectorHits),
                TOP_K
        );
        if (fusedHits.isEmpty()) {
            return new QueryResponse("未找到相关知识", List.of(), List.of());
        }

        String answer = answerGenerationService.generate(question, fusedHits);
        ReviewResult reviewResult = reviewerAgent.review(question, answer, collectSourcePaths(fusedHits));
        QueryResponse cachedPayload = new QueryResponse(
                answer,
                toSourceResponses(fusedHits),
                toArticleResponses(fusedHits),
                null,
                reviewResult.getStatus().name()
        );
        queryCacheStore.put(cacheKey, cachedPayload);
        return attachPendingQuery(question, cachedPayload);
    }

    /**
     * 为查询结果附加新的 pending query。
     *
     * @param question 问题
     * @param baseResponse 基础查询响应
     * @return 带 queryId 的查询响应
     */
    private QueryResponse attachPendingQuery(String question, QueryResponse baseResponse) {
        String queryId = pendingQueryManager.createPendingQuery(question, baseResponse).getQueryId();
        return new QueryResponse(
                baseResponse.getAnswer(),
                baseResponse.getSources(),
                baseResponse.getArticles(),
                queryId,
                baseResponse.getReviewStatus()
        );
    }

    /**
     * 规范化查询问题，避免无意义空白导致缓存穿透。
     *
     * @param question 查询问题
     * @return 规范化后的缓存键
     */
    private String normalizeQuestion(String question) {
        return question.trim();
    }

    /**
     * 转换来源响应。
     *
     * @param fusedHits 融合结果
     * @return 来源响应列表
     */
    private List<QuerySourceResponse> toSourceResponses(List<QueryArticleHit> fusedHits) {
        List<QuerySourceResponse> sourceResponses = new ArrayList<QuerySourceResponse>();
        Set<String> responseKeys = new LinkedHashSet<String>();
        appendSourceResponses(sourceResponses, responseKeys, fusedHits, QueryEvidenceType.ARTICLE);
        appendSourceResponses(sourceResponses, responseKeys, fusedHits, QueryEvidenceType.SOURCE);
        appendSourceResponses(sourceResponses, responseKeys, fusedHits, QueryEvidenceType.CONTRIBUTION);
        return sourceResponses;
    }

    /**
     * 按证据类型追加来源响应。
     *
     * @param sourceResponses 来源响应列表
     * @param responseKeys 已输出键集合
     * @param fusedHits 融合结果
     * @param queryEvidenceType 证据类型
     */
    private void appendSourceResponses(
            List<QuerySourceResponse> sourceResponses,
            Set<String> responseKeys,
            List<QueryArticleHit> fusedHits,
            QueryEvidenceType queryEvidenceType
    ) {
        for (QueryArticleHit fusedHit : fusedHits) {
            if (fusedHit.getEvidenceType() != queryEvidenceType) {
                continue;
            }
            String responseKey = fusedHit.getEvidenceType().name() + ":" + fusedHit.getConceptId();
            if (!responseKeys.add(responseKey)) {
                continue;
            }
            sourceResponses.add(new QuerySourceResponse(
                    fusedHit.getConceptId(),
                    fusedHit.getTitle(),
                    fusedHit.getSourcePaths()
            ));
        }
    }

    /**
     * 转换文章响应。
     *
     * @param fusedHits 融合结果
     * @return 文章响应列表
     */
    private List<QueryArticleResponse> toArticleResponses(List<QueryArticleHit> fusedHits) {
        List<QueryArticleResponse> articleResponses = new ArrayList<QueryArticleResponse>();
        for (QueryArticleHit fusedHit : fusedHits) {
            if (fusedHit.getEvidenceType() != QueryEvidenceType.ARTICLE) {
                continue;
            }
            articleResponses.add(new QueryArticleResponse(
                    fusedHit.getConceptId(),
                    fusedHit.getTitle()
            ));
        }
        return articleResponses;
    }

    /**
     * 收集全部来源路径，供审查与 pending 记录复用。
     *
     * @param fusedHits 融合结果
     * @return 去重后的来源路径
     */
    private List<String> collectSourcePaths(List<QueryArticleHit> fusedHits) {
        Set<String> sourcePaths = new LinkedHashSet<String>();
        for (QueryArticleHit fusedHit : fusedHits) {
            sourcePaths.addAll(fusedHit.getSourcePaths());
        }
        return new ArrayList<String>(sourcePaths);
    }
}
