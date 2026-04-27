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
    @JsonCreator
    public AnalyzedConcept(
            @JsonProperty("conceptId") String conceptId,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("sourcePaths") List<String> sourcePaths,
            @JsonProperty("snippets") List<String> snippets,
            @JsonProperty("sections") List<ConceptSection> sections
    ) {
        this.conceptId = conceptId;
        this.title = title;
        this.description = description;
        this.sourcePaths = sourcePaths;
        this.snippets = snippets;
        this.sections = sections;
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
        this(conceptId, title, "", sourcePaths, snippets, Collections.<ConceptSection>emptyList());
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
        this(conceptId, title, description, sourcePaths, snippets, Collections.<ConceptSection>emptyList());
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
}
