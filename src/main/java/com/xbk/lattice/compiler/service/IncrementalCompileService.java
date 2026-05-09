package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.domain.ConceptSection;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.compiler.node.AnalyzeNode;
import com.xbk.lattice.compiler.node.BatchSplitNode;
import com.xbk.lattice.compiler.node.CompileArticleNode;
import com.xbk.lattice.compiler.node.CrossGroupMergeNode;
import com.xbk.lattice.compiler.node.GroupNode;
import com.xbk.lattice.compiler.node.IngestNode;
import com.xbk.lattice.compiler.prompt.LatticePrompts;
import com.xbk.lattice.compiler.prompt.SchemaAwarePrompts;
import com.xbk.lattice.documentparse.application.DocumentParseApplicationService;
import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.governance.DependencyGraphService;
import com.xbk.lattice.governance.PropagationItem;
import com.xbk.lattice.governance.PropagationReport;
import com.xbk.lattice.governance.PropagationService;
import com.xbk.lattice.governance.repo.RepoSnapshotService;
import com.xbk.lattice.query.service.ArticleChunkVectorIndexService;
import com.xbk.lattice.query.service.ArticleVectorIndexService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 增量编译节点能力服务
 *
 * 职责：提供增量规划、增强与同包测试所需的增量编译能力
 *
 * @author xiexu
 */
@Slf4j
public class IncrementalCompileService extends IncrementalCompileEnhancementSupport {

