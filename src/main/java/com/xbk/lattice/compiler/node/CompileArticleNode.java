package com.xbk.lattice.compiler.node;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.article.service.ArticleMarkdownSupport;
import com.xbk.lattice.compiler.domain.ConceptSection;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.compiler.prompt.LatticePrompts;
import com.xbk.lattice.compiler.prompt.SchemaAwarePrompts;
import com.xbk.lattice.compiler.service.ArticleReviewerGateway;
import com.xbk.lattice.compiler.service.DocumentSectionSelector;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.compiler.service.ReviewFixService;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.query.domain.ReviewResult;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文章编译节点
 *
 * 职责：把合并概念编译为 Markdown/frontmatter 文章记录
 *
 * @author xiexu
 */
public class CompileArticleNode {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern REFERENTIAL_PATTERN = Pattern.compile("[A-Za-z0-9_-]+=[A-Za-z0-9._-]+|\\b\\d{3,5}\\b");

    private static final String COMPILE_SCENE = "compile";

    private static final String WRITER_ROLE = "writer";

    private final LlmGateway llmGateway;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    private final DocumentSectionSelector documentSectionSelector;

    private final ArticleReviewerGateway articleReviewerGateway;

    private final ReviewFixService reviewFixService;

    private final SchemaAwarePrompts schemaAwarePrompts;

    /**
     * 创建文章编译节点。
     *
     * @param llmGateway LLM 网关
     * @param sourceFileJdbcRepository 源文件仓储
     * @param documentSectionSelector 文档章节选择器
     */
    public CompileArticleNode(
            LlmGateway llmGateway,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            DocumentSectionSelector documentSectionSelector,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService
    ) {
        this(
                llmGateway,
                sourceFileJdbcRepository,
                documentSectionSelector,
                articleReviewerGateway,
                reviewFixService,
                null
        );
    }

    /**
     * 创建文章编译节点。
     *
     * @param llmGateway LLM 网关
     * @param sourceFileJdbcRepository 源文件仓储
     * @param documentSectionSelector 文档章节选择器
     * @param articleReviewerGateway 文章审查网关
     * @param reviewFixService 审查修复服务
     * @param schemaAwarePrompts SCHEMA 感知 Prompt 服务
     */
    public CompileArticleNode(
            LlmGateway llmGateway,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            DocumentSectionSelector documentSectionSelector,
            ArticleReviewerGateway articleReviewerGateway,
            ReviewFixService reviewFixService,
            SchemaAwarePrompts schemaAwarePrompts
    ) {
        this.llmGateway = llmGateway;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
        this.documentSectionSelector = documentSectionSelector;
        this.articleReviewerGateway = articleReviewerGateway;
        this.reviewFixService = reviewFixService;
        this.schemaAwarePrompts = schemaAwarePrompts;
    }

    /**
     * 编译文章记录。
     *
     * @param mergedConcept 合并概念
     * @return 文章记录
     */
    public ArticleRecord compile(MergedConcept mergedConcept) {
        return compile(mergedConcept, null);
    }

    /**
     * 编译文章草稿。
     *
     * @param mergedConcept 合并概念
     * @param sourceDir 输入目录
     * @return 草稿文章记录
     */
    public ArticleRecord compileDraft(MergedConcept mergedConcept, Path sourceDir) {
        return compileDraft(mergedConcept, sourceDir, null, null, null, COMPILE_SCENE);
    }

    /**
     * 编译文章草稿。
     *
     * @param mergedConcept 合并概念
     * @param sourceDir 输入目录
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 草稿文章记录
     */
    public ArticleRecord compileDraft(MergedConcept mergedConcept, Path sourceDir, String scopeId, String scene) {
        return compileDraft(mergedConcept, sourceDir, null, null, scopeId, scene);
    }

    /**
     * 编译文章草稿。
     *
     * @param mergedConcept 合并概念
     * @param sourceDir 输入目录
     * @param sourceId 资料源主键
     * @param sourceCode 资料源编码
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 草稿文章记录
     */
    public ArticleRecord compileDraft(
            MergedConcept mergedConcept,
            Path sourceDir,
            Long sourceId,
            String sourceCode,
            String scopeId,
            String scene
    ) {
        String summary = buildSummary(mergedConcept);
        List<String> referentialKeywords = extractReferentialKeywords(mergedConcept);
        String markdownContent = tryCompileWithLlm(mergedConcept, summary, sourceDir, sourceId, scopeId, scene);
        if (markdownContent == null || markdownContent.isBlank()) {
            markdownContent = buildFallbackMarkdown(mergedConcept, summary, referentialKeywords);
        }
        String articleKey = buildArticleKey(sourceCode, mergedConcept.getConceptId());
        return new ArticleRecord(
                sourceId,
                articleKey,
                mergedConcept.getConceptId(),
                mergedConcept.getTitle(),
                markdownContent,
                "ACTIVE",
                OffsetDateTime.now(),
                mergedConcept.getSourcePaths(),
                buildMetadataJson(mergedConcept),
                summary,
                referentialKeywords,
                List.of(),
                List.of(),
                "medium",
                "pending"
        );
    }

