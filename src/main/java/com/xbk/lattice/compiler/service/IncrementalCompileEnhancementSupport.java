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
 * 增量编译文章增强支持。
 *
 * 职责：基于增量概念生成增强文章正文、摘要与元数据。
 *
 * @author xiexu
 */
@Slf4j
abstract class IncrementalCompileEnhancementSupport extends IncrementalCompilePlanningSupport {

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
    protected IncrementalCompileEnhancementSupport(
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
     * 增强已有文章。
     *
     * @param existingArticle 已有文章
     * @param mergedConcepts 增量概念
     * @return 更新后的文章
     */
    protected ArticleRecord enhanceExistingArticle(ArticleRecord existingArticle, List<MergedConcept> mergedConcepts) {
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
    protected String tryEnhanceWithLlm(ArticleRecord existingArticle, List<MergedConcept> mergedConcepts) {
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
    protected String buildIncrementalEnhancePrompt(ArticleRecord existingArticle, List<MergedConcept> mergedConcepts) {
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
    protected String buildFallbackEnhancedMarkdown(
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
    protected String buildEnhancedSummary(ArticleRecord existingArticle, List<MergedConcept> mergedConcepts) {
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
}
