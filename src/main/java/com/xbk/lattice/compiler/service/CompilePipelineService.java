package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.compiler.node.CompileArticleNode;
import com.xbk.lattice.compiler.prompt.SchemaAwarePrompts;
import com.xbk.lattice.governance.repo.RepoSnapshotService;
import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.query.service.ArticleChunkVectorIndexService;
import com.xbk.lattice.query.service.ArticleVectorIndexService;
import com.xbk.lattice.query.service.QueryCacheStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 编译节点能力服务
 *
 * 职责：为 Graph 编排提供过渡委托层，并保留 legacy compile/retry 兼容路径
 *
 * @author xiexu
 */
@Service
@Slf4j
@Profile("jdbc")
public class CompilePipelineService {

    private final SourceIngestSupport sourceIngestSupport;

    private final ArticleCompileSupport articleCompileSupport;

    private final ArticlePersistSupport articlePersistSupport;

    private final CompileArticleNode compileArticleNode;

    private final CompilationWalStore compilationWalStore;

    private final SynthesisArtifactsService synthesisArtifactsService;

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ArticleChunkJdbcRepository articleChunkJdbcRepository;

    private final ArticleVectorIndexService articleVectorIndexService;

    private final ArticleChunkVectorIndexService articleChunkVectorIndexService;

    private final IncrementalCompileService incrementalCompileService;

    private final LlmGateway llmGateway;

    private QueryCacheStore queryCacheStore;

    /**
     * 创建编译节点能力服务。
     *
     * @param compilerProperties 编译配置
     * @param llmGateway LLM 网关
     * @param articleReviewerGateway 文章审查网关
     * @param reviewFixService 审查修复服务
     * @param synthesisArtifactsService 合成产物服务
     * @param articleJdbcRepository 文章仓储
     * @param articleChunkJdbcRepository 文章 chunk 仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param sourceFileChunkJdbcRepository 源文件 chunk 仓储
     * @param compilationWalStore 编译 WAL 存储
     * @param articleVectorIndexService 文章向量索引服务
     * @param sourceIngestSupport 源数据支撑服务
     * @param articleCompileSupport 文章编译支撑服务
     * @param articlePersistSupport 文章落库支撑服务
     */
    @Autowired
    public CompilePipelineService(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SynthesisArtifactsService synthesisArtifactsService,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository,
            CompilationWalStore compilationWalStore,
            ArticleVectorIndexService articleVectorIndexService,
            ArticleChunkVectorIndexService articleChunkVectorIndexService,
            SourceIngestSupport sourceIngestSupport,
            ArticleCompileSupport articleCompileSupport,
            ArticlePersistSupport articlePersistSupport
    ) {
        this(
                compilerProperties,
                llmGateway,
                articleReviewerGateway,
                reviewFixService,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository,
                sourceFileChunkJdbcRepository,
                compilationWalStore,
                new SupportBundle(
                        sourceIngestSupport,
                        articleCompileSupport,
                        articlePersistSupport,
                        articleVectorIndexService,
                        articleChunkVectorIndexService
                )
        );
    }

    /**
     * 创建最小编译链路服务。
     *
     * @param compilerProperties 编译配置
     * @param llmGateway LLM 网关
     * @param articleReviewerGateway 文章审查网关
     * @param reviewFixService 审查修复服务
     * @param synthesisArtifactsService 合成产物服务
     * @param articleJdbcRepository 文章仓储
     * @param articleChunkJdbcRepository 文章 chunk 仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param sourceFileChunkJdbcRepository 源文件 chunk 仓储
     * @param compilationWalStore 编译 WAL 存储
     * @param articleVectorIndexService 文章向量索引服务
     */
    public CompilePipelineService(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SynthesisArtifactsService synthesisArtifactsService,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository,
            CompilationWalStore compilationWalStore,
            ArticleVectorIndexService articleVectorIndexService
    ) {
        this(
                compilerProperties,
                llmGateway,
                articleReviewerGateway,
                reviewFixService,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository,
                sourceFileChunkJdbcRepository,
                compilationWalStore,
                articleVectorIndexService,
                new ArticleChunkVectorIndexService()
        );
    }

