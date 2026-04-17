package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.governance.repo.RepoSnapshotService;
import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.query.service.ArticleVectorIndexService;
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

    private final SourceIngestSupport sourceIngestSupport;

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
            SourceIngestSupport sourceIngestSupport
    ) {
        this.articleCompileSupport = articleCompileSupport;
        this.articleJdbcRepository = articleJdbcRepository;
        this.articleChunkJdbcRepository = articleChunkJdbcRepository;
        this.compilationWalStore = compilationWalStore;
        this.articleVectorIndexService = articleVectorIndexService;
        this.sourceIngestSupport = sourceIngestSupport;
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
     * 正式落库文章。
     *
     * @param jobId 作业标识
     * @param reviewedArticles 审查后文章集合
     * @return 已落库文章数
     */
    @Transactional(rollbackFor = Exception.class)
    public int persistArticles(String jobId, List<ArticleReviewEnvelope> reviewedArticles) {
        int persistedCount = 0;
        for (ArticleReviewEnvelope reviewEnvelope : reviewedArticles) {
            ArticleRecord articleRecord = finalizeArticleForPersist(reviewEnvelope);
            articleJdbcRepository.upsert(articleRecord);
            if (compilationWalStore != null) {
                compilationWalStore.markCommitted(jobId, articleRecord.getConceptId());
            }
            persistedCount++;
        }
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
            articleChunkJdbcRepository.replaceChunksFromContent(articleRecord.getConceptId(), articleRecord.getContent());
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
