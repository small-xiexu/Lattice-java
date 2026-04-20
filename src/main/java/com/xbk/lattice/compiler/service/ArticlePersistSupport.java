package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.governance.repo.RepoSnapshotService;
import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleSourceRefJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleSourceRefRecord;
import com.xbk.lattice.query.service.ArticleChunkVectorIndexService;
import com.xbk.lattice.query.service.ArticleVectorIndexService;
import com.xbk.lattice.query.service.QueryCacheStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;

/**
 * 编译文章落库支撑服务
 *
 * 职责：承载文章落库、切块、索引、合成与快照等确定性副作用能力
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class ArticlePersistSupport {

    private final ArticleCompileSupport articleCompileSupport;

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ArticleChunkJdbcRepository articleChunkJdbcRepository;

    private final CompilationWalStore compilationWalStore;

    private final ArticleVectorIndexService articleVectorIndexService;

    private final ArticleChunkVectorIndexService articleChunkVectorIndexService;

    private final SourceIngestSupport sourceIngestSupport;

    private final ArticleSourceRefJdbcRepository articleSourceRefJdbcRepository;

    private QueryCacheStore queryCacheStore;

    private RepoSnapshotService repoSnapshotService;

    /**
     * 创建编译文章落库支撑服务。
     *
     * @param articleCompileSupport 编译文章认知支撑服务
     * @param articleJdbcRepository 文章仓储
     * @param articleChunkJdbcRepository 文章 chunk 仓储
     * @param compilationWalStore 编译 WAL 存储
     * @param articleVectorIndexService 文章向量索引服务
     * @param sourceIngestSupport 编译源数据支撑服务
     */
    public ArticlePersistSupport(
            ArticleCompileSupport articleCompileSupport,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            CompilationWalStore compilationWalStore,
            ArticleVectorIndexService articleVectorIndexService,
            ArticleChunkVectorIndexService articleChunkVectorIndexService,
            SourceIngestSupport sourceIngestSupport
    ) {
        this(
                articleCompileSupport,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                compilationWalStore,
                articleVectorIndexService,
                articleChunkVectorIndexService,
                sourceIngestSupport,
                null
        );
    }

    /**
     * 创建编译文章落库支撑服务。
     *
     * @param articleCompileSupport 编译文章认知支撑服务
     * @param articleJdbcRepository 文章仓储
     * @param articleChunkJdbcRepository 文章 chunk 仓储
     * @param compilationWalStore 编译 WAL 存储
     * @param articleVectorIndexService 文章向量索引服务
     * @param articleChunkVectorIndexService 文章分块向量索引服务
     * @param sourceIngestSupport 编译源数据支撑服务
     * @param articleSourceRefJdbcRepository 来源关联仓储
     */
    @Autowired
    public ArticlePersistSupport(
            ArticleCompileSupport articleCompileSupport,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            CompilationWalStore compilationWalStore,
            ArticleVectorIndexService articleVectorIndexService,
            ArticleChunkVectorIndexService articleChunkVectorIndexService,
            SourceIngestSupport sourceIngestSupport,
            ArticleSourceRefJdbcRepository articleSourceRefJdbcRepository
    ) {
        this.articleCompileSupport = articleCompileSupport;
        this.articleJdbcRepository = articleJdbcRepository;
        this.articleChunkJdbcRepository = articleChunkJdbcRepository;
        this.compilationWalStore = compilationWalStore;
        this.articleVectorIndexService = articleVectorIndexService;
        this.articleChunkVectorIndexService = articleChunkVectorIndexService;
        this.sourceIngestSupport = sourceIngestSupport;
        this.articleSourceRefJdbcRepository = articleSourceRefJdbcRepository;
    }

    /**
     * 注入整库快照服务。
     *
     * @param repoSnapshotService 整库快照服务
     */
    @Autowired(required = false)
    void setRepoSnapshotService(RepoSnapshotService repoSnapshotService) {
        this.repoSnapshotService = repoSnapshotService;
    }

    /**
     * 注入查询缓存存储。
     *
     * @param queryCacheStore 查询缓存存储
     */
    @Autowired(required = false)
    void setQueryCacheStore(QueryCacheStore queryCacheStore) {
        this.queryCacheStore = queryCacheStore;
    }

    /**
     * 正式落库文章。
     *
     * @param jobId 作业标识
     * @param reviewedArticles 审查后文章集合
     * @return 已落库文章数
     */
    @Transactional(rollbackFor = Exception.class)
    public int persistArticles(String jobId, List<ArticleReviewEnvelope> reviewedArticles) {
        return persistArticles(jobId, reviewedArticles, null, null, java.util.Collections.<String, Long>emptyMap());
    }

    /**
     * 正式落库文章。
     *
     * @param jobId 作业标识
     * @param reviewedArticles 审查后文章集合
     * @param sourceId 资料源主键
     * @param sourceCode 资料源编码
     * @param sourceFileIdsByPath 源文件主键映射
     * @return 已落库文章数
     */
    @Transactional(rollbackFor = Exception.class)
    public int persistArticles(
            String jobId,
            List<ArticleReviewEnvelope> reviewedArticles,
            Long sourceId,
            String sourceCode,
            java.util.Map<String, Long> sourceFileIdsByPath
    ) {
        int persistedCount = 0;
        for (ArticleReviewEnvelope reviewEnvelope : reviewedArticles) {
            ArticleRecord articleRecord = finalizeArticleForPersist(reviewEnvelope);
            articleRecord = ensureSourceAwareIdentifiers(articleRecord, sourceId, sourceCode);
            articleJdbcRepository.upsert(articleRecord);
            replaceArticleSourceRefs(articleRecord, sourceId, sourceFileIdsByPath);
            if (compilationWalStore != null) {
                compilationWalStore.markCommitted(jobId, articleRecord.getConceptId());
            }
            persistedCount++;
        }
        evictQueryCacheIfNeeded(persistedCount);
        return persistedCount;
    }

    /**
     * 重建文章分块。
     *
     * @param reviewedArticles 已落库文章集合
     */
    @Transactional(rollbackFor = Exception.class)
    public void rebuildArticleChunks(List<ArticleReviewEnvelope> reviewedArticles) {
        for (ArticleReviewEnvelope reviewEnvelope : reviewedArticles) {
            ArticleRecord articleRecord = finalizeArticleForPersist(reviewEnvelope);
            articleChunkJdbcRepository.replaceChunksFromContent(
                    articleRecord.getArticleKey(),
                    articleRecord.getConceptId(),
                    articleRecord.getContent()
            );
        }
    }

    /**
     * 刷新文章向量索引。
     *
     * @param reviewedArticles 已落库文章集合
     */
    @Transactional(rollbackFor = Exception.class)
    public void refreshVectorIndex(List<ArticleReviewEnvelope> reviewedArticles) {
        for (ArticleReviewEnvelope reviewEnvelope : reviewedArticles) {
            ArticleRecord articleRecord = finalizeArticleForPersist(reviewEnvelope);
            articleVectorIndexService.indexArticle(articleRecord);
            if (articleChunkVectorIndexService != null) {
                articleChunkVectorIndexService.indexArticle(articleRecord);
            }
        }
    }

    /**
     * 汇总文章最终落库形态。
     *
     * @param reviewEnvelope 审查包裹对象
     * @return 最终文章记录
     */
    public ArticleRecord finalizeArticleForPersist(ArticleReviewEnvelope reviewEnvelope) {
        return articleCompileSupport.finalizeArticleForPersist(reviewEnvelope);
    }

    /**
     * 在文章落库成功后清理查询缓存。
     *
     * @param persistedCount 已落库文章数
     */
    private void evictQueryCacheIfNeeded(int persistedCount) {
        if (persistedCount <= 0 || queryCacheStore == null) {
            return;
        }
        queryCacheStore.evictAll();
    }

    private ArticleRecord ensureSourceAwareIdentifiers(ArticleRecord articleRecord, Long sourceId, String sourceCode) {
        Long effectiveSourceId = articleRecord.getSourceId() == null ? sourceId : articleRecord.getSourceId();
        String effectiveArticleKey = articleRecord.getArticleKey();
        if (effectiveArticleKey == null || effectiveArticleKey.isBlank()) {
            if (sourceCode == null || sourceCode.isBlank()) {
                effectiveArticleKey = articleRecord.getConceptId();
            }
            else {
                effectiveArticleKey = sourceCode + "--" + articleRecord.getConceptId();
            }
        }
        return new ArticleRecord(
                effectiveSourceId,
                effectiveArticleKey,
                articleRecord.getConceptId(),
                articleRecord.getTitle(),
                articleRecord.getContent(),
                articleRecord.getLifecycle(),
                articleRecord.getCompiledAt(),
                articleRecord.getSourcePaths(),
                articleRecord.getMetadataJson(),
                articleRecord.getSummary(),
                articleRecord.getReferentialKeywords(),
                articleRecord.getDependsOn(),
                articleRecord.getRelated(),
                articleRecord.getConfidence(),
                articleRecord.getReviewStatus()
        );
    }

    private void replaceArticleSourceRefs(
            ArticleRecord articleRecord,
            Long sourceId,
            java.util.Map<String, Long> sourceFileIdsByPath
    ) {
        if (articleSourceRefJdbcRepository == null
                || articleRecord.getArticleKey() == null
                || articleRecord.getArticleKey().isBlank()
                || sourceId == null) {
            return;
        }
        java.util.Map<String, ArticleSourceRefRecord> existingRefsByLabel = new java.util.LinkedHashMap<String, ArticleSourceRefRecord>();
        for (ArticleSourceRefRecord existingRef : articleSourceRefJdbcRepository.findByArticleKey(articleRecord.getArticleKey())) {
            if (existingRef.getRefLabel() == null || existingRef.getRefLabel().isBlank()) {
                continue;
            }
            existingRefsByLabel.put(existingRef.getRefLabel(), existingRef);
        }

        java.util.List<ArticleSourceRefRecord> refRecords = new java.util.ArrayList<ArticleSourceRefRecord>();
        for (String sourcePath : articleRecord.getSourcePaths()) {
            Long sourceFileId = sourceFileIdsByPath.get(sourcePath);
            if (sourceFileId == null) {
                ArticleSourceRefRecord existingRef = existingRefsByLabel.get(sourcePath);
                if (existingRef != null) {
                    sourceFileId = existingRef.getSourceFileId();
                }
            }
            if (sourceFileId == null) {
                throw new IllegalStateException("source file id missing for article path: " + sourcePath);
            }
            refRecords.add(new ArticleSourceRefRecord(
                    articleRecord.getArticleKey(),
                    sourceId,
                    sourceFileId,
                    "PRIMARY",
                    sourcePath
            ));
        }
        articleSourceRefJdbcRepository.replaceRefs(articleRecord.getArticleKey(), refRecords);
    }

    /**
     * 刷新合成产物。
     */
    @Transactional(rollbackFor = Exception.class)
    public void generateGraphSynthesisArtifacts() {
        sourceIngestSupport.refreshGraphSynthesisArtifacts();
    }

    /**
     * 捕获整库快照。
     *
     * @param triggerEvent 触发事件
     * @param sourceDir 源目录
     * @param persistedCount 已落库数量
     */
    public void captureRepoSnapshot(String triggerEvent, Path sourceDir, int persistedCount) {
        if (repoSnapshotService == null || persistedCount <= 0) {
            return;
        }
        String description = sourceDir == null
                ? "CLI/HTTP write operation"
                : "sourceDir=" + sourceDir;
        repoSnapshotService.snapshot(triggerEvent, description, null);
    }
}