    /**
     * 创建最小编译链路服务。
     *
     * @param compilerProperties 编译配置
     * @param llmGateway LLM 网关
     * @param articleReviewerGateway 文章审查网关
     * @param reviewFixService 审查修复服务
     * @param synthesisArtifactsService 合成产物服务
     * @param articleJdbcRepository 文章仓储
     * @param articleChunkJdbcRepository 文章 chunk 仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param sourceFileChunkJdbcRepository 源文件 chunk 仓储
     * @param compilationWalStore 编译 WAL 存储
     * @param articleVectorIndexService 文章向量索引服务
     */
    public CompilePipelineService(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SynthesisArtifactsService synthesisArtifactsService,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository,
            CompilationWalStore compilationWalStore,
            ArticleVectorIndexService articleVectorIndexService,
            ArticleChunkVectorIndexService articleChunkVectorIndexService
    ) {
        this(
                compilerProperties,
                llmGateway,
                articleReviewerGateway,
                reviewFixService,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository,
                sourceFileChunkJdbcRepository,
                compilationWalStore,
                createSupportBundle(
                        compilerProperties,
                        llmGateway,
                        articleReviewerGateway,
                        reviewFixService,
                        synthesisArtifactsService,
                        articleJdbcRepository,
                        articleChunkJdbcRepository,
                        sourceFileJdbcRepository,
                        sourceFileChunkJdbcRepository,
                        compilationWalStore,
                        articleVectorIndexService,
                        articleChunkVectorIndexService
                )
        );
    }

    /**
     * 创建最小编译链路服务。
     *
     * @param compilerProperties 编译配置
     * @param llmGateway LLM 网关
     * @param articleReviewerGateway 文章审查网关
     * @param reviewFixService 审查修复服务
     * @param synthesisArtifactsService 合成产物服务
     * @param articleJdbcRepository 文章仓储
     * @param articleChunkJdbcRepository 文章 chunk 仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param sourceFileChunkJdbcRepository 源文件 chunk 仓储
     * @param compilationWalStore 编译 WAL 存储
     */
    public CompilePipelineService(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SynthesisArtifactsService synthesisArtifactsService,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository,
            CompilationWalStore compilationWalStore
    ) {
        this(
                compilerProperties,
                llmGateway,
                articleReviewerGateway,
                reviewFixService,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository,
                sourceFileChunkJdbcRepository,
                compilationWalStore,
                createSupportBundle(
                        compilerProperties,
                        llmGateway,
                        articleReviewerGateway,
                        reviewFixService,
                        synthesisArtifactsService,
                        articleJdbcRepository,
                        articleChunkJdbcRepository,
                        sourceFileJdbcRepository,
                        sourceFileChunkJdbcRepository,
                        compilationWalStore,
                        new ArticleVectorIndexService(),
                        new ArticleChunkVectorIndexService()
                )
        );
    }

    /**
     * 创建最小编译链路服务。
     *
     * @param compilerProperties 编译配置
     * @param articleJdbcRepository 文章仓储
     * @param articleChunkJdbcRepository 文章 chunk 仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param compilationWalStore 编译 WAL 存储
     */
    CompilePipelineService(
            CompilerProperties compilerProperties,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            CompilationWalStore compilationWalStore
    ) {
        this(
                compilerProperties,
                null,
                null,
                null,
                null,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository,
                null,
                compilationWalStore,
                createSupportBundle(
                        compilerProperties,
                        null,
                        null,
                        null,
                        null,
                        articleJdbcRepository,
                        articleChunkJdbcRepository,
                        sourceFileJdbcRepository,
                        null,
                        compilationWalStore,
                        new ArticleVectorIndexService(),
                        new ArticleChunkVectorIndexService()
                )
        );
    }

