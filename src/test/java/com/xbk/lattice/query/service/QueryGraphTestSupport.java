package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.query.citation.CitationCheckService;
import com.xbk.lattice.query.citation.CitationExtractor;
import com.xbk.lattice.query.citation.CitationValidator;
import com.xbk.lattice.query.graph.InMemoryQueryWorkingSetStore;
import com.xbk.lattice.query.graph.QueryGraphConditions;
import com.xbk.lattice.query.graph.QueryGraphDefinitionFactory;
import com.xbk.lattice.query.graph.QueryGraphLifecycleListener;
import com.xbk.lattice.query.graph.QueryGraphStateMapper;
import com.xbk.lattice.query.graph.QueryWorkingSetStore;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Query Graph 测试支撑
 *
 * 职责：为重构后的 Query Graph / QueryFacade 单测提供统一的新链路装配入口
 *
 * @author xiexu
 */
final class QueryGraphTestSupport {

    private QueryGraphTestSupport() {
    }

    /**
     * 创建走新 Query Graph 链路的编排器。
     *
     * @param ftsSearchService FTS 检索服务
     * @param refKeySearchService RefKey 检索服务
     * @param sourceSearchService Source 检索服务
     * @param contributionSearchService Contribution 检索服务
     * @param vectorSearchService Vector 检索服务
     * @param answerGenerationService 答案生成服务
     * @param queryCacheStore 查询缓存
     * @param reviewerAgent 审查代理
     * @param queryReviewProperties 审查配置
     * @param evidenceCatalog 引用核验用证据目录
     * @return Query Graph 编排器
     */
    static QueryGraphOrchestrator createQueryGraphOrchestrator(
            FtsSearchService ftsSearchService,
            RefKeySearchService refKeySearchService,
            SourceSearchService sourceSearchService,
            ContributionSearchService contributionSearchService,
            VectorSearchService vectorSearchService,
            AnswerGenerationService answerGenerationService,
            QueryCacheStore queryCacheStore,
            ReviewerAgent reviewerAgent,
            QueryReviewProperties queryReviewProperties,
            List<QueryArticleHit> evidenceCatalog
    ) {
        QueryWorkingSetStore queryWorkingSetStore = new InMemoryQueryWorkingSetStore();
        QueryGraphStateMapper queryGraphStateMapper = new QueryGraphStateMapper();
        CitationCheckService citationCheckService = new CitationCheckService(
                new CitationExtractor(),
                new CitationValidator(
                        new CatalogArticleJdbcRepository(evidenceCatalog),
                        new CatalogSourceFileJdbcRepository(evidenceCatalog)
                )
        );
        QueryGraphDefinitionFactory queryGraphDefinitionFactory = new QueryGraphDefinitionFactory(
                ftsSearchService,
                refKeySearchService,
                sourceSearchService,
                contributionSearchService,
                new GraphSearchService(),
                vectorSearchService,
                new ChunkVectorSearchService(),
                new RrfFusionService(),
                new QueryRetrievalSettingsService(),
                answerGenerationService,
                queryCacheStore,
                reviewerAgent,
                queryWorkingSetStore,
                citationCheckService,
                null,
                queryGraphStateMapper,
                new QueryGraphConditions(queryReviewProperties)
        );
        return new QueryGraphOrchestrator(
                queryGraphDefinitionFactory,
                queryGraphStateMapper,
                new QueryGraphLifecycleListener(queryGraphStateMapper),
                queryWorkingSetStore,
                queryReviewProperties,
                null
        );
    }

    /**
     * 创建走新 Query Graph 链路的 QueryFacade。
     *
     * @param queryGraphOrchestrator Query Graph 编排器
     * @param pendingQueryManager PendingQuery 管理器
     * @return QueryFacade
     */
    static QueryFacadeService createQueryFacadeService(
            QueryGraphOrchestrator queryGraphOrchestrator,
            PendingQueryManager pendingQueryManager
    ) {
        return new QueryFacadeService(queryGraphOrchestrator, null, null, pendingQueryManager, null);
    }

