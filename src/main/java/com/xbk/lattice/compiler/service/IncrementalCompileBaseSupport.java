package com.xbk.lattice.compiler.service;

import com.xbk.lattice.shared.json.JsonMappers;

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
 * 增量编译基础支持。
 *
 * 职责：承载节点依赖、文章元数据合并、Markdown 解析和合成产物刷新工具。
 *
 * @author xiexu
 */
@Slf4j
abstract class IncrementalCompileBaseSupport {

    protected static final ObjectMapper OBJECT_MAPPER = JsonMappers.defaultMapper();

    protected static final String COMPILE_SCENE = "compile";

    protected static final String WRITER_ROLE = "writer";

    protected static final Pattern FRONTMATTER_PATTERN = Pattern.compile("\\A---\\R(.*?)\\R---\\R?(.*)\\z", Pattern.DOTALL);

    protected static final Pattern REFERENTIAL_PATTERN = Pattern.compile("[A-Za-z0-9_-]+=[A-Za-z0-9._-]+|\\b\\d{3,5}\\b");

    protected final IngestNode ingestNode;

    protected final GroupNode groupNode;

    protected final BatchSplitNode batchSplitNode;

    protected final AnalyzeNode analyzeNode;

    protected final CrossGroupMergeNode crossGroupMergeNode;

    protected final CompileArticleNode compileArticleNode;

    protected final LlmGateway llmGateway;

    protected final SynthesisArtifactsService synthesisArtifactsService;

    protected final ArticleJdbcRepository articleJdbcRepository;

    protected final ArticleChunkJdbcRepository articleChunkJdbcRepository;

    protected final SourceFileJdbcRepository sourceFileJdbcRepository;

    protected final SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository;

    protected final ArticleVectorIndexService articleVectorIndexService;

    protected final ArticleChunkVectorIndexService articleChunkVectorIndexService;

    protected RepoSnapshotService repoSnapshotService;

    protected FactCardGenerationService factCardGenerationService;

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
    protected IncrementalCompileBaseSupport(
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
        this.ingestNode = new IngestNode(compilerProperties, documentParseApplicationService);
        this.groupNode = new GroupNode(compilerProperties);
        this.batchSplitNode = new BatchSplitNode(
                compilerProperties,
                new FileRankingService(compilerProperties)
        );
        this.analyzeNode = new AnalyzeNode(llmGateway, null, compilerProperties);
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
     * 构建文章元数据 JSON。
     *
     * @param summary 摘要
     * @param sourcePaths 来源路径
     * @param mergedConcepts 增量概念
     * @return 元数据 JSON
     */
    protected String buildIncrementalMetadataJson(
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
    protected List<String> mergeSourcePaths(List<String> existingSourcePaths, List<MergedConcept> mergedConcepts) {
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
    protected List<String> mergeReferentialKeywords(List<String> existingKeywords, List<MergedConcept> mergedConcepts) {
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
    protected String buildSourceContents(List<MergedConcept> mergedConcepts) {
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
    protected void refreshSynthesisArtifacts(String jobId) {
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
    protected List<String> normalizeTextList(List<String> rawValues) {
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
    protected String unwrapJsonCodeFence(String content) {
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
    protected FrontmatterValues parseFrontmatter(String markdownContent) {
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
    protected String extractBody(String markdownContent) {
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
    protected List<String> parseYamlList(String rawValue) {
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
    protected String stripQuotes(String value) {
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
    protected String formatYamlList(List<String> values) {
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
    protected String escapeYaml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    /**
     * 归一化概念标识。
     *
     * @param conceptId 原始概念标识
     * @return 归一化概念标识
     */
    protected String normalizeConceptId(String conceptId) {
        return conceptId == null ? "" : conceptId.trim().toLowerCase(Locale.ROOT);
    }
    /**
     * 增量计划。
     *
     * 职责：承载增强与新建文章两类动作
     *
     * @author xiexu
     */
    protected static class IncrementalPlan {

        protected final List<EnhancementPlan> enhancements;

        protected final List<NewArticlePlan> newArticles;

        protected IncrementalPlan(List<EnhancementPlan> enhancements, List<NewArticlePlan> newArticles) {
            this.enhancements = enhancements;
            this.newArticles = newArticles;
        }

        protected List<EnhancementPlan> getEnhancements() {
            return enhancements;
        }

        protected List<NewArticlePlan> getNewArticles() {
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
    protected static class EnhancementPlan {

        protected final String targetArticleId;

        protected final String newInfoSummary;

        protected final List<String> sourceRefs;

        protected EnhancementPlan(String targetArticleId, String newInfoSummary, List<String> sourceRefs) {
            this.targetArticleId = targetArticleId;
            this.newInfoSummary = newInfoSummary;
            this.sourceRefs = sourceRefs;
        }

        protected String getTargetArticleId() {
            return targetArticleId;
        }

        protected String getNewInfoSummary() {
            return newInfoSummary;
        }

        protected List<String> getSourceRefs() {
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
    protected static class NewArticlePlan {

        protected final String id;

        protected final String title;

        protected final String description;

        protected final List<String> sourceRefs;

        protected NewArticlePlan(String id, String title, String description, List<String> sourceRefs) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.sourceRefs = sourceRefs;
        }

        protected String getId() {
            return id;
        }

        protected String getTitle() {
            return title;
        }

        protected String getDescription() {
            return description;
        }

        protected List<String> getSourceRefs() {
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
    protected static class FrontmatterValues {

        protected final String title;

        protected final String summary;

        protected final List<String> referentialKeywords;

        protected final List<String> sources;

        protected final List<String> dependsOn;

        protected final List<String> related;

        protected final String confidence;

        protected final String reviewStatus;

        protected FrontmatterValues(
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

        protected static FrontmatterValues empty() {
            return new FrontmatterValues("", "", List.of(), List.of(), List.of(), List.of(), "", "");
        }

        protected String getTitle() {
            return title;
        }

        protected String getSummary() {
            return summary;
        }

        protected List<String> getReferentialKeywords() {
            return referentialKeywords;
        }

        protected List<String> getSources() {
            return sources;
        }

        protected List<String> getDependsOn() {
            return dependsOn;
        }

        protected List<String> getRelated() {
            return related;
        }

        protected String getConfidence() {
            return confidence;
        }

        protected String getReviewStatus() {
            return reviewStatus;
        }
    }
}
