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
import com.xbk.lattice.source.infra.KnowledgeSourceJdbcRepository;
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
 * 增量编译写入支持。
 *
 * 职责：处理源文件变更判断、source/chunk 写入、fact card 重建和快照记录。
 *
 * @author xiexu
 */
@Slf4j
abstract class IncrementalCompileWritebackSupport extends IncrementalCompileBaseSupport {

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
    protected IncrementalCompileWritebackSupport(
            CompilerProperties compilerProperties,
            LlmGateway llmGateway,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SynthesisArtifactsService synthesisArtifactsService,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            KnowledgeSourceJdbcRepository knowledgeSourceJdbcRepository,
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
                knowledgeSourceJdbcRepository,
                sourceFileChunkJdbcRepository,
                articleVectorIndexService,
                articleChunkVectorIndexService,
                documentParseApplicationService
        );
    }

    protected void captureRepoSnapshot(Path sourceDir, int persistedCount) {
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
    protected List<MergedConcept> analyzeMergedConcepts(List<RawSource> rawSources, Path sourceDir) {
        Map<String, List<RawSource>> groupedSources = groupNode.group(rawSources);
        List<AnalyzedConcept> analyzedConcepts = new ArrayList<AnalyzedConcept>();
        for (Map.Entry<String, List<RawSource>> entry : groupedSources.entrySet()) {
            List<SourceBatch> sourceBatches = batchSplitNode.split(entry.getKey(), entry.getValue());
            analyzedConcepts.addAll(analyzeNode.analyze(entry.getKey(), sourceBatches, sourceDir));
        }
        return crossGroupMergeNode.merge(analyzedConcepts);
    }
    protected Optional<SourceFileRecord> findExistingSourceFileRecord(RawSource rawSource) {
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
    protected boolean hasSourceFileChanged(RawSource rawSource, SourceFileRecord existingRecord) {
        return !sameText(rawSource.getRelativePath(), existingRecord.getRelativePath())
                || !sameText(rawSource.getContent(), existingRecord.getContentText())
                || rawSource.getFileSize() != existingRecord.getFileSize()
                || !sameText(rawSource.getFormat(), existingRecord.getFormat())
                || !sameMetadataJson(rawSource.getMetadataJson(), existingRecord.getMetadataJson())
                || rawSource.isVerbatim() != existingRecord.isVerbatim();
    }
    protected boolean sameText(String left, String right) {
        String normalizedLeft = left == null ? "" : left;
        String normalizedRight = right == null ? "" : right;
        return normalizedLeft.equals(normalizedRight);
    }
    protected boolean sameMetadataJson(String left, String right) {
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
    protected void persistSourceFiles(List<RawSource> rawSources) {
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
    protected void persistSourceFileChunks(List<RawSource> rawSources) {
        if (sourceFileChunkJdbcRepository == null) {
            return;
        }

        for (RawSource rawSource : rawSources) {
            Long sourceFileId = resolveSourceFileId(rawSource);
            sourceFileChunkJdbcRepository.replaceChunksFromContent(
                    sourceFileId,
                    rawSource.getRelativePath(),
                    rawSource.getContent(),
                    rawSource.isVerbatim()
            );
            rebuildFactCards(sourceFileId);
        }
    }
    /**
     * 基于最新 source chunks 重建事实证据卡。
     *
     * @param sourceFileId 源文件主键
     */
    protected void rebuildFactCards(Long sourceFileId) {
        if (factCardGenerationService == null || sourceFileId == null) {
            return;
        }
        factCardGenerationService.rebuildForSourceFile(sourceFileId);
    }
    /**
     * 解析源文件主键。
     *
     * @param rawSource 原始源文件
     * @return 源文件主键
     */
    protected Long resolveSourceFileId(RawSource rawSource) {
        Optional<SourceFileRecord> sourceFileRecord = findExistingSourceFileRecord(rawSource);
        if (sourceFileRecord.isEmpty()) {
            return null;
        }
        return sourceFileRecord.orElseThrow().getId();
    }
    /**
     * 构建源文件预览。
     *
     * @param content 原始内容
     * @return 预览文本
     */
    protected String buildContentPreview(String content) {
        int maxPreviewChars = 500;
        if (content.length() <= maxPreviewChars) {
            return content;
        }
        return content.substring(0, maxPreviewChars);
    }
}
