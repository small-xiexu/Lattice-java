package com.xbk.lattice.compiler.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 合并后的概念。
 *
 * <p>表示跨批次合并后的概念对象，语义为"合并后最终版"——与 {@link AnalyzedConcept} 结构相同，
 * 但代表经过跨批次去重、合并后的稳定概念，作为后续编译步骤的输入。
 *
 * @author xiexu
 */
@Getter
public class MergedConcept {

    /** 概念标识（唯一）。 */
    private final String conceptId;

    /** 概念标题。 */
    private final String title;

    /** 概念描述。 */
    private final String description;

    /** 来源文件路径列表。合并后可能包含多个批次的来源。 */
    private final List<String> sourcePaths;

    /** 概念片段摘要列表。合并后去重。 */
    private final List<String> snippets;

    /** 概念章节列表。合并后去重（基于 equals/hashCode）。 */
    private final List<ConceptSection> sections;

    /** LLM Analyze 概念生成模式。 */
    private final String analysisMode;

    /** LLM Analyze 失败原因。 */
    private final String failureReason;

    /** 标题来源。 */
    private final String titleSource;

    @JsonCreator
    public MergedConcept(
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

    public MergedConcept(String conceptId, String title, List<String> sourcePaths, List<String> snippets) {
        this(conceptId, title, "", sourcePaths, snippets, Collections.<ConceptSection>emptyList(), null, null, null);
    }

    public MergedConcept(
            String conceptId,
            String title,
            String description,
            List<String> sourcePaths,
            List<String> snippets
    ) {
        this(conceptId, title, description, sourcePaths, snippets, Collections.<ConceptSection>emptyList(), null, null, null);
    }

    public MergedConcept(
            String conceptId,
            String title,
            String description,
            List<String> sourcePaths,
            List<String> snippets,
            List<ConceptSection> sections
    ) {
        this(conceptId, title, description, sourcePaths, snippets, sections, null, null, null);
    }
}
