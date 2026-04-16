package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.model.AnalyzedConcept;
import com.xbk.lattice.compiler.model.MergedConcept;
import com.xbk.lattice.compiler.model.RawSource;
import com.xbk.lattice.compiler.model.SourceBatch;
import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.query.service.ArticleVectorIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 最小编译链路服务
 *
 * 职责：把源目录内容编译为文章并落入 articles 表
 *
 * @author xiexu
 */
@Service
@Slf4j
@Profile("jdbc")
public class CompilePipelineService {

    private final IngestNode ingestNode;

    private final GroupNode groupNode;

    private final BatchSplitNode batchSplitNode;

    private final AnalyzeNode analyzeNode;

    private final CrossGroupMergeNode crossGroupMergeNode;

    private final CompileArticleNode compileArticleNode;

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ArticleChunkJdbcRepository articleChunkJdbcRepository;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    private final SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository;

    private final CompilationWalStore compilationWalStore;

    private final SynthesisArtifactsService synthesisArtifactsService;

    private final IncrementalCompileService incrementalCompileService;

    private final ArticleVectorIndexService articleVectorIndexService;

    /**
     * 创建最小编译链路服务。
     *
     * @param compilerProperties 编译配置
     * @param articleJdbcRepository 文章仓储
     * @param articleChunkJdbcRepository 文章 chunk 仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param compilationWalStore 编译 WAL 存储
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
            ArticleVectorIndexService articleVectorIndexService
    ) {
        this.ingestNode = new IngestNode(compilerProperties);
        this.groupNode = new GroupNode(compilerProperties);
        this.batchSplitNode = new BatchSplitNode(
                compilerProperties,
                new FileRankingService(compilerProperties)
        );
        this.analyzeNode = new AnalyzeNode();
        this.crossGroupMergeNode = new CrossGroupMergeNode();
        this.compileArticleNode = new CompileArticleNode(
                llmGateway,
                sourceFileJdbcRepository,
                new DocumentSectionSelector(),
                articleReviewerGateway,
                reviewFixService,
                new SchemaAwarePrompts(compilerProperties)
        );
        this.articleJdbcRepository = articleJdbcRepository;
        this.articleChunkJdbcRepository = articleChunkJdbcRepository;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
        this.sourceFileChunkJdbcRepository = sourceFileChunkJdbcRepository;
        this.compilationWalStore = compilationWalStore;
        this.synthesisArtifactsService = synthesisArtifactsService;
        this.articleVectorIndexService = articleVectorIndexService;
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
            articleVectorIndexService
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
                new ArticleVectorIndexService()
        );
    }

    /**
     * 创建最小编译链路服务（兼容测试构造器）。
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
                new ArticleVectorIndexService()
        );
    }

    /**
     * 编译源目录并落盘文章。
     *
     * @param sourceDir 源目录
     * @return 编译结果
     * @throws IOException IO 异常
     */
    public CompileResult compile(Path sourceDir) throws IOException {
        String jobId = UUID.randomUUID().toString();
        log.info("Compile started sourceDir: {}", sourceDir);
        List<RawSource> rawSources = ingestNode.ingest(sourceDir);
        persistSourceFiles(rawSources);
        persistSourceFileChunks(rawSources);
        Map<String, List<RawSource>> groupedSources = groupNode.group(rawSources);

        List<AnalyzedConcept> analyzedConcepts = new ArrayList<AnalyzedConcept>();
        for (Map.Entry<String, List<RawSource>> entry : groupedSources.entrySet()) {
            List<SourceBatch> sourceBatches = batchSplitNode.split(entry.getKey(), entry.getValue());
            analyzedConcepts.addAll(analyzeNode.analyze(entry.getKey(), sourceBatches, sourceDir));
        }

        List<MergedConcept> mergedConcepts = crossGroupMergeNode.merge(analyzedConcepts);
        compilationWalStore.stage(jobId, mergedConcepts);
        int persistedCount = commitPendingConcepts(jobId, sourceDir);
        if (synthesisArtifactsService != null && !mergedConcepts.isEmpty()) {
            synthesisArtifactsService.generateAll(mergedConcepts);
        }
        CompileResult compileResult = new CompileResult(persistedCount, jobId);
        log.info("Compile completed sourceDir: {}, jobId: {}, persistedCount: {}", sourceDir, jobId, compileResult.getPersistedCount());
        return compileResult;
    }

    /**
     * 基于 jobId 重试未完成提交的概念。
     *
     * @param jobId 作业标识
     * @return 编译结果
     */
    public CompileResult retry(String jobId) {
        int persistedCount = commitPendingConcepts(jobId, null);
        return new CompileResult(persistedCount, jobId);
    }

    /**
     * 以增量模式编译源目录。
     *
     * @param sourceDir 源目录
     * @return 编译结果
     * @throws IOException IO 异常
     */
    public CompileResult incrementalCompile(Path sourceDir) throws IOException {
        return incrementalCompileService.incrementalCompile(sourceDir);
    }

    /**
     * 落盘源文件预览。
     *
     * @param rawSources 原始源文件集合
     */
    private void persistSourceFiles(List<RawSource> rawSources) {
        for (RawSource rawSource : rawSources) {
            sourceFileJdbcRepository.upsert(new SourceFileRecord(
                    rawSource.getRelativePath(),
                    buildContentPreview(rawSource.getContent()),
                    rawSource.getFormat(),
                    rawSource.getFileSize(),
                    rawSource.getContent(),
                    rawSource.getMetadataJson(),
                    rawSource.isVerbatim(),
                    rawSource.getRawPath()
            ));
        }
    }

    /**
     * 落盘源文件分块，供查询层做 source evidence 检索。
     *
     * @param rawSources 原始源文件集合
     */
    private void persistSourceFileChunks(List<RawSource> rawSources) {
        if (sourceFileChunkJdbcRepository == null) {
            return;
        }

        for (RawSource rawSource : rawSources) {
            sourceFileChunkJdbcRepository.replaceChunksFromContent(
                    rawSource.getRelativePath(),
                    rawSource.getContent(),
                    rawSource.isVerbatim()
            );
        }
    }

    /**
     * 构建源文件预览内容。
     *
     * @param content 原始内容
     * @return 预览内容
     */
    private String buildContentPreview(String content) {
        int maxPreviewChars = 500;
        if (content.length() <= maxPreviewChars) {
            return content;
        }
        return content.substring(0, maxPreviewChars);
    }

    /**
     * 提交 WAL 中尚未完成的概念。
     *
     * @param jobId 作业标识
     * @return 成功提交数量
     */
    private int commitPendingConcepts(String jobId, Path sourceDir) {
        int persistedCount = 0;
        List<MergedConcept> pendingConcepts = compilationWalStore.loadPendingConcepts(jobId);
        for (MergedConcept mergedConcept : pendingConcepts) {
            ArticleRecord articleRecord = compileArticleNode.compile(mergedConcept, sourceDir);
            articleJdbcRepository.upsert(articleRecord);
            articleChunkJdbcRepository.replaceChunksFromContent(articleRecord.getConceptId(), articleRecord.getContent());
            articleVectorIndexService.indexArticle(articleRecord);
            compilationWalStore.markCommitted(jobId, mergedConcept.getConceptId());
            persistedCount++;
        }
        return persistedCount;
    }
}
