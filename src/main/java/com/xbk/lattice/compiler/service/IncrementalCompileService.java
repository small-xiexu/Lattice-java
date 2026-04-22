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
 * 职责：提供增量规划、增强与同包测试所需的过渡增量编译能力
 *
 * @author xiexu
 */
@Slf4j
public class IncrementalCompileService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String COMPILE_SCENE = "compile";

    private static final String WRITER_ROLE = "writer";

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("\\A---\\R(.*?)\\R---\\R?(.*)\\z", Pattern.DOTALL);

    private static final Pattern REFERENTIAL_PATTERN = Pattern.compile("[A-Za-z0-9_-]+=[A-Za-z0-9._-]+|\\b\\d{3,5}\\b");

    private final IngestNode ingestNode;

    private final GroupNode groupNode;

    private final BatchSplitNode batchSplitNode;

    private final AnalyzeNode analyzeNode;

    private final CrossGroupMergeNode crossGroupMergeNode;

    private final CompileArticleNode compileArticleNode;

    private final LlmGateway llmGateway;

    private final SynthesisArtifactsService synthesisArtifactsService;

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ArticleChunkJdbcRepository articleChunkJdbcRepository;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    private final SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository;

    private final ArticleVectorIndexService articleVectorIndexService;

    private final ArticleChunkVectorIndexService articleChunkVectorIndexService;

    private RepoSnapshotService repoSnapshotService;

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
                new ArticleChunkVectorIndexService()
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
                new ArticleChunkVectorIndexService()
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
        this.ingestNode = new IngestNode(compilerProperties);
        this.groupNode = new GroupNode(compilerProperties);
        this.batchSplitNode = new BatchSplitNode(
                compilerProperties,
                new FileRankingService(compilerProperties)
        );
        this.analyzeNode = new AnalyzeNode(llmGateway);
        this.crossGroupMergeNode = new CrossGroupMergeNode();
        this.compileArticleNode = new CompileArticleNode(
                llmGateway,
                sourceFileJdbcRepository,
                new DocumentSectionSelector(),
                articleReviewerGateway,
                reviewFixService,
                new SchemaAwarePrompts(compilerProperties)
        );
        this.llmGateway = llmGateway;
        this.synthesisArtifactsService = synthesisArtifactsService;
        this.articleJdbcRepository = articleJdbcRepository;
        this.articleChunkJdbcRepository = articleChunkJdbcRepository;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
        this.sourceFileChunkJdbcRepository = sourceFileChunkJdbcRepository;
        this.articleVectorIndexService = articleVectorIndexService;
        this.articleChunkVectorIndexService = articleChunkVectorIndexService;
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
                new ArticleVectorIndexService()
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

    private void captureRepoSnapshot(Path sourceDir, int persistedCount) {
        if (repoSnapshotService == null || persistedCount <= 0) {
            return;
        }
        repoSnapshotService.snapshot("compile.incremental", "sourceDir=" + sourceDir, null);
    }

    /**
     * 分析并合并增量源文件。
     *
     * @param rawSources 原始源文件
     * @return 合并概念
     */
    private List<MergedConcept> analyzeMergedConcepts(List<RawSource> rawSources, Path sourceDir) {
        Map<String, List<RawSource>> groupedSources = groupNode.group(rawSources);
        List<AnalyzedConcept> analyzedConcepts = new ArrayList<AnalyzedConcept>();
        for (Map.Entry<String, List<RawSource>> entry : groupedSources.entrySet()) {
            List<SourceBatch> sourceBatches = batchSplitNode.split(entry.getKey(), entry.getValue());
            analyzedConcepts.addAll(analyzeNode.analyze(entry.getKey(), sourceBatches, sourceDir));
        }
        return crossGroupMergeNode.merge(analyzedConcepts);
    }

    private Optional<SourceFileRecord> findExistingSourceFileRecord(RawSource rawSource) {
        if (rawSource.getSourceId() != null) {
            Optional<SourceFileRecord> sourceAwareRecord = sourceFileJdbcRepository.findBySourceIdAndRelativePath(
                    rawSource.getSourceId(),
                    rawSource.getRelativePath()
            );
            if (sourceAwareRecord.isPresent()) {
                return sourceAwareRecord;
            }
        }
        return sourceFileJdbcRepository.findByPath(rawSource.getRelativePath());
    }

    private boolean hasSourceFileChanged(RawSource rawSource, SourceFileRecord existingRecord) {
        return !sameText(rawSource.getRelativePath(), existingRecord.getRelativePath())
                || !sameText(rawSource.getContent(), existingRecord.getContentText())
                || rawSource.getFileSize() != existingRecord.getFileSize()
                || !sameText(rawSource.getFormat(), existingRecord.getFormat())
                || !sameMetadataJson(rawSource.getMetadataJson(), existingRecord.getMetadataJson())
                || rawSource.isVerbatim() != existingRecord.isVerbatim();
    }

    private boolean sameText(String left, String right) {
        String normalizedLeft = left == null ? "" : left;
        String normalizedRight = right == null ? "" : right;
        return normalizedLeft.equals(normalizedRight);
    }

    private boolean sameMetadataJson(String left, String right) {
        String normalizedLeft = left == null ? "" : left.trim();
        String normalizedRight = right == null ? "" : right.trim();
        if (normalizedLeft.equals(normalizedRight)) {
            return true;
        }
        try {
            return OBJECT_MAPPER.readTree(normalizedLeft).equals(OBJECT_MAPPER.readTree(normalizedRight));
        }
        catch (JsonProcessingException ex) {
            return normalizedLeft.equals(normalizedRight);
        }
    }

    /**
     * 落盘源文件元数据。
     *
     * @param rawSources 原始源文件
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
     * 落盘源文件 chunk。
     *
     * @param rawSources 原始源文件
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
     * 构建源文件预览。
     *
     * @param content 原始内容
     * @return 预览文本
     */
    private String buildContentPreview(String content) {
        int maxPreviewChars = 500;
        if (content.length() <= maxPreviewChars) {
            return content;
        }
        return content.substring(0, maxPreviewChars);
    }

    /**
     * 规划增量编译的增强与新建动作。
     *
     * @param mergedConcepts 增量概念
     * @param existingArticles 已有文章
     * @return 增量计划
     */
    private IncrementalPlan planIncrementalChanges(List<MergedConcept> mergedConcepts, List<ArticleRecord> existingArticles) {
        return buildRuleBasedPlan(mergedConcepts, existingArticles);
    }

    /**
     * 构建规则主导的增量计划。
     *
     * @param mergedConcepts 增量概念
     * @param existingArticles 已有文章
     * @return 增量计划
     */
    private IncrementalPlan buildRuleBasedPlan(List<MergedConcept> mergedConcepts, List<ArticleRecord> existingArticles) {
        Map<String, List<MergedConcept>> directHitConcepts = resolveDirectHitConcepts(mergedConcepts, existingArticles);
        List<EnhancementPlan> enhancements = new ArrayList<EnhancementPlan>();
        for (Map.Entry<String, List<MergedConcept>> entry : directHitConcepts.entrySet()) {
            enhancements.add(buildEnhancementPlan(entry.getKey(), entry.getValue()));
        }
        enhancements.addAll(buildPropagationPlans(directHitConcepts, existingArticles));
        return new IncrementalPlan(enhancements, buildNewArticlePlans(mergedConcepts, existingArticles));
    }

    /**
     * 解析直命中文章集合。
     *
     * @param mergedConcepts 增量概念
     * @param existingArticles 已有文章
     * @return 直命中文章到增量概念的映射
     */
    private Map<String, List<MergedConcept>> resolveDirectHitConcepts(
            List<MergedConcept> mergedConcepts,
            List<ArticleRecord> existingArticles
    ) {
        Map<String, List<MergedConcept>> result = new LinkedHashMap<String, List<MergedConcept>>();
        for (ArticleRecord existingArticle : existingArticles) {
            for (MergedConcept mergedConcept : mergedConcepts) {
                if (!isDirectHit(existingArticle, mergedConcept)) {
                    continue;
                }
                addMatchedConcept(result, existingArticle.getConceptId(), mergedConcept);
            }
        }
        return result;
    }

    /**
     * 判断文章是否被本次变更直接命中。
     *
     * @param existingArticle 已有文章
     * @param mergedConcept 增量概念
     * @return 是否直命中
     */
    private boolean isDirectHit(ArticleRecord existingArticle, MergedConcept mergedConcept) {
        if (normalizeConceptId(existingArticle.getConceptId()).equals(normalizeConceptId(mergedConcept.getConceptId()))) {
            return true;
        }
        return hasSourceIntersection(existingArticle.getSourcePaths(), mergedConcept.getSourcePaths());
    }

    /**
     * 判断两组来源路径是否存在交集。
     *
     * @param leftSourcePaths 左侧来源路径
     * @param rightSourcePaths 右侧来源路径
     * @return 是否存在交集
     */
    private boolean hasSourceIntersection(List<String> leftSourcePaths, List<String> rightSourcePaths) {
        Set<String> normalizedPaths = new LinkedHashSet<String>();
        if (leftSourcePaths == null || rightSourcePaths == null) {
            return false;
        }
        for (String sourcePath : leftSourcePaths) {
            if (sourcePath == null || sourcePath.isBlank()) {
                continue;
            }
            normalizedPaths.add(sourcePath.trim());
        }
        for (String sourcePath : rightSourcePaths) {
            if (sourcePath == null || sourcePath.isBlank()) {
                continue;
            }
            if (normalizedPaths.contains(sourcePath.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建传播增强计划。
     *
     * @param directHitConcepts 直命中文章集合
     * @param existingArticles 已有文章
     * @return 下游增强计划列表
     */
    private List<EnhancementPlan> buildPropagationPlans(
            Map<String, List<MergedConcept>> directHitConcepts,
            List<ArticleRecord> existingArticles
    ) {
        List<EnhancementPlan> propagationPlans = new ArrayList<EnhancementPlan>();
        if (directHitConcepts.isEmpty()) {
            return propagationPlans;
        }
        PropagationService propagationService = new PropagationService(
                new DependencyGraphService(articleJdbcRepository),
                articleJdbcRepository
        );
        Map<String, ArticleRecord> existingArticleMap = indexExistingArticles(existingArticles);
        Set<String> emittedPlanKeys = new LinkedHashSet<String>();
        for (Map.Entry<String, List<MergedConcept>> entry : directHitConcepts.entrySet()) {
            ArticleRecord rootArticle = existingArticleMap.get(normalizeConceptId(entry.getKey()));
            if (rootArticle == null) {
                continue;
            }
            String rootArticleId = resolveArticleId(rootArticle);
            PropagationReport propagationReport = propagationService.analyzeImpact(rootArticleId, "incremental compile");
            List<String> sourceRefs = collectSourceRefs(entry.getValue());
            if (sourceRefs.isEmpty()) {
                continue;
            }
            for (PropagationItem propagationItem : propagationReport.getItems()) {
                String targetArticleId = propagationItem.getConceptId();
                if (targetArticleId == null || targetArticleId.isBlank()) {
                    continue;
                }
                String emittedPlanKey = normalizeConceptId(entry.getKey()) + "->" + normalizeConceptId(targetArticleId);
                if (!emittedPlanKeys.add(emittedPlanKey)) {
                    continue;
                }
                propagationPlans.add(new EnhancementPlan(
                        targetArticleId,
                        buildEnhancementSummary(entry.getValue()),
                        sourceRefs
                ));
            }
        }
        return propagationPlans;
    }

    /**
     * 构建新建文章计划。
     *
     * @param mergedConcepts 增量概念
     * @param existingArticles 已有文章
     * @return 新建文章计划列表
     */
    private List<NewArticlePlan> buildNewArticlePlans(List<MergedConcept> mergedConcepts, List<ArticleRecord> existingArticles) {
        Set<String> existingArticleIds = new LinkedHashSet<String>();
        for (ArticleRecord existingArticle : existingArticles) {
            existingArticleIds.add(normalizeConceptId(existingArticle.getConceptId()));
        }
        Set<String> plannedConceptIds = new LinkedHashSet<String>();
        List<NewArticlePlan> newArticles = new ArrayList<NewArticlePlan>();
        for (MergedConcept mergedConcept : mergedConcepts) {
            String normalizedConceptId = normalizeConceptId(mergedConcept.getConceptId());
            if (existingArticleIds.contains(normalizedConceptId) || !plannedConceptIds.add(normalizedConceptId)) {
                continue;
            }
            newArticles.add(new NewArticlePlan(
                    mergedConcept.getConceptId(),
                    mergedConcept.getTitle(),
                    mergedConcept.getDescription(),
                    mergedConcept.getSourcePaths()
            ));
        }
        return newArticles;
    }

    /**
     * 构建单条增强计划。
     *
     * @param targetArticleId 目标文章标识
     * @param matchedConcepts 命中的增量概念
     * @return 增强计划
     */
    private EnhancementPlan buildEnhancementPlan(String targetArticleId, List<MergedConcept> matchedConcepts) {
        return new EnhancementPlan(
                targetArticleId,
                buildEnhancementSummary(matchedConcepts),
                collectSourceRefs(matchedConcepts)
        );
    }

    /**
     * 构建增强摘要。
     *
     * @param matchedConcepts 命中的增量概念
     * @return 摘要文本
     */
    private String buildEnhancementSummary(List<MergedConcept> matchedConcepts) {
        List<String> summaries = new ArrayList<String>();
        for (MergedConcept matchedConcept : matchedConcepts) {
            if (matchedConcept.getDescription() == null || matchedConcept.getDescription().isBlank()) {
                continue;
            }
            summaries.add(matchedConcept.getDescription().trim());
        }
        return String.join("；", summaries);
    }

    /**
     * 收集增量概念对应的来源路径。
     *
     * @param matchedConcepts 命中的增量概念
     * @return 去重后的来源路径
     */
    private List<String> collectSourceRefs(List<MergedConcept> matchedConcepts) {
        LinkedHashSet<String> sourceRefs = new LinkedHashSet<String>();
        for (MergedConcept matchedConcept : matchedConcepts) {
            sourceRefs.addAll(normalizeTextList(matchedConcept.getSourcePaths()));
        }
        return new ArrayList<String>(sourceRefs);
    }

    /**
     * 解析增强计划对应的概念集合。
     *
     * @param enhancements 增强计划
     * @param mergedConcepts 增量概念
     * @param existingArticles 已有文章
     * @return 按目标文章分组后的概念集合
     */
    private Map<String, List<MergedConcept>> resolveEnhancementConcepts(
            List<EnhancementPlan> enhancements,
            List<MergedConcept> mergedConcepts,
            List<ArticleRecord> existingArticles
    ) {
        Set<String> existingArticleIds = new LinkedHashSet<String>();
        for (ArticleRecord existingArticle : existingArticles) {
            existingArticleIds.add(normalizeConceptId(existingArticle.getConceptId()));
        }

        Map<String, List<MergedConcept>> result = new LinkedHashMap<String, List<MergedConcept>>();
        for (EnhancementPlan enhancementPlan : enhancements) {
            if (!existingArticleIds.contains(normalizeConceptId(enhancementPlan.getTargetArticleId()))) {
                continue;
            }
            List<MergedConcept> matchedConcepts = matchConceptsForEnhancement(enhancementPlan, mergedConcepts);
            if (matchedConcepts.isEmpty()) {
                continue;
            }
            for (MergedConcept matchedConcept : matchedConcepts) {
                addMatchedConcept(result, enhancementPlan.getTargetArticleId(), matchedConcept);
            }
        }
        return result;
    }

    /**
     * 为增强计划匹配具体概念。
     *
     * @param enhancementPlan 增强计划
     * @param mergedConcepts 增量概念
     * @return 命中的概念
     */
    private List<MergedConcept> matchConceptsForEnhancement(
            EnhancementPlan enhancementPlan,
            List<MergedConcept> mergedConcepts
    ) {
        List<MergedConcept> matchedConcepts = new ArrayList<MergedConcept>();
        Set<String> sourceRefs = new LinkedHashSet<String>(normalizeTextList(enhancementPlan.getSourceRefs()));
        for (MergedConcept mergedConcept : mergedConcepts) {
            boolean matchedBySource = false;
            for (String sourcePath : mergedConcept.getSourcePaths()) {
                if (sourceRefs.contains(sourcePath)) {
                    matchedBySource = true;
                    break;
                }
            }
            if (matchedBySource) {
                matchedConcepts.add(mergedConcept);
            }
        }
        if (!matchedConcepts.isEmpty()) {
            return matchedConcepts;
        }
        for (MergedConcept mergedConcept : mergedConcepts) {
            if (normalizeConceptId(mergedConcept.getConceptId()).equals(normalizeConceptId(enhancementPlan.getTargetArticleId()))) {
                matchedConcepts.add(mergedConcept);
            }
        }
        return matchedConcepts;
    }

    /**
     * 解析需要新建文章的概念集合。
     *
     * @param newArticles 新建计划
     * @param mergedConcepts 增量概念
     * @return 待新建概念
     */
    private List<MergedConcept> resolveConceptsToCreate(List<NewArticlePlan> newArticles, List<MergedConcept> mergedConcepts) {
        List<MergedConcept> conceptsToCreate = new ArrayList<MergedConcept>();
        Set<String> conceptIdsToCreate = new LinkedHashSet<String>();
        for (NewArticlePlan newArticle : newArticles) {
            MergedConcept matchedConcept = findMergedConceptById(mergedConcepts, newArticle.getId());
            if (matchedConcept == null) {
                continue;
            }
            if (conceptIdsToCreate.add(normalizeConceptId(matchedConcept.getConceptId()))) {
                conceptsToCreate.add(matchedConcept);
            }
        }
        return conceptsToCreate;
    }

    /**
     * 向命中映射中追加概念，并按概念标识去重。
     *
     * @param matchedConcepts 目标映射
     * @param targetArticleId 目标文章标识
     * @param mergedConcept 增量概念
     */
    private void addMatchedConcept(
            Map<String, List<MergedConcept>> matchedConcepts,
            String targetArticleId,
            MergedConcept mergedConcept
    ) {
        List<MergedConcept> concepts = matchedConcepts.computeIfAbsent(
                targetArticleId,
                key -> new ArrayList<MergedConcept>()
        );
        for (MergedConcept existingConcept : concepts) {
            if (normalizeConceptId(existingConcept.getConceptId()).equals(normalizeConceptId(mergedConcept.getConceptId()))) {
                return;
            }
        }
        concepts.add(mergedConcept);
    }

    /**
     * 建立已有文章索引。
     *
     * @param existingArticles 已有文章
     * @return 按概念标识归一化后的文章映射
     */
    private Map<String, ArticleRecord> indexExistingArticles(List<ArticleRecord> existingArticles) {
        Map<String, ArticleRecord> existingArticleMap = new LinkedHashMap<String, ArticleRecord>();
        for (ArticleRecord existingArticle : existingArticles) {
            existingArticleMap.put(normalizeConceptId(existingArticle.getConceptId()), existingArticle);
        }
        return existingArticleMap;
    }

    /**
     * 解析传播分析使用的根文章标识。
     *
     * @param articleRecord 根文章
     * @return articleKey 或 conceptId
     */
    private String resolveArticleId(ArticleRecord articleRecord) {
        if (articleRecord.getArticleKey() != null && !articleRecord.getArticleKey().isBlank()) {
            return articleRecord.getArticleKey();
        }
        return articleRecord.getConceptId();
    }

    /**
     * 按概念标识查找增量概念。
     *
     * @param mergedConcepts 概念集合
     * @param conceptId 概念标识
     * @return 概念；不存在时返回 null
     */
    private MergedConcept findMergedConceptById(List<MergedConcept> mergedConcepts, String conceptId) {
        String normalizedConceptId = normalizeConceptId(conceptId);
        for (MergedConcept mergedConcept : mergedConcepts) {
            if (normalizeConceptId(mergedConcept.getConceptId()).equals(normalizedConceptId)) {
                return mergedConcept;
            }
        }
        return null;
    }

    /**
     * 增强已有文章。
     *
     * @param existingArticle 已有文章
     * @param mergedConcepts 增量概念
     * @return 更新后的文章
     */
    private ArticleRecord enhanceExistingArticle(ArticleRecord existingArticle, List<MergedConcept> mergedConcepts) {
        List<String> mergedSourcePaths = mergeSourcePaths(existingArticle.getSourcePaths(), mergedConcepts);
        String markdownContent = tryEnhanceWithLlm(existingArticle, mergedConcepts);
        if (markdownContent == null || markdownContent.isBlank()) {
            markdownContent = buildFallbackEnhancedMarkdown(existingArticle, mergedConcepts, mergedSourcePaths);
        }
        FrontmatterValues frontmatterValues = parseFrontmatter(markdownContent);
        List<String> sourcePaths = frontmatterValues.getSources().isEmpty() ? mergedSourcePaths : frontmatterValues.getSources();
        String summary = frontmatterValues.getSummary().isBlank() ? buildEnhancedSummary(existingArticle, mergedConcepts) : frontmatterValues.getSummary();
        List<String> referentialKeywords = frontmatterValues.getReferentialKeywords().isEmpty()
                ? mergeReferentialKeywords(existingArticle.getReferentialKeywords(), mergedConcepts)
                : frontmatterValues.getReferentialKeywords();
        List<String> dependsOn = frontmatterValues.getDependsOn().isEmpty()
                ? existingArticle.getDependsOn()
                : frontmatterValues.getDependsOn();
        List<String> related = frontmatterValues.getRelated().isEmpty()
                ? existingArticle.getRelated()
                : frontmatterValues.getRelated();
        String confidence = frontmatterValues.getConfidence().isBlank()
                ? existingArticle.getConfidence()
                : frontmatterValues.getConfidence();
        String reviewStatus = frontmatterValues.getReviewStatus().isBlank()
                ? existingArticle.getReviewStatus()
                : frontmatterValues.getReviewStatus();
        String title = frontmatterValues.getTitle().isBlank() ? existingArticle.getTitle() : frontmatterValues.getTitle();

        return existingArticle.copy(
                title,
                markdownContent,
                existingArticle.getLifecycle(),
                OffsetDateTime.now(),
                sourcePaths,
                buildIncrementalMetadataJson(summary, sourcePaths, mergedConcepts),
                summary,
                referentialKeywords,
                dependsOn,
                related,
                confidence,
                reviewStatus
        );
    }

    /**
     * 尝试使用 LLM 增强已有文章。
     *
     * @param existingArticle 已有文章
     * @param mergedConcepts 增量概念
     * @return 增强后的 Markdown；失败时返回 null
     */
    private String tryEnhanceWithLlm(ArticleRecord existingArticle, List<MergedConcept> mergedConcepts) {
        if (llmGateway == null) {
            return null;
        }
        try {
            return llmGateway.generateText(
                    COMPILE_SCENE,
                    WRITER_ROLE,
                    "incremental-enhance",
                    LatticePrompts.SYSTEM_INCREMENTAL_ENHANCE,
                    buildIncrementalEnhancePrompt(existingArticle, mergedConcepts)
            );
        }
        catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 构建增量增强 Prompt。
     *
     * @param existingArticle 已有文章
     * @param mergedConcepts 增量概念
     * @return 用户提示词
     */
    private String buildIncrementalEnhancePrompt(ArticleRecord existingArticle, List<MergedConcept> mergedConcepts) {
        StringBuilder builder = new StringBuilder();
        builder.append("EXISTING ARTICLE").append("\n");
        builder.append(existingArticle.getContent()).append("\n\n");
        builder.append("NEW SOURCE MATERIAL").append("\n");
        builder.append(buildSourceContents(mergedConcepts)).append("\n\n");
        builder.append("NEW CONCEPT SUMMARY").append("\n");
        for (MergedConcept mergedConcept : mergedConcepts) {
            builder.append("- ").append(mergedConcept.getTitle()).append(": ").append(mergedConcept.getDescription()).append("\n");
        }
        return builder.toString().trim();
    }

    /**
     * 构建增量增强失败时的回退 Markdown。
     *
     * @param existingArticle 已有文章
     * @param mergedConcepts 增量概念
     * @param sourcePaths 合并后的来源路径
     * @return Markdown 内容
     */
    private String buildFallbackEnhancedMarkdown(
            ArticleRecord existingArticle,
            List<MergedConcept> mergedConcepts,
            List<String> sourcePaths
    ) {
        String summary = buildEnhancedSummary(existingArticle, mergedConcepts);
        StringBuilder builder = new StringBuilder();
        builder.append("---").append("\n");
        builder.append("title: ").append("\"").append(escapeYaml(existingArticle.getTitle())).append("\"").append("\n");
        builder.append("summary: ").append("\"").append(escapeYaml(summary)).append("\"").append("\n");
        builder.append("referential_keywords: ").append(formatYamlList(mergeReferentialKeywords(existingArticle.getReferentialKeywords(), mergedConcepts))).append("\n");
        builder.append("sources: ").append(formatYamlList(sourcePaths)).append("\n");
        builder.append("depends_on: ").append(formatYamlList(existingArticle.getDependsOn())).append("\n");
        builder.append("related: ").append(formatYamlList(existingArticle.getRelated())).append("\n");
        builder.append("confidence: ").append(existingArticle.getConfidence()).append("\n");
        builder.append("compiled_at: ").append("\"").append(OffsetDateTime.now()).append("\"").append("\n");
        builder.append("review_status: ").append(existingArticle.getReviewStatus()).append("\n");
        builder.append("---").append("\n\n");
        String body = extractBody(existingArticle.getContent());
        if (!body.isBlank()) {
            builder.append(body.trim()).append("\n\n");
        }
        builder.append("## 增量更新").append("\n");
        for (MergedConcept mergedConcept : mergedConcepts) {
            builder.append("### ").append(mergedConcept.getTitle()).append("\n");
            if (mergedConcept.getDescription() != null && !mergedConcept.getDescription().isBlank()) {
                builder.append(mergedConcept.getDescription()).append("\n");
            }
            for (ConceptSection section : mergedConcept.getSections()) {
                builder.append("#### ").append(section.getHeading()).append("\n");
                for (String contentLine : section.getContentLines()) {
                    builder.append("- ").append(contentLine).append("\n");
                }
                if (!section.getSourceRefs().isEmpty()) {
                    builder.append("> Sources: ").append(String.join(", ", section.getSourceRefs())).append("\n");
                }
            }
            if (!mergedConcept.getSourcePaths().isEmpty()) {
                builder.append("> Incremental Sources: ").append(String.join(", ", mergedConcept.getSourcePaths())).append("\n");
            }
            builder.append("\n");
        }
        return builder.toString().trim();
    }

    /**
     * 构建增强后的摘要。
     *
     * @param existingArticle 已有文章
     * @param mergedConcepts 增量概念
     * @return 摘要
     */
    private String buildEnhancedSummary(ArticleRecord existingArticle, List<MergedConcept> mergedConcepts) {
        String summary = existingArticle.getSummary();
        for (MergedConcept mergedConcept : mergedConcepts) {
            if (mergedConcept.getDescription() == null || mergedConcept.getDescription().isBlank()) {
                continue;
            }
            if (summary == null || summary.isBlank()) {
                summary = mergedConcept.getDescription().trim();
                continue;
            }
            if (!summary.contains(mergedConcept.getDescription().trim())) {
                summary = summary + "；" + mergedConcept.getDescription().trim();
            }
        }
        return summary == null ? "" : summary;
    }

    /**
     * 构建文章元数据 JSON。
     *
     * @param summary 摘要
     * @param sourcePaths 来源路径
     * @param mergedConcepts 增量概念
     * @return 元数据 JSON
     */
    private String buildIncrementalMetadataJson(
            String summary,
            List<String> sourcePaths,
            List<MergedConcept> mergedConcepts
    ) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("incremental", true);
        metadata.put("summary", summary);
        metadata.put("sourceCount", sourcePaths.size());
        metadata.put("enhancementCount", mergedConcepts.size());
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("构建增量编译 metadata 失败", ex);
        }
    }

    /**
     * 合并文章来源路径。
     *
     * @param existingSourcePaths 旧来源
     * @param mergedConcepts 增量概念
     * @return 合并后的来源路径
     */
    private List<String> mergeSourcePaths(List<String> existingSourcePaths, List<MergedConcept> mergedConcepts) {
        LinkedHashSet<String> sourcePaths = new LinkedHashSet<String>(existingSourcePaths);
        for (MergedConcept mergedConcept : mergedConcepts) {
            sourcePaths.addAll(mergedConcept.getSourcePaths());
        }
        return new ArrayList<String>(sourcePaths);
    }

    /**
     * 合并明确性关键词。
     *
     * @param existingKeywords 旧关键词
     * @param mergedConcepts 增量概念
     * @return 合并后的关键词
     */
    private List<String> mergeReferentialKeywords(List<String> existingKeywords, List<MergedConcept> mergedConcepts) {
        LinkedHashSet<String> keywords = new LinkedHashSet<String>(existingKeywords);
        for (MergedConcept mergedConcept : mergedConcepts) {
            for (ConceptSection section : mergedConcept.getSections()) {
                for (String contentLine : section.getContentLines()) {
                    Matcher matcher = REFERENTIAL_PATTERN.matcher(contentLine);
                    while (matcher.find()) {
                        keywords.add(matcher.group());
                    }
                }
            }
        }
        return new ArrayList<String>(keywords);
    }

    /**
     * 构建增量源文件正文。
     *
     * @param mergedConcepts 增量概念
     * @return 源文件正文
     */
    private String buildSourceContents(List<MergedConcept> mergedConcepts) {
        StringBuilder builder = new StringBuilder();
        Set<String> visitedSourcePaths = new LinkedHashSet<String>();
        for (MergedConcept mergedConcept : mergedConcepts) {
            for (String sourcePath : mergedConcept.getSourcePaths()) {
                if (!visitedSourcePaths.add(sourcePath)) {
                    continue;
                }
                Optional<SourceFileRecord> sourceFileRecord = sourceFileJdbcRepository.findByPath(sourcePath);
                if (sourceFileRecord.isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append("\n\n");
                }
                builder.append("=== Source: ").append(sourcePath).append(" ===").append("\n");
                builder.append(sourceFileRecord.orElseThrow().getContentText()).append("\n");
                builder.append("=== End ===");
            }
        }
        return builder.toString();
    }

    /**
     * 刷新合成产物。
     */
    private void refreshSynthesisArtifacts(String jobId) {
        if (synthesisArtifactsService == null) {
            return;
        }
        List<MergedConcept> knowledgeBaseConcepts = new ArrayList<MergedConcept>();
        for (ArticleRecord articleRecord : articleJdbcRepository.findAll()) {
            knowledgeBaseConcepts.add(new MergedConcept(
                    articleRecord.getConceptId(),
                    articleRecord.getTitle(),
                    articleRecord.getSummary(),
                    articleRecord.getSourcePaths(),
                    List.of(),
                    List.of()
            ));
        }
        synthesisArtifactsService.generateAll(jobId, knowledgeBaseConcepts);
    }

    /**
     * 归一化字符串列表。
     *
     * @param rawValues 原始字符串列表
     * @return 归一化后的字符串列表
     */
    private List<String> normalizeTextList(List<String> rawValues) {
        List<String> values = new ArrayList<String>();
        if (rawValues == null) {
            return values;
        }
        for (String rawValue : rawValues) {
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            values.add(rawValue.trim());
        }
        return values;
    }

    /**
     * 从 Markdown code fence 中提取 JSON 主体。
     *
     * @param content 原始文本
     * @return 归一化后的 JSON 文本
     */
    private String unwrapJsonCodeFence(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.trim();
        int fenceStartIndex = normalized.indexOf("```");
        if (fenceStartIndex < 0) {
            return normalized;
        }
        int firstLineBreakIndex = normalized.indexOf('\n', fenceStartIndex);
        if (firstLineBreakIndex < 0) {
            return normalized;
        }
        int closingFenceIndex = normalized.indexOf("```", firstLineBreakIndex + 1);
        if (closingFenceIndex < 0) {
            return normalized;
        }
        return normalized.substring(firstLineBreakIndex + 1, closingFenceIndex).trim();
    }

    /**
     * 解析 frontmatter。
     *
     * @param markdownContent Markdown 内容
     * @return frontmatter 字段
     */
    private FrontmatterValues parseFrontmatter(String markdownContent) {
        Matcher matcher = FRONTMATTER_PATTERN.matcher(markdownContent.trim());
        if (!matcher.find()) {
            return FrontmatterValues.empty();
        }
        String frontmatter = matcher.group(1);
        Map<String, String> values = new LinkedHashMap<String, String>();
        String[] lines = frontmatter.split("\\R");
        for (String line : lines) {
            int separatorIndex = line.indexOf(':');
            if (separatorIndex < 0) {
                continue;
            }
            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            values.put(key, value);
        }
        return new FrontmatterValues(
                stripQuotes(values.get("title")),
                stripQuotes(values.get("summary")),
                parseYamlList(values.get("referential_keywords")),
                parseYamlList(values.get("sources")),
                parseYamlList(values.get("depends_on")),
                parseYamlList(values.get("related")),
                stripQuotes(values.get("confidence")),
                stripQuotes(values.get("review_status"))
        );
    }

    /**
     * 提取 Markdown 主体。
     *
     * @param markdownContent Markdown 内容
     * @return 主体内容
     */
    private String extractBody(String markdownContent) {
        Matcher matcher = FRONTMATTER_PATTERN.matcher(markdownContent.trim());
        if (!matcher.find()) {
            return markdownContent == null ? "" : markdownContent;
        }
        return matcher.group(2);
    }

    /**
     * 解析 YAML 行内列表。
     *
     * @param rawValue 原始值
     * @return 列表内容
     */
    private List<String> parseYamlList(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        String trimmedValue = rawValue.trim();
        if ("[]".equals(trimmedValue)) {
            return List.of();
        }
        if (trimmedValue.startsWith("[") && trimmedValue.endsWith("]")) {
            String innerValue = trimmedValue.substring(1, trimmedValue.length() - 1).trim();
            if (innerValue.isBlank()) {
                return List.of();
            }
            String[] items = innerValue.split(",");
            List<String> values = new ArrayList<String>();
            for (String item : items) {
                String value = stripQuotes(item.trim());
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
            return values;
        }
        return List.of(stripQuotes(trimmedValue));
    }

    /**
     * 去除 YAML 引号。
     *
     * @param value 原始值
     * @return 去引号后的值
     */
    private String stripQuotes(String value) {
        if (value == null) {
            return "";
        }
        String trimmedValue = value.trim();
        if (trimmedValue.length() >= 2
                && trimmedValue.startsWith("\"")
                && trimmedValue.endsWith("\"")) {
            return trimmedValue.substring(1, trimmedValue.length() - 1);
        }
        return trimmedValue;
    }

    /**
     * 格式化 YAML 行内列表。
     *
     * @param values 值列表
     * @return YAML 行内列表
     */
    private String formatYamlList(List<String> values) {
        List<String> escapedValues = new ArrayList<String>();
        for (String value : values) {
            escapedValues.add("\"" + escapeYaml(value) + "\"");
        }
        return "[" + String.join(", ", escapedValues) + "]";
    }

    /**
     * 转义 YAML 文本。
     *
     * @param value 原始文本
     * @return 转义后的文本
     */
    private String escapeYaml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 归一化概念标识。
     *
     * @param conceptId 原始概念标识
     * @return 归一化概念标识
     */
    private String normalizeConceptId(String conceptId) {
        return conceptId == null ? "" : conceptId.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 增量计划。
     *
     * 职责：承载增强与新建文章两类动作
     *
     * @author xiexu
     */
    private static class IncrementalPlan {

        private final List<EnhancementPlan> enhancements;

        private final List<NewArticlePlan> newArticles;

        private IncrementalPlan(List<EnhancementPlan> enhancements, List<NewArticlePlan> newArticles) {
            this.enhancements = enhancements;
            this.newArticles = newArticles;
        }

        private List<EnhancementPlan> getEnhancements() {
            return enhancements;
        }

        private List<NewArticlePlan> getNewArticles() {
            return newArticles;
        }
    }

    /**
     * 增强计划。
     *
     * 职责：描述某篇已有文章需要吸收的增量信息
     *
     * @author xiexu
     */
    private static class EnhancementPlan {

        private final String targetArticleId;

        private final String newInfoSummary;

        private final List<String> sourceRefs;

        private EnhancementPlan(String targetArticleId, String newInfoSummary, List<String> sourceRefs) {
            this.targetArticleId = targetArticleId;
            this.newInfoSummary = newInfoSummary;
            this.sourceRefs = sourceRefs;
        }

        private String getTargetArticleId() {
            return targetArticleId;
        }

        private String getNewInfoSummary() {
            return newInfoSummary;
        }

        private List<String> getSourceRefs() {
            return sourceRefs;
        }
    }

    /**
     * 新建文章计划。
     *
     * 职责：描述待新建文章的目标概念
     *
     * @author xiexu
     */
    private static class NewArticlePlan {

        private final String id;

        private final String title;

        private final String description;

        private final List<String> sourceRefs;

        private NewArticlePlan(String id, String title, String description, List<String> sourceRefs) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.sourceRefs = sourceRefs;
        }

        private String getId() {
            return id;
        }

        private String getTitle() {
            return title;
        }

        private String getDescription() {
            return description;
        }

        private List<String> getSourceRefs() {
            return sourceRefs;
        }
    }

    /**
     * frontmatter 解析结果。
     *
     * 职责：承载 Markdown frontmatter 中的结构化字段
     *
     * @author xiexu
     */
    private static class FrontmatterValues {

        private final String title;

        private final String summary;

        private final List<String> referentialKeywords;

        private final List<String> sources;

        private final List<String> dependsOn;

        private final List<String> related;

        private final String confidence;

        private final String reviewStatus;

        private FrontmatterValues(
                String title,
                String summary,
                List<String> referentialKeywords,
                List<String> sources,
                List<String> dependsOn,
                List<String> related,
                String confidence,
                String reviewStatus
        ) {
            this.title = title;
            this.summary = summary;
            this.referentialKeywords = referentialKeywords;
            this.sources = sources;
            this.dependsOn = dependsOn;
            this.related = related;
            this.confidence = confidence;
            this.reviewStatus = reviewStatus;
        }

        private static FrontmatterValues empty() {
            return new FrontmatterValues("", "", List.of(), List.of(), List.of(), List.of(), "", "");
        }

        private String getTitle() {
            return title;
        }

        private String getSummary() {
            return summary;
        }

        private List<String> getReferentialKeywords() {
            return referentialKeywords;
        }

        private List<String> getSources() {
            return sources;
        }

        private List<String> getDependsOn() {
            return dependsOn;
        }

        private List<String> getRelated() {
            return related;
        }

        private String getConfidence() {
            return confidence;
        }

        private String getReviewStatus() {
            return reviewStatus;
        }
    }
}