    /**
     * 编译文章记录。
     *
     * @param mergedConcept 合并概念
     * @param sourceDir 输入目录
     * @return 文章记录
     * @deprecated 图流程已通过独立审查节点处理，此方法仅保留用于过渡期测试兼容
     */
    @Deprecated
    public ArticleRecord compile(MergedConcept mergedConcept, Path sourceDir) {
        ArticleRecord draftArticle = compileDraft(mergedConcept, sourceDir);
        String markdownContent = draftArticle.getContent();
        String reviewStatus = "pending";
        String sourceContents = buildSourceContents(draftArticle.getSourcePaths(), draftArticle.getSourceId());
        if (articleReviewerGateway != null && articleReviewerGateway.isEnabled()) {
            ReviewResult reviewResult = articleReviewerGateway.review(markdownContent, sourceContents);
            if (reviewResult.isPass()) {
                reviewStatus = "passed";
            }
            else {
                String fixedContent = reviewFixService == null
                        ? null
                        : reviewFixService.applyFix(markdownContent, reviewResult.getIssues(), sourceContents);
                if (fixedContent != null && !fixedContent.isBlank()) {
                    markdownContent = fixedContent;
                    reviewStatus = "passed";
                }
                else {
                    reviewStatus = "needs_human_review";
                }
            }
        }
        return replaceReviewStatus(draftArticle, reviewStatus, markdownContent);
    }

    /**
     * 基于来源路径构建源文件正文。
     *
     * @param sourcePaths 来源路径列表
     * @return 源文件正文
     */
    public String buildSourceContents(List<String> sourcePaths) {
        return buildSourceContents(sourcePaths, null);
    }

    /**
     * 基于来源路径构建源文件正文。
     *
     * @param sourcePaths 来源路径列表
     * @param sourceId 资料源主键
     * @return 源文件正文
     */
    public String buildSourceContents(List<String> sourcePaths, Long sourceId) {
        StringBuilder builder = new StringBuilder();
        for (String sourcePath : sourcePaths) {
            Optional<SourceFileRecord> sourceFileRecord = sourceId == null
                    ? sourceFileJdbcRepository.findByPath(sourcePath)
                    : sourceFileJdbcRepository.findBySourceIdAndRelativePath(sourceId, sourcePath);
            if (sourceFileRecord.isEmpty()) {
                sourceFileRecord = sourceFileJdbcRepository.findByPath(sourcePath);
            }
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
        return builder.toString();
    }

    /**
     * 基于新状态替换文章 frontmatter 并返回更新后的文章记录。
     *
     * @param articleRecord 原始文章
     * @param reviewStatus 审查状态
     * @param markdownContent Markdown 内容
     * @return 更新后的文章记录
     */
    public ArticleRecord replaceReviewStatus(
            ArticleRecord articleRecord,
            String reviewStatus,
            String markdownContent
    ) {
        String normalizedContent = replaceReviewStatus(markdownContent, reviewStatus);
        normalizedContent = ArticleMarkdownSupport.normalizeSourcePaths(
                normalizedContent,
                articleRecord.getSourcePaths()
        );
        ArticleRecord updatedRecord = new ArticleRecord(
                articleRecord.getSourceId(),
                articleRecord.getArticleKey(),
                articleRecord.getConceptId(),
                articleRecord.getTitle(),
                normalizedContent,
                articleRecord.getLifecycle(),
                OffsetDateTime.now(),
                articleRecord.getSourcePaths(),
                articleRecord.getMetadataJson(),
                articleRecord.getSummary(),
                articleRecord.getReferentialKeywords(),
                articleRecord.getDependsOn(),
                articleRecord.getRelated(),
                articleRecord.getConfidence(),
                reviewStatus
        );
        return ArticleMarkdownSupport.synchronizeArticleRecord(updatedRecord, normalizedContent, reviewStatus);
    }

    /**
     * 尝试使用 LLM 编译文章。
     *
     * @param mergedConcept 合并概念
     * @param summary 摘要
     * @return Markdown 文章；失败时返回 null
     */
    private String tryCompileWithLlm(
            MergedConcept mergedConcept,
            String summary,
            Path sourceDir,
            Long sourceId,
            String scopeId,
            String scene
    ) {
        if (llmGateway == null) {
            return null;
        }
        try {
            String systemPrompt = resolveCompileSystemPrompt(mergedConcept, sourceDir);
            String userPrompt = buildCompilePrompt(mergedConcept, summary, sourceId);
            return scopeId == null || scopeId.isBlank()
                    ? llmGateway.generateText(COMPILE_SCENE, WRITER_ROLE, "compile-article", systemPrompt, userPrompt)
                    : llmGateway.generateTextWithScope(
                            scopeId,
                            scene,
                            WRITER_ROLE,
                            "compile-article",
                            systemPrompt,
                            userPrompt
                    );
        }
        catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 根据概念来源类型选择更合适的编译系统提示词。
     *
     * @param mergedConcept 合并概念
     * @param sourceDir 源目录
     * @return 系统提示词
     */
    private String resolveCompileSystemPrompt(MergedConcept mergedConcept, Path sourceDir) {
        if (isImageDominantConcept(mergedConcept)) {
            return LatticePrompts.SYSTEM_COMPILE_IMAGE_ARTICLE;
        }
        return schemaAwarePrompts == null
                ? LatticePrompts.SYSTEM_COMPILE_ARTICLE
                : schemaAwarePrompts.getCompileArticlePrompt(sourceDir);
    }

    /**
     * 构建文章编译提示词。
     *
     * @param mergedConcept 合并概念
     * @param summary 摘要
     * @return 提示词
     */
    private String buildCompilePrompt(MergedConcept mergedConcept, String summary, Long sourceId) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Compile a knowledge article about: \"").append(mergedConcept.getTitle()).append("\"").append("\n\n");
        promptBuilder.append("Description: ").append(summary).append("\n\n");
        promptBuilder.append("Concept ID: ").append(mergedConcept.getConceptId()).append("\n\n");
        promptBuilder.append("Relevant source content:").append("\n");
        for (String sourcePath : mergedConcept.getSourcePaths()) {
            Optional<SourceFileRecord> sourceFileRecord = sourceId == null
                    ? sourceFileJdbcRepository.findByPath(sourcePath)
                    : sourceFileJdbcRepository.findBySourceIdAndRelativePath(sourceId, sourcePath);
            if (sourceFileRecord.isEmpty()) {
                sourceFileRecord = sourceFileJdbcRepository.findByPath(sourcePath);
            }
            if (sourceFileRecord.isEmpty()) {
                continue;
            }
            String selectedContent = documentSectionSelector.select(
                    sourceFileRecord.orElseThrow().getContentText(),
                    buildConceptTerms(mergedConcept),
                    4000
            );
            promptBuilder.append("=== Source: ").append(sourcePath).append(" ===").append("\n");
            promptBuilder.append(selectedContent).append("\n");
            promptBuilder.append("=== End ===").append("\n\n");
        }
        return promptBuilder.toString().trim();
    }