    /**
     * 创建编译节点能力服务。
     *
     * @param compilerProperties 编译配置
     * @param llmGateway LLM 网关
     * @param articleReviewerGateway 文章审查网关
     * @param reviewFixService 审查修复服务
     * @param synthesisArtifactsService 合成产物服务
     * @param articleJdbcRepository 文章仓储
     * @param articleChunkJdbcRepository 文章 chunk 仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param sourceFileChunkJdbcRepository 源文件 chunk 仓储
     * @param compilationWalStore 编译 WAL 存储
     * @param supportBundle 支撑服务集合
     */
    private CompilePipelineService(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SynthesisArtifactsService synthesisArtifactsService,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository,
            CompilationWalStore compilationWalStore,
            SupportBundle supportBundle
    ) {
        this.llmGateway = llmGateway;
        this.sourceIngestSupport = supportBundle.sourceIngestSupport;
        this.articleCompileSupport = supportBundle.articleCompileSupport;
        this.articlePersistSupport = supportBundle.articlePersistSupport;
        this.compileArticleNode = new CompileArticleNode(
                llmGateway,
                sourceFileJdbcRepository,
                new DocumentSectionSelector(),
                articleReviewerGateway,
                reviewFixService,
                new SchemaAwarePrompts(compilerProperties)
        );
        this.compilationWalStore = compilationWalStore;
        this.synthesisArtifactsService = synthesisArtifactsService;
        this.articleJdbcRepository = articleJdbcRepository;
        this.articleChunkJdbcRepository = articleChunkJdbcRepository;
        this.articleVectorIndexService = supportBundle.articleVectorIndexService;
        this.articleChunkVectorIndexService = supportBundle.articleChunkVectorIndexService;
        this.incrementalCompileService = new IncrementalCompileService(
                compilerProperties,
                llmGateway,
                articleReviewerGateway,
                reviewFixService,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository,
                sourceFileChunkJdbcRepository,
                this.articleVectorIndexService,
                this.articleChunkVectorIndexService
        );
    }