    /**
     * 创建增量编译服务。
     *
     * @param compilerProperties 编译配置
     * @param llmGateway LLM 网关
     * @param articleReviewerGateway 文章审查网关
     * @param reviewFixService 审查修复服务
     * @param synthesisArtifactsService 合成产物服务
     * @param articleJdbcRepository 文章仓储
     * @param articleChunkJdbcRepository 文章 chunk 仓储
     * @param sourceFileJdbcRepository 源文件仓储
     */
    public IncrementalCompileService(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SynthesisArtifactsService synthesisArtifactsService,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository
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
                null,
                new ArticleVectorIndexService(),
                new ArticleChunkVectorIndexService(),
                null
        );
    }
    /**
     * 创建增量编译服务。
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
     * @param articleVectorIndexService 文章向量索引服务
     * @param documentParseApplicationService 文档解析应用服务
     */
    public IncrementalCompileService(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SynthesisArtifactsService synthesisArtifactsService,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository,
            ArticleVectorIndexService articleVectorIndexService,
            DocumentParseApplicationService documentParseApplicationService
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
                articleVectorIndexService,
                new ArticleChunkVectorIndexService(),
                documentParseApplicationService
        );
    }
    /**
     * 创建增量编译服务。
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
     * @param articleVectorIndexService 文章向量索引服务
     * @param articleChunkVectorIndexService 文章分块向量索引服务
     */
    public IncrementalCompileService(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SynthesisArtifactsService synthesisArtifactsService,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository,
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
                articleVectorIndexService,
                articleChunkVectorIndexService,
                null
        );
    }
    /**
     * 创建增量编译服务。
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
     * @param articleVectorIndexService 文章向量索引服务
     */
    public IncrementalCompileService(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SynthesisArtifactsService synthesisArtifactsService,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository,
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
                articleVectorIndexService,
                new ArticleChunkVectorIndexService(),
                null
        );
    }
    /**
     * 创建增量编译服务。
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
     * @param articleVectorIndexService 文章向量索引服务
     * @param articleChunkVectorIndexService 文章分块向量索引服务
     * @param documentParseApplicationService 文档解析应用服务
     */
    public IncrementalCompileService(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SynthesisArtifactsService synthesisArtifactsService,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository,
            ArticleVectorIndexService articleVectorIndexService,
            ArticleChunkVectorIndexService articleChunkVectorIndexService,
            DocumentParseApplicationService documentParseApplicationService
    ) {
        super(
                compilerProperties,
                llmGateway,
                articleReviewerGateway,
                reviewFixService,
                synthesisArtifactsService,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository,
                sourceFileChunkJdbcRepository,
                articleVectorIndexService,
                articleChunkVectorIndexService,
                documentParseApplicationService
        );
    }
    /**
     * 注入整库快照服务。
     *
     * @param repoSnapshotService 整库快照服务
     */
    public void setRepoSnapshotService(RepoSnapshotService repoSnapshotService) {
        this.repoSnapshotService = repoSnapshotService;
    }
    /**
     * 注入事实证据卡生成服务。
     *
     * @param factCardGenerationService 事实证据卡生成服务
     */
    public void setFactCardGenerationService(FactCardGenerationService factCardGenerationService) {
        this.factCardGenerationService = factCardGenerationService;
    }
    /**
     * 创建增量编译服务。
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
     */
    public IncrementalCompileService(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SynthesisArtifactsService synthesisArtifactsService,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository
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
                new ArticleVectorIndexService(),
                new ArticleChunkVectorIndexService(),
                null
        );
    }
    /**
     * 对新增源目录执行增量编译。
     *
     * @param sourceDir 源目录
     * @return 编译结果
     * @throws IOException IO 异常
     */
    CompileResult incrementalCompile(Path sourceDir) throws IOException {
        String jobId = UUID.randomUUID().toString();
        log.info("Incremental compile started sourceDir: {}", sourceDir);
        List<RawSource> ingestedRawSources = ingestNode.ingest(sourceDir);
        List<RawSource> rawSources = filterChangedRawSources(ingestedRawSources);
        log.info(
                "Incremental compile filtered rawSources sourceDir: {}, changedCount: {}, ingestedCount: {}",
                sourceDir,
                rawSources.size(),
                ingestedRawSources.size()
        );
        persistSourceFiles(rawSources);
        persistSourceFileChunks(rawSources);
        List<MergedConcept> mergedConcepts = analyzeMergedConcepts(rawSources, sourceDir);
        List<ArticleRecord> existingArticles = articleJdbcRepository.findAll();
        IncrementalPlan incrementalPlan = planIncrementalChanges(mergedConcepts, existingArticles);
        Map<String, List<MergedConcept>> enhancementConcepts = resolveEnhancementConcepts(
                incrementalPlan.getEnhancements(),
                mergedConcepts,
                existingArticles
        );
        int persistedCount = 0;

        for (Map.Entry<String, List<MergedConcept>> entry : enhancementConcepts.entrySet()) {
            Optional<ArticleRecord> existingArticle = articleJdbcRepository.findByConceptId(entry.getKey());
            if (existingArticle.isEmpty() || entry.getValue().isEmpty()) {
                continue;
            }
            ArticleRecord updatedArticle = enhanceExistingArticle(existingArticle.orElseThrow(), entry.getValue());
            articleJdbcRepository.upsert(updatedArticle);
            articleChunkJdbcRepository.replaceChunksFromContent(
                    updatedArticle.getArticleKey(),
                    updatedArticle.getConceptId(),
                    updatedArticle.getContent()
            );
            articleVectorIndexService.indexArticle(updatedArticle);
            if (articleChunkVectorIndexService != null) {
                articleChunkVectorIndexService.indexArticle(updatedArticle);
            }
            persistedCount++;
        }

        List<MergedConcept> conceptsToCreate = resolveConceptsToCreate(incrementalPlan.getNewArticles(), mergedConcepts);
        for (MergedConcept mergedConcept : conceptsToCreate) {
            ArticleRecord createdArticle = compileArticleNode.compile(mergedConcept, sourceDir);
            articleJdbcRepository.upsert(createdArticle);
            articleChunkJdbcRepository.replaceChunksFromContent(
                    createdArticle.getArticleKey(),
                    createdArticle.getConceptId(),
                    createdArticle.getContent()
            );
            articleVectorIndexService.indexArticle(createdArticle);
            if (articleChunkVectorIndexService != null) {
                articleChunkVectorIndexService.indexArticle(createdArticle);
            }
            persistedCount++;
        }

        refreshSynthesisArtifacts(jobId);
        captureRepoSnapshot(sourceDir, persistedCount);
        log.info("Incremental compile completed sourceDir: {}, jobId: {}, persistedCount: {}", sourceDir, jobId, persistedCount);
        return new CompileResult(persistedCount, jobId);
    }
    /**
     * 生成增量规划结果。
     *
     * @param mergedConcepts 合并概念
     * @return 增量规划结果
     */
    public IncrementalCompilePlanResult planGraphChanges(List<MergedConcept> mergedConcepts) {
        List<ArticleRecord> existingArticles = articleJdbcRepository.findAll();
        IncrementalPlan incrementalPlan = planIncrementalChanges(mergedConcepts, existingArticles);
        Map<String, List<MergedConcept>> enhancementConcepts = resolveEnhancementConcepts(
                incrementalPlan.getEnhancements(),
                mergedConcepts,
                existingArticles
        );
        List<MergedConcept> conceptsToCreate = resolveConceptsToCreate(incrementalPlan.getNewArticles(), mergedConcepts);

        IncrementalCompilePlanResult result = new IncrementalCompilePlanResult();
        result.setEnhancementConcepts(enhancementConcepts);
        result.setConceptsToCreate(conceptsToCreate);
        result.setNothingToDo(enhancementConcepts.isEmpty() && conceptsToCreate.isEmpty());
        return result;
    }
    /**
     * 批量增强已有文章，返回增强后的草稿集合。
     *
     * @param enhancementConcepts 按目标文章分组的增量概念
     * @return 增强后的草稿集合
     */
    public List<ArticleRecord> enhanceExistingArticles(Map<String, List<MergedConcept>> enhancementConcepts) {
        List<ArticleRecord> draftArticles = new ArrayList<ArticleRecord>();
        for (Map.Entry<String, List<MergedConcept>> entry : enhancementConcepts.entrySet()) {
            Optional<ArticleRecord> existingArticle = articleJdbcRepository.findByConceptId(entry.getKey());
            if (existingArticle.isEmpty() || entry.getValue().isEmpty()) {
                continue;
            }
            draftArticles.add(enhanceExistingArticle(existingArticle.orElseThrow(), entry.getValue()));
        }
        return draftArticles;
    }
    /**
     * 刷新合成产物。
     */
    public void refreshGraphSynthesisArtifacts() {
        refreshGraphSynthesisArtifacts(null);
    }
    /**
     * 在指定作业作用域下刷新合成产物。
     *
     * @param jobId 作业标识
     */
    public void refreshGraphSynthesisArtifacts(String jobId) {
        refreshSynthesisArtifacts(jobId);
    }
    /**
     * 过滤本次真正发生变化的原始源文件。
     *
     * @param rawSources 原始源文件集合
     * @return 仅包含新增或内容变化文件的集合
     */
    public List<RawSource> filterChangedRawSources(List<RawSource> rawSources) {
        List<RawSource> changedRawSources = new ArrayList<RawSource>();
        for (RawSource rawSource : rawSources) {
            Optional<SourceFileRecord> existingRecord = findExistingSourceFileRecord(rawSource);
            if (existingRecord.isEmpty() || hasSourceFileChanged(rawSource, existingRecord.orElseThrow())) {
                changedRawSources.add(rawSource);
            }
        }
        return changedRawSources;
    }
}
