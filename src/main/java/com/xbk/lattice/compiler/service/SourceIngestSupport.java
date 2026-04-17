package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.model.AnalyzedConcept;
import com.xbk.lattice.compiler.model.MergedConcept;
import com.xbk.lattice.compiler.model.RawSource;
import com.xbk.lattice.compiler.model.SourceBatch;
import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.query.service.ArticleVectorIndexService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 编译源数据支撑服务
 *
 * 职责：承载 Graph 所需的源文件摄入、分析、增量规划与源数据持久化能力
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class SourceIngestSupport {

    private final IngestNode ingestNode;

    private final GroupNode groupNode;

    private final BatchSplitNode batchSplitNode;

    private final AnalyzeNode analyzeNode;

    private final CrossGroupMergeNode crossGroupMergeNode;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    private final SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository;

    private final CompilationWalStore compilationWalStore;

    private final IncrementalCompileService incrementalCompileService;

    /**
     * 创建编译源数据支撑服务。
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
     * @param articleVectorIndexService 向量索引服务
     */
    public SourceIngestSupport(
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
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
        this.sourceFileChunkJdbcRepository = sourceFileChunkJdbcRepository;
        this.compilationWalStore = compilationWalStore;
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
     * 摄入源目录。
     *
     * @param sourceDir 源目录
     * @return 原始源文件集合
     * @throws IOException IO 异常
     */
    public List<RawSource> ingest(Path sourceDir) throws IOException {
        return ingestNode.ingest(sourceDir);
    }

    /**
     * 对源文件集合进行分组。
     *
     * @param rawSources 原始源文件集合
     * @return 分组结果
     */
    public Map<String, List<RawSource>> groupSources(List<RawSource> rawSources) {
        return groupNode.group(rawSources);
    }

    /**
     * 对分组结果执行分批。
     *
     * @param groupedSources 分组结果
     * @return 分批结果
     */
    public Map<String, List<SourceBatch>> splitBatches(Map<String, List<RawSource>> groupedSources) {
        Map<String, List<SourceBatch>> sourceBatches = new java.util.LinkedHashMap<String, List<SourceBatch>>();
        for (Map.Entry<String, List<RawSource>> entry : groupedSources.entrySet()) {
            sourceBatches.put(entry.getKey(), batchSplitNode.split(entry.getKey(), entry.getValue()));
        }
        return sourceBatches;
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
        List<AnalyzedConcept> analyzedConcepts = new ArrayList<AnalyzedConcept>();
        for (Map.Entry<String, List<SourceBatch>> entry : sourceBatches.entrySet()) {
            analyzedConcepts.addAll(analyzeNode.analyze(entry.getKey(), entry.getValue(), sourceDir));
        }
        return analyzedConcepts;
    }

    /**
     * 合并分析结果。
     *
     * @param analyzedConcepts 分析结果
     * @return 合并概念结果
     */
    public List<MergedConcept> mergeConcepts(List<AnalyzedConcept> analyzedConcepts) {
        return crossGroupMergeNode.merge(analyzedConcepts);
    }

    /**
     * 暂存待提交概念到 WAL。
     *
     * @param jobId 作业标识
     * @param mergedConcepts 合并概念列表
     */
    public void stageWal(String jobId, List<MergedConcept> mergedConcepts) {
        compilationWalStore.stage(jobId, mergedConcepts);
    }

    /**
     * 执行增量规划。
     *
     * @param mergedConcepts 合并概念
     * @return 增量规划结果
     */
    public IncrementalCompilePlanResult planIncrementalGraphChanges(List<MergedConcept> mergedConcepts) {
        return incrementalCompileService.planGraphChanges(mergedConcepts);
    }

    /**
     * 生成增强文章草稿。
     *
     * @param enhancementConcepts 增强映射
     * @return 增强草稿列表
     */
    public List<com.xbk.lattice.infra.persistence.ArticleRecord> enhanceExistingArticles(
            Map<String, List<MergedConcept>> enhancementConcepts
    ) {
        return incrementalCompileService.enhanceExistingArticles(enhancementConcepts);
    }

    /**
     * 刷新合成产物。
     */
    public void refreshGraphSynthesisArtifacts() {
        incrementalCompileService.refreshGraphSynthesisArtifacts();
    }

    /**
     * 落盘源文件预览。
     *
     * @param rawSources 原始源文件集合
     */
    @Transactional(rollbackFor = Exception.class)
    public void persistSourceFiles(List<RawSource> rawSources) {
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
     * 落盘源文件分块。
     *
     * @param rawSources 原始源文件集合
     */
    @Transactional(rollbackFor = Exception.class)
    public void persistSourceFileChunks(List<RawSource> rawSources) {
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
}
