package com.xbk.lattice.compiler.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 分析后的概念。
 *
 * <p>表示批次分析后的最小概念对象，生命周期：LLM Analyze 输出→跨批次合并前。
 * 通过 {@code withAnalysisMetadata()} 可创建带元数据的不可变副本。
 *
 * @author xiexu
 */
@Getter
public class AnalyzedConcept {

    /** 概念标识（唯一）。 */
    private final String conceptId;

    /** 概念标题。 */
    private final String title;

    /** 概念描述。 */
    private final String description;

    /** 来源文件路径列表。关联该概念的源文件。 */
    private final List<String> sourcePaths;

    /** 概念片段摘要列表。 */
    private final List<String> snippets;

    /** 概念章节列表。 */
    private final List<ConceptSection> sections;

    /** LLM Analyze 概念生成模式（如 full/lite）。为 null 表示未分析。 */
    private final String analysisMode;

    /** LLM Analyze 失败原因。为 null 表示分析成功。 */
    private final String failureReason;

    /** 标题来源（如 LLM_GENERATED / SOURCE_EXTRACTED）。 */
    private final String titleSource;

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

    public AnalyzedConcept(String conceptId, String title, List<String> sourcePaths, List<String> snippets) {
        this(conceptId, title, "", sourcePaths, snippets, Collections.<ConceptSection>emptyList(), null, null, null);
    }

    public AnalyzedConcept(
            String conceptId,
            String title,
            String description,
            List<String> sourcePaths,
            List<String> snippets
    ) {
        this(conceptId, title, description, sourcePaths, snippets, Collections.<ConceptSection>emptyList(), null, null, null);
    }

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

    public AnalyzedConcept withAnalysisMetadata(String analysisMode, String failureReason) {
        return withAnalysisMetadata(analysisMode, failureReason, titleSource);
    }

    public AnalyzedConcept withAnalysisMetadata(String analysisMode, String failureReason, String titleSource) {
        return new AnalyzedConcept(
                conceptId, title, description, sourcePaths, snippets, sections,
                analysisMode, failureReason, titleSource
        );
    }
}