    /**
     * 目录化文章仓储替身。
     *
     * @author xiexu
     */
    private static class CatalogArticleJdbcRepository extends ArticleJdbcRepository {

        private final Map<String, ArticleRecord> articleRecordsByKey;

        private CatalogArticleJdbcRepository(List<QueryArticleHit> evidenceCatalog) {
            super(null);
            this.articleRecordsByKey = new LinkedHashMap<String, ArticleRecord>();
            if (evidenceCatalog == null) {
                return;
            }
            long sourceId = 1L;
            for (QueryArticleHit queryArticleHit : evidenceCatalog) {
                String articleKey = resolveArticleKey(queryArticleHit);
                ArticleRecord articleRecord = new ArticleRecord(
                        sourceId,
                        articleKey,
                        queryArticleHit.getConceptId(),
                        queryArticleHit.getTitle(),
                        queryArticleHit.getContent(),
                        "published",
                        OffsetDateTime.now(),
                        queryArticleHit.getSourcePaths(),
                        queryArticleHit.getMetadataJson(),
                        "",
                        List.of(),
                        List.of(),
                        List.of(),
                        "high",
                        "approved"
                );
                articleRecordsByKey.put(articleKey, articleRecord);
                if (queryArticleHit.getConceptId() != null && !queryArticleHit.getConceptId().isBlank()) {
                    articleRecordsByKey.put(queryArticleHit.getConceptId(), articleRecord);
                }
                sourceId++;
            }
        }

        @Override
        public Optional<ArticleRecord> findByArticleKey(String articleKey) {
            return Optional.ofNullable(articleRecordsByKey.get(articleKey));
        }

        @Override
        public Optional<ArticleRecord> findByConceptId(String conceptId) {
            return Optional.ofNullable(articleRecordsByKey.get(conceptId));
        }
    }

    /**
     * 目录化源码仓储替身。
     *
     * @author xiexu
     */
    private static class CatalogSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final Map<String, SourceFileRecord> sourceFileRecordsByPath;

        private CatalogSourceFileJdbcRepository(List<QueryArticleHit> evidenceCatalog) {
            super(null);
            this.sourceFileRecordsByPath = new LinkedHashMap<String, SourceFileRecord>();
            if (evidenceCatalog == null) {
                return;
            }
            long sourceFileId = 100L;
            for (QueryArticleHit queryArticleHit : evidenceCatalog) {
                if (queryArticleHit.getSourcePaths() == null || queryArticleHit.getSourcePaths().isEmpty()) {
                    continue;
                }
                for (String sourcePath : queryArticleHit.getSourcePaths()) {
                    if (sourcePath == null || sourcePath.isBlank()) {
                        continue;
                    }
                    sourceFileRecordsByPath.put(
                            sourcePath,
                            new SourceFileRecord(
                                    sourceFileId,
                                    queryArticleHit.getSourceId(),
                                    sourcePath,
                                    sourcePath,
                                    null,
                                    queryArticleHit.getContent(),
                                    "JAVA",
                                    128L,
                                    queryArticleHit.getContent(),
                                    "{}",
                                    false,
                                    sourcePath
                            )
                    );
                    sourceFileId++;
                }
            }
        }

        @Override
        public Optional<SourceFileRecord> findByPath(String filePath) {
            return Optional.ofNullable(sourceFileRecordsByPath.get(filePath));
        }
    }

    private static String resolveArticleKey(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return "";
        }
        if (queryArticleHit.getArticleKey() != null && !queryArticleHit.getArticleKey().isBlank()) {
            return queryArticleHit.getArticleKey();
        }
        if (queryArticleHit.getConceptId() != null) {
            return queryArticleHit.getConceptId();
        }
        return "";
    }
}
