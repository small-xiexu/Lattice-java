package com.xbk.lattice.compiler.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * 分析后的概念
 *
 * 职责：表示批次分析后的最小概念对象
 *
 * @author xiexu
 */
public class AnalyzedConcept {

    private final String conceptId;

    private final String title;

    private final String description;

    private final List<String> sourcePaths;

    private final List<String> snippets;

    private final List<ConceptSection> sections;

    private final String analysisMode;

    private final String failureReason;

    private final String titleSource;

    /**
     * 创建分析后的概念。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param description 描述
     * @param sourcePaths 来源路径
     * @param snippets 片段摘要
     * @param sections 章节列表
     * @param analysisMode Analyze 概念生成模式
     * @param failureReason Analyze 失败原因
     * @param titleSource 标题来源
     */
    @JsonCreator
    public AnalyzedConcept(
            @JsonProperty("conceptId") String conceptId,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("sourcePaths") List<String> sourcePaths,
            @JsonProperty("snippets") List<String> snippets,
            @JsonProperty("sections") List<ConceptSection> sections,
            @JsonProperty("analysisMode") String analysisMode,
            @JsonProperty("failureReason") String failureReason,
            @JsonProperty("titleSource") String titleSource
    ) {
        this.conceptId = conceptId;
        this.title = title;
        this.description = description;
        this.sourcePaths = sourcePaths;
        this.snippets = snippets;
        this.sections = sections;
        this.analysisMode = analysisMode;
        this.failureReason = failureReason;
        this.titleSource = titleSource;
    }

    /**
     * 创建分析后的概念。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param sourcePaths 来源路径
     * @param snippets 片段摘要
     */
    public AnalyzedConcept(String conceptId, String title, List<String> sourcePaths, List<String> snippets) {
        this(conceptId, title, "", sourcePaths, snippets, Collections.<ConceptSection>emptyList(), null, null, null);
    }

    /**
     * 创建分析后的概念。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param description 描述
     * @param sourcePaths 来源路径
     * @param snippets 片段摘要
     */
    public AnalyzedConcept(
            String conceptId,
            String title,
            String description,
            List<String> sourcePaths,
            List<String> snippets
    ) {
        this(conceptId, title, description, sourcePaths, snippets, Collections.<ConceptSection>emptyList(), null, null, null);
    }

    /**
     * 创建分析后的概念。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param description 描述
     * @param sourcePaths 来源路径
     * @param snippets 片段摘要
     * @param sections 章节列表
     */
    public AnalyzedConcept(
            String conceptId,
            String title,
            String description,
            List<String> sourcePaths,
            List<String> snippets,
            List<ConceptSection> sections
    ) {
        this(conceptId, title, description, sourcePaths, snippets, sections, null, null, null);
    }

    /**
     * 创建带 Analyze 元数据的分析结果副本。
     *
     * @param analysisMode Analyze 概念生成模式
     * @param failureReason Analyze 失败原因
     * @return 带元数据的新对象
     */
    public AnalyzedConcept withAnalysisMetadata(String analysisMode, String failureReason) {
        return withAnalysisMetadata(analysisMode, failureReason, titleSource);
    }

    /**
     * 创建带 Analyze 元数据的分析结果副本。
     *
     * @param analysisMode Analyze 概念生成模式
     * @param failureReason Analyze 失败原因
     * @param titleSource 标题来源
     * @return 带元数据的新对象
     */
    public AnalyzedConcept withAnalysisMetadata(String analysisMode, String failureReason, String titleSource) {
        return new AnalyzedConcept(
                conceptId,
                title,
                description,
                sourcePaths,
                snippets,
                sections,
                analysisMode,
                failureReason,
                titleSource
        );
    }

    /**
     * 获取概念标识。
     *
     * @return 概念标识
     */
    public String getConceptId() {
        return conceptId;
    }

    /**
     * 获取标题。
     *
     * @return 标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 获取描述。
     *
     * @return 描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取来源路径。
     *
     * @return 来源路径
     */
    public List<String> getSourcePaths() {
        return sourcePaths;
    }

    /**
     * 获取片段摘要。
     *
     * @return 片段摘要
     */
    public List<String> getSnippets() {
        return snippets;
    }

    /**
     * 获取章节列表。
     *
     * @return 章节列表
     */
    public List<ConceptSection> getSections() {
        return sections;
    }

    /**
     * 获取 Analyze 概念生成模式。
     *
     * @return Analyze 模式
     */
    public String getAnalysisMode() {
        return analysisMode;
    }

    /**
     * 获取 Analyze 失败原因。
     *
     * @return 失败原因
     */
    public String getFailureReason() {
        return failureReason;
    }

    /**
     * 获取标题来源。
     *
     * @return 标题来源
     */
    public String getTitleSource() {
        return titleSource;
    }
}
