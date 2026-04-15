package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.model.AnalyzedConcept;
import com.xbk.lattice.compiler.model.ConceptSection;
import com.xbk.lattice.compiler.model.MergedConcept;
import com.xbk.lattice.compiler.model.RawSource;
import com.xbk.lattice.compiler.model.SourceBatch;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IngestNode ingestNode;

    private final GroupNode groupNode;

    private final BatchSplitNode batchSplitNode;

    private final AnalyzeNode analyzeNode;

    private final CrossGroupMergeNode crossGroupMergeNode;

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ArticleChunkJdbcRepository articleChunkJdbcRepository;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    /**
     * 创建最小编译链路服务。
     *
     * @param compilerProperties 编译配置
     * @param articleJdbcRepository 文章仓储
     * @param articleChunkJdbcRepository 文章 chunk 仓储
     * @param sourceFileJdbcRepository 源文件仓储
     */
    public CompilePipelineService(
            CompilerProperties compilerProperties,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository
    ) {
        this.ingestNode = new IngestNode(compilerProperties);
        this.groupNode = new GroupNode(compilerProperties);
        this.batchSplitNode = new BatchSplitNode(compilerProperties);
        this.analyzeNode = new AnalyzeNode();
        this.crossGroupMergeNode = new CrossGroupMergeNode();
        this.articleJdbcRepository = articleJdbcRepository;
        this.articleChunkJdbcRepository = articleChunkJdbcRepository;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
    }

    /**
     * 编译源目录并落盘文章。
     *
     * @param sourceDir 源目录
     * @return 编译结果
     * @throws IOException IO 异常
     */
    public CompileResult compile(Path sourceDir) throws IOException {
        log.info("Compile started sourceDir: {}", sourceDir);
        List<RawSource> rawSources = ingestNode.ingest(sourceDir);
        persistSourceFiles(rawSources);
        Map<String, List<RawSource>> groupedSources = groupNode.group(rawSources);

        List<AnalyzedConcept> analyzedConcepts = new ArrayList<AnalyzedConcept>();
        for (Map.Entry<String, List<RawSource>> entry : groupedSources.entrySet()) {
            List<SourceBatch> sourceBatches = batchSplitNode.split(entry.getKey(), entry.getValue());
            analyzedConcepts.addAll(analyzeNode.analyze(entry.getKey(), sourceBatches));
        }

        List<MergedConcept> mergedConcepts = crossGroupMergeNode.merge(analyzedConcepts);
        for (MergedConcept mergedConcept : mergedConcepts) {
            articleJdbcRepository.upsert(toArticleRecord(mergedConcept));
            articleChunkJdbcRepository.replaceChunks(mergedConcept.getConceptId(), mergedConcept.getSnippets());
        }

        CompileResult compileResult = new CompileResult(mergedConcepts.size());
        log.info("Compile completed sourceDir: {}, persistedCount: {}", sourceDir, compileResult.getPersistedCount());
        return compileResult;
    }

    /**
     * 把合并概念转换为文章记录。
     *
     * @param mergedConcept 合并概念
     * @return 文章记录
     */
    private ArticleRecord toArticleRecord(MergedConcept mergedConcept) {
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("# ").append(mergedConcept.getTitle()).append("\n\n");
        appendSummary(contentBuilder, mergedConcept.getDescription());
        appendSections(contentBuilder, mergedConcept.getSections());
        contentBuilder.append("## Sources").append("\n");
        for (String sourcePath : mergedConcept.getSourcePaths()) {
            contentBuilder.append("- ").append(sourcePath).append("\n");
        }
        contentBuilder.append("\n").append("## Snippets").append("\n");
        for (String snippet : mergedConcept.getSnippets()) {
            contentBuilder.append(snippet).append("\n");
        }

        return new ArticleRecord(
                mergedConcept.getConceptId(),
                mergedConcept.getTitle(),
                contentBuilder.toString().trim(),
                "ACTIVE",
                OffsetDateTime.now(),
                mergedConcept.getSourcePaths(),
                buildMetadataJson(mergedConcept)
        );
    }

    /**
     * 追加文章摘要段落。
     *
     * @param contentBuilder 内容构建器
     * @param description 概念描述
     */
    private void appendSummary(StringBuilder contentBuilder, String description) {
        if (description == null || description.isBlank()) {
            return;
        }
        contentBuilder.append("## Summary").append("\n");
        contentBuilder.append(description.trim()).append("\n\n");
    }

    /**
     * 追加结构化章节。
     *
     * @param contentBuilder 内容构建器
     * @param sections 章节列表
     */
    private void appendSections(StringBuilder contentBuilder, List<ConceptSection> sections) {
        for (ConceptSection section : sections) {
            contentBuilder.append("## ").append(section.getHeading().trim()).append("\n");
            for (String contentLine : section.getContentLines()) {
                contentBuilder.append("- ").append(contentLine.trim()).append("\n");
            }
            if (!section.getSourceRefs().isEmpty()) {
                contentBuilder.append("> Sources: ").append(String.join(", ", section.getSourceRefs())).append("\n");
            }
            contentBuilder.append("\n");
        }
    }

    /**
     * 构建最小文章元数据 JSON。
     *
     * @param mergedConcept 合并概念
     * @return 元数据 JSON
     */
    private String buildMetadataJson(MergedConcept mergedConcept) {
        java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("description", mergedConcept.getDescription());
        metadata.put("structured", !mergedConcept.getSections().isEmpty());
        metadata.put("sourceCount", mergedConcept.getSourcePaths().size());
        metadata.put("snippetCount", mergedConcept.getSnippets().size());
        metadata.put("sectionCount", mergedConcept.getSections().size());
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("构建文章 metadata 失败", ex);
        }
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
                    rawSource.getContent(),
                    rawSource.getFormat(),
                    rawSource.getFileSize()
            ));
        }
    }
}