    /**
     * 注入整库快照服务。
     *
     * @param repoSnapshotService 整库快照服务
     */
    @Autowired(required = false)
    void setRepoSnapshotService(RepoSnapshotService repoSnapshotService) {
        articlePersistSupport.setRepoSnapshotService(repoSnapshotService);
        incrementalCompileService.setRepoSnapshotService(repoSnapshotService);
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
     * 编译源目录并落盘文章。
     *
     * @param sourceDir 源目录
     * @return 编译结果
     * @throws IOException IO 异常
     */
    CompileResult compile(Path sourceDir) throws IOException {
        String jobId = UUID.randomUUID().toString();
        log.info("Compile started sourceDir: {}", sourceDir);
        List<RawSource> rawSources = sourceIngestSupport.ingest(sourceDir);
        sourceIngestSupport.persistSourceFiles(rawSources);
        sourceIngestSupport.persistSourceFileChunks(rawSources);
        Map<String, List<RawSource>> groupedSources = sourceIngestSupport.groupSources(rawSources);
        Map<String, List<SourceBatch>> sourceBatches = sourceIngestSupport.splitBatches(groupedSources);
        List<AnalyzedConcept> analyzedConcepts = sourceIngestSupport.analyzeBatches(sourceBatches, sourceDir);
        List<MergedConcept> mergedConcepts = sourceIngestSupport.mergeConcepts(analyzedConcepts);
        sourceIngestSupport.stageWal(jobId, mergedConcepts);
        int persistedCount = commitPendingConcepts(jobId, sourceDir);
        if (synthesisArtifactsService != null && !mergedConcepts.isEmpty()) {
            synthesisArtifactsService.generateAll(jobId, mergedConcepts);
        }
        articlePersistSupport.captureRepoSnapshot("compile.full", sourceDir, persistedCount);
        evictQueryCacheIfNeeded(persistedCount);
        CompileResult compileResult = new CompileResult(persistedCount, jobId);
        log.info(
                "Compile completed sourceDir: {}, jobId: {}, persistedCount: {}",
                sourceDir,
                jobId,
                compileResult.getPersistedCount()
        );
        return compileResult;
    }

    /**
     * 基于 jobId 重试未完成提交的概念。
     *
     * @param jobId 作业标识
     * @return 编译结果
     */
    CompileResult retry(String jobId) {
        int persistedCount = commitPendingConcepts(jobId, null);
        articlePersistSupport.captureRepoSnapshot("compile.retry", null, persistedCount);
        evictQueryCacheIfNeeded(persistedCount);
        return new CompileResult(persistedCount, jobId);
    }

    /**
     * 以增量模式编译源目录。
     *
     * @param sourceDir 源目录
     * @return 编译结果
     * @throws IOException IO 异常
     */
    CompileResult incrementalCompile(Path sourceDir) throws IOException {
        CompileResult compileResult = incrementalCompileService.incrementalCompile(sourceDir);
        evictQueryCacheIfNeeded(compileResult.getPersistedCount());
        return compileResult;
    }

    /**
     * 摄入源目录。
     *
     * @param sourceDir 源目录
     * @return 原始源文件集合
     * @throws IOException IO 异常
     */
    public List<RawSource> ingest(Path sourceDir) throws IOException {
        return sourceIngestSupport.ingest(sourceDir);
    }

    /**
     * 对源文件集合进行分组。
     *
     * @param rawSources 原始源文件集合
     * @return 分组结果
     */
    public Map<String, List<RawSource>> groupSources(List<RawSource> rawSources) {
        return sourceIngestSupport.groupSources(rawSources);
    }

    /**
     * 对分组结果执行分批。
     *
     * @param groupedSources 分组结果
     * @return 分批结果
     */
    public Map<String, List<SourceBatch>> splitBatches(Map<String, List<RawSource>> groupedSources) {
        return sourceIngestSupport.splitBatches(groupedSources);
    }

    /**
     * 分析全部批次。
     *
     * @param sourceBatches 分批结果
     * @param sourceDir 源目录
     * @return 分析结果
     */
    public List<AnalyzedConcept> analyzeBatches(
            Map<String, List<SourceBatch>> sourceBatches,
            Path sourceDir
    ) {
        return sourceIngestSupport.analyzeBatches(sourceBatches, sourceDir);
    }

    /**
     * 合并分析结果。
     *
     * @param analyzedConcepts 分析结果
     * @return 合并概念结果
     */
    public List<MergedConcept> mergeConcepts(List<AnalyzedConcept> analyzedConcepts) {
        return sourceIngestSupport.mergeConcepts(analyzedConcepts);
    }

    /**
     * 暂存待提交概念到 WAL。
     *
     * @param jobId 作业标识
     * @param mergedConcepts 合并概念
     */
    public void stageWal(String jobId, List<MergedConcept> mergedConcepts) {
        sourceIngestSupport.stageWal(jobId, mergedConcepts);
    }

    /**
     * 编译新文章草稿。
     *
     * @param mergedConcepts 合并概念
     * @param sourceDir 源目录
     * @return 草稿文章集合
     */
    public List<ArticleRecord> compileDraftArticles(List<MergedConcept> mergedConcepts, Path sourceDir) {
        return articleCompileSupport.compileDraftArticles(mergedConcepts, sourceDir);
    }

    /**
     * 执行增量规划。
     *
     * @param mergedConcepts 合并概念
     * @return 增量规划结果
     */
    public IncrementalCompilePlanResult planIncrementalGraphChanges(List<MergedConcept> mergedConcepts) {
        return sourceIngestSupport.planIncrementalGraphChanges(mergedConcepts);
    }

    /**
     * 生成增强文章草稿。
     *
     * @param enhancementConcepts 增强映射
     * @return 增强文章草稿
     */
    public List<ArticleRecord> enhanceExistingArticles(Map<String, List<MergedConcept>> enhancementConcepts) {
        return sourceIngestSupport.enhanceExistingArticles(enhancementConcepts);
    }

    /**
     * 审查草稿文章。
     *
     * @param draftArticles 草稿文章集合
     * @return 审查结果集合
     */
    public List<ArticleReviewEnvelope> reviewDraftArticles(List<ArticleRecord> draftArticles) {
        return articleCompileSupport.reviewDraftArticles(draftArticles);
    }

    /**
     * 对审查失败文章执行修复。
     *
     * @param reviewedArticles 审查后文章集合
     * @return 修复后的文章集合
     */
    public List<ArticleReviewEnvelope> fixReviewedArticles(List<ArticleReviewEnvelope> reviewedArticles) {
        return articleCompileSupport.fixReviewedArticles(reviewedArticles);
    }

    /**
     * 正式落库文章。
     *
     * @param jobId 作业标识
     * @param reviewedArticles 审查后文章集合
     * @return 已落库文章数
     */
    public int persistArticles(String jobId, List<ArticleReviewEnvelope> reviewedArticles) {
        return articlePersistSupport.persistArticles(jobId, reviewedArticles);
    }

    /**
     * 重建文章分块。
     *
     * @param reviewedArticles 已落库文章集合
     */
    public void rebuildArticleChunks(List<ArticleReviewEnvelope> reviewedArticles) {
        articlePersistSupport.rebuildArticleChunks(reviewedArticles);
    }

    /**
     * 刷新文章向量索引。
     *
     * @param reviewedArticles 已落库文章集合
     */
    public void refreshVectorIndex(List<ArticleReviewEnvelope> reviewedArticles) {
        articlePersistSupport.refreshVectorIndex(reviewedArticles);
    }

    /**
     * 刷新合成产物。
     */
    public void generateGraphSynthesisArtifacts() {
        articlePersistSupport.generateGraphSynthesisArtifacts();
    }

    /**
     * 摄取整库快照。
     *
     * @param triggerEvent 触发事件
     * @param sourceDir 源目录
     * @param persistedCount 已落库数量
     */
    public void captureRepoSnapshot(String triggerEvent, Path sourceDir, int persistedCount) {
        articlePersistSupport.captureRepoSnapshot(triggerEvent, sourceDir, persistedCount);
    }

    /**
     * 落盘源文件预览。
     *
     * @param rawSources 原始源文件集合
     */
    public void persistSourceFiles(List<RawSource> rawSources) {
        sourceIngestSupport.persistSourceFiles(rawSources);
    }

    /**
     * 落盘源文件分块。
     *
     * @param rawSources 原始源文件集合
     */
    public void persistSourceFileChunks(List<RawSource> rawSources) {
        sourceIngestSupport.persistSourceFileChunks(rawSources);
    }

    /**
     * 汇总文章最终落库形态。
     *
     * @param reviewEnvelope 审查包裹对象
     * @return 最终文章记录
     */
    public ArticleRecord finalizeArticleForPersist(ArticleReviewEnvelope reviewEnvelope) {
        return articlePersistSupport.finalizeArticleForPersist(reviewEnvelope);
    }

    /**
     * 提交 WAL 中尚未完成的概念。
     *
     * @param jobId 作业标识
     * @param sourceDir 源目录
     * @return 成功提交数量
     */
    private int commitPendingConcepts(String jobId, Path sourceDir) {
        int persistedCount = 0;
        List<MergedConcept> pendingConcepts = compilationWalStore.loadPendingConcepts(jobId);
        for (MergedConcept mergedConcept : pendingConcepts) {
            ArticleRecord articleRecord = compileArticleNode.compile(mergedConcept, sourceDir);
            articleJdbcRepository.upsert(articleRecord);
            articleChunkJdbcRepository.replaceChunksFromContent(
                    articleRecord.getArticleKey(),
                    articleRecord.getConceptId(),
                    articleRecord.getContent()
            );
            articleVectorIndexService.indexArticle(articleRecord);
            if (articleChunkVectorIndexService != null) {
                articleChunkVectorIndexService.indexArticle(articleRecord);
            }
            compilationWalStore.markCommitted(jobId, mergedConcept.getConceptId());
            persistedCount++;
        }
        return persistedCount;
    }

    /**
     * 在编译写入成功后清理查询缓存。
     *
     * @param persistedCount 已落库文章数
     */
    private void evictQueryCacheIfNeeded(int persistedCount) {
        if (persistedCount <= 0) {
            return;
        }
        if (queryCacheStore != null) {
            queryCacheStore.evictAll();
        }
        if (llmGateway != null) {
            llmGateway.evictPromptCache();
        }
    }

    /**
     * 返回编译角色当前路由。
     *
     * @return 编译角色路由
     */
    public String currentCompileRoute() {
        return articleCompileSupport.currentCompileRoute();
    }

    /**
     * 返回审查角色当前路由。
     *
     * @return 审查角色路由
     */
    public String currentReviewRoute() {
        return articleCompileSupport.currentReviewRoute();
    }

    /**
     * 返回修复角色当前路由。
     *
     * @return 修复角色路由
     */
    public String currentFixRoute() {
        return articleCompileSupport.currentFixRoute();
    }

    /**
     * 创建支撑服务集合。
     *
     * @param compilerProperties 编译配置
     * @param llmGateway LLM 网关
     * @param articleReviewerGateway 文章审查网关
     * @param reviewFixService 审查修复服务
     * @param synthesisArtifactsService 合成产物服务
     * @param articleJdbcRepository 文章仓储
     * @param articleChunkJdbcRepository 文章 chunk 仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param sourceFileChunkJdbcRepository 源文件 chunk 仓储
     * @param compilationWalStore 编译 WAL 存储
     * @param articleVectorIndexService 文章向量索引服务
     * @param articleChunkVectorIndexService 文章分块向量索引服务
     * @return 支撑服务集合
     */
    private static SupportBundle createSupportBundle(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SynthesisArtifactsService synthesisArtifactsService,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository,
            CompilationWalStore compilationWalStore,
            ArticleVectorIndexService articleVectorIndexService,
            ArticleChunkVectorIndexService articleChunkVectorIndexService
    ) {
        ArticleVectorIndexService resolvedArticleVectorIndexService = articleVectorIndexService == null
                ? new ArticleVectorIndexService()
                : articleVectorIndexService;
        ArticleChunkVectorIndexService resolvedArticleChunkVectorIndexService = articleChunkVectorIndexService == null
                ? new ArticleChunkVectorIndexService()
                : articleChunkVectorIndexService;
        SourceIngestSupport sourceIngestSupport = new SourceIngestSupport(
                compilerProperties,
                llmGateway,
                articleReviewerGateway,
                reviewFixService,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository,
                sourceFileChunkJdbcRepository,
                compilationWalStore,
                resolvedArticleVectorIndexService
        );
        ArticleCompileSupport articleCompileSupport = new ArticleCompileSupport(
                compilerProperties,
                llmGateway,
                articleReviewerGateway,
                reviewFixService,
                sourceFileJdbcRepository
        );
        ArticlePersistSupport articlePersistSupport = new ArticlePersistSupport(
                articleCompileSupport,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                compilationWalStore,
                resolvedArticleVectorIndexService,
                resolvedArticleChunkVectorIndexService,
                sourceIngestSupport
        );
        return new SupportBundle(
                sourceIngestSupport,
                articleCompileSupport,
                articlePersistSupport,
                resolvedArticleVectorIndexService,
                resolvedArticleChunkVectorIndexService
        );
    }

    /**
     * 编译支撑服务集合。
     *
     * @author xiexu
     */
    private static final class SupportBundle {

        private final SourceIngestSupport sourceIngestSupport;

        private final ArticleCompileSupport articleCompileSupport;

        private final ArticlePersistSupport articlePersistSupport;

        private final ArticleVectorIndexService articleVectorIndexService;

        private final ArticleChunkVectorIndexService articleChunkVectorIndexService;

        /**
         * 创建编译支撑服务集合。
         *
         * @param sourceIngestSupport 源数据支撑服务
         * @param articleCompileSupport 文章编译支撑服务
         * @param articlePersistSupport 文章落库支撑服务
         * @param articleVectorIndexService 文章向量索引服务
         */
        private SupportBundle(
                SourceIngestSupport sourceIngestSupport,
                ArticleCompileSupport articleCompileSupport,
                ArticlePersistSupport articlePersistSupport,
                ArticleVectorIndexService articleVectorIndexService,
                ArticleChunkVectorIndexService articleChunkVectorIndexService
        ) {
            this.sourceIngestSupport = sourceIngestSupport;
            this.articleCompileSupport = articleCompileSupport;
            this.articlePersistSupport = articlePersistSupport;
            this.articleVectorIndexService = articleVectorIndexService;
            this.articleChunkVectorIndexService = articleChunkVectorIndexService;
        }
    }
}