    /**
     * 构建源文件正文。
     *
     * @param mergedConcept 合并概念
     * @return 源文件正文
     */
    private String buildSourceContents(MergedConcept mergedConcept) {
        return buildSourceContents(mergedConcept.getSourcePaths());
    }

    /**
     * 判断概念是否主要来自图片 / OCR 资产。
     *
     * @param mergedConcept 合并概念
     * @return 图片主导返回 true
     */
    private boolean isImageDominantConcept(MergedConcept mergedConcept) {
        if (mergedConcept == null
                || mergedConcept.getSourcePaths() == null
                || mergedConcept.getSourcePaths().isEmpty()) {
            return false;
        }
        int imageSourceCount = 0;
        for (String sourcePath : mergedConcept.getSourcePaths()) {
            if (isImageLikePath(sourcePath)) {
                imageSourceCount++;
            }
        }
        return imageSourceCount == mergedConcept.getSourcePaths().size();
    }

    /**
     * 判断来源路径是否为图片 / 视觉资产。
     *
     * @param sourcePath 来源路径
     * @return 图片类资源返回 true
     */
    private boolean isImageLikePath(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return false;
        }
        String normalizedPath = sourcePath.trim().toLowerCase(Locale.ROOT);
        return normalizedPath.endsWith(".png")
                || normalizedPath.endsWith(".jpg")
                || normalizedPath.endsWith(".jpeg")
                || normalizedPath.endsWith(".gif")
                || normalizedPath.endsWith(".bmp")
                || normalizedPath.endsWith(".webp")
                || normalizedPath.endsWith(".svg")
                || normalizedPath.endsWith(".drawio");
    }

    private String buildArticleKey(String sourceCode, String conceptId) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return conceptId;
        }
        return sourceCode + "--" + conceptId;
    }

    /**
     * 构建确定性回退 Markdown。
     *
     * @param mergedConcept 合并概念
     * @param summary 摘要
     * @param referentialKeywords 明确性关键词
     * @return Markdown 内容
     */
    private String buildFallbackMarkdown(MergedConcept mergedConcept, String summary, List<String> referentialKeywords) {
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("---").append("\n");
        contentBuilder.append("title: ").append("\"").append(mergedConcept.getTitle()).append("\"").append("\n");
        contentBuilder.append("summary: ").append("\"").append(escapeYaml(summary)).append("\"").append("\n");
        contentBuilder.append("referential_keywords: ").append(formatYamlList(referentialKeywords)).append("\n");
        contentBuilder.append("sources: ").append(formatYamlList(mergedConcept.getSourcePaths())).append("\n");
        contentBuilder.append("depends_on: []").append("\n");
        contentBuilder.append("related: []").append("\n");
        contentBuilder.append("confidence: medium").append("\n");
        contentBuilder.append("compiled_at: ").append("\"").append(OffsetDateTime.now()).append("\"").append("\n");
        contentBuilder.append("review_status: pending").append("\n");
        contentBuilder.append("---").append("\n\n");
        contentBuilder.append("# ").append(mergedConcept.getTitle()).append("\n\n");
        if (!summary.isBlank()) {
            contentBuilder.append(summary).append("\n\n");
        }
        appendSections(contentBuilder, mergedConcept.getSections());
        if (!mergedConcept.getSourcePaths().isEmpty()) {
            contentBuilder.append("## Sources").append("\n");
            for (String sourcePath : mergedConcept.getSourcePaths()) {
                contentBuilder.append("- ").append(sourcePath).append("\n");
            }
        }
        return contentBuilder.toString().trim();
    }

    /**
     * 替换 frontmatter 中的审查状态。
     *
     * @param markdownContent Markdown 内容
     * @param reviewStatus 审查状态
     * @return 更新后的 Markdown
     */
    public String replaceReviewStatus(String markdownContent, String reviewStatus) {
        return markdownContent.replaceFirst("review_status:\\s*\\w+", "review_status: " + reviewStatus);
    }

    /**
     * 追加章节内容。
     *
     * @param contentBuilder 内容构建器
     * @param sections 章节列表
     */
    private void appendSections(StringBuilder contentBuilder, List<ConceptSection> sections) {
        for (ConceptSection section : sections) {
            contentBuilder.append("## ").append(section.getHeading()).append("\n");
            for (String contentLine : section.getContentLines()) {
                contentBuilder.append("- ").append(contentLine).append("\n");
            }
            if (!section.getSourceRefs().isEmpty()) {
                contentBuilder.append("> Sources: ").append(String.join(", ", section.getSourceRefs())).append("\n");
            }
            contentBuilder.append("\n");
        }
    }

    /**
     * 构建摘要。
     *
     * @param mergedConcept 合并概念
     * @return 摘要
     */
    private String buildSummary(MergedConcept mergedConcept) {
        if (mergedConcept.getDescription() != null && !mergedConcept.getDescription().isBlank()) {
            return mergedConcept.getDescription().trim();
        }
        return mergedConcept.getTitle();
    }

    /**
     * 抽取明确性关键词。
     *
     * @param mergedConcept 合并概念
     * @return 明确性关键词
     */
    private List<String> extractReferentialKeywords(MergedConcept mergedConcept) {
        LinkedHashSet<String> keywords = new LinkedHashSet<String>();
        for (ConceptSection section : mergedConcept.getSections()) {
            for (String contentLine : section.getContentLines()) {
                Matcher matcher = REFERENTIAL_PATTERN.matcher(contentLine);
                while (matcher.find()) {
                    keywords.add(matcher.group());
                }
            }
        }
        return new ArrayList<String>(keywords);
    }

    /**
     * 构建概念关键词。
     *
     * @param mergedConcept 合并概念
     * @return 概念关键词
     */
    private List<String> buildConceptTerms(MergedConcept mergedConcept) {
        List<String> conceptTerms = new ArrayList<String>();
        conceptTerms.add(mergedConcept.getTitle());
        conceptTerms.add(mergedConcept.getConceptId().replace('-', ' '));
        if (mergedConcept.getDescription() != null && !mergedConcept.getDescription().isBlank()) {
            conceptTerms.add(mergedConcept.getDescription());
        }
        return conceptTerms;
    }

    /**
     * 构建 metadata JSON。
     *
     * @param mergedConcept 合并概念
     * @return metadata JSON
     */
    private String buildMetadataJson(MergedConcept mergedConcept) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
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
     * 格式化 YAML 列表。
     *
     * @param items 条目列表
     * @return YAML 列表文本
     */
    private String formatYamlList(List<String> items) {
        List<String> escapedItems = new ArrayList<String>();
        for (String item : items) {
            escapedItems.add("\"" + escapeYaml(item) + "\"");
        }
        return "[" + String.join(", ", escapedItems) + "]";
    }

    /**
     * 转义 YAML 双引号内容。
     *
     * @param value 原始值
     * @return 转义后内容
     */
    private String escapeYaml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
