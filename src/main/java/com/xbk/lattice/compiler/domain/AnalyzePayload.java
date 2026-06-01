package com.xbk.lattice.compiler.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyze 结构化载荷。
 *
 * <p>承载 LLM Analyze 步骤的结构化概念输出，由 AnalyzeNode 产出后反序列化。
 * 所有嵌套列表在构造时做防御性拷贝，运行时不可变。
 *
 * @author xiexu
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalyzePayload {

    /** 概念载荷列表。不可变（构造时防御性拷贝）。 */
    private final List<AnalyzeConceptPayload> concepts;

    @JsonCreator
    public AnalyzePayload(@JsonProperty("concepts") List<AnalyzeConceptPayload> concepts) {
        this.concepts = concepts == null ? List.of() : new ArrayList<AnalyzeConceptPayload>(concepts);
    }

    /**
     * Analyze 概念载荷。
     *
     * <p>承载 LLM 输出的单个概念的最小结构化字段。生命周期：LLM Analyze 输出→概念合并前。
     */
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnalyzeConceptPayload {

        /** 概念标识（LLM 生成）。为空时取 {@code ""}。 */
        private final String id;

        /** 概念标题。为空时取 {@code ""}。 */
        private final String title;

        /** 概念描述。为空时取 {@code ""}。 */
        private final String description;

        /** 概念片段列表。不可变（构造时防御性拷贝）。 */
        private final List<String> snippets;

        /** 概念章节列表。不可变（构造时防御性拷贝）。 */
        private final List<AnalyzeSectionPayload> sections;

        /** 概念来源列表。不可变（构造时防御性拷贝）。 */
        private final List<AnalyzeSourcePayload> sources;

        @JsonCreator
        public AnalyzeConceptPayload(
                @JsonProperty("id") String id,
                @JsonProperty("title") String title,
                @JsonProperty("description") String description,
                @JsonProperty("snippets") List<String> snippets,
                @JsonProperty("sections") List<AnalyzeSectionPayload> sections,
                @JsonProperty("sources") List<AnalyzeSourcePayload> sources
        ) {
            this.id = id == null ? "" : id;
            this.title = title == null ? "" : title;
            this.description = description == null ? "" : description;
            this.snippets = snippets == null ? List.of() : new ArrayList<String>(snippets);
            this.sections = sections == null ? List.of() : new ArrayList<AnalyzeSectionPayload>(sections);
            this.sources = sources == null ? List.of() : new ArrayList<AnalyzeSourcePayload>(sources);
        }
    }

    /**
     * Analyze 章节载荷。
     *
     * <p>承载单个概念章节的结构化字段。生命周期：LLM Analyze 输出→概念章节合并去重。
     */
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnalyzeSectionPayload {

        /** 章节标题。为空时取 {@code ""}。 */
        private final String heading;

        /** 章节内容行列表。不可变（构造时防御性拷贝）。 */
        private final List<String> content;

        /** 章节来源引用列表。不可变（构造时防御性拷贝）。 */
        private final List<String> sources;

        @JsonCreator
        public AnalyzeSectionPayload(
                @JsonProperty("heading") String heading,
                @JsonProperty("content") List<String> content,
                @JsonProperty("sources") List<String> sources
        ) {
            this.heading = heading == null ? "" : heading;
            this.content = content == null ? List.of() : new ArrayList<String>(content);
            this.sources = sources == null ? List.of() : new ArrayList<String>(sources);
        }
    }

    /**
     * Analyze 来源载荷。
     *
     * <p>承载概念来源的最小定位信息（路径+位置）。生命周期：LLM Analyze 输出→关联到具体概念。
     */
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnalyzeSourcePayload {

        /** 来源文件路径。为空时取 {@code ""}。 */
        private final String path;

        /** 来源位置（如行号范围）。为空时取 {@code ""}。 */
        private final String location;

        @JsonCreator
        public AnalyzeSourcePayload(
                @JsonProperty("path") String path,
                @JsonProperty("location") String location
        ) {
            this.path = path == null ? "" : path;
            this.location = location == null ? "" : location;
        }
    }
}
