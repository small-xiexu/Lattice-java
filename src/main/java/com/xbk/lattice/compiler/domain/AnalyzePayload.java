package com.xbk.lattice.compiler.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyze 结构化载荷
 *
 * 职责：承载 `AnalyzeNode` 的结构化概念输出
 *
 * @author xiexu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalyzePayload {

    private final List<AnalyzeConceptPayload> concepts;

    /**
     * 创建 Analyze 结构化载荷。
     *
     * @param concepts 概念载荷列表
     */
    @JsonCreator
    public AnalyzePayload(@JsonProperty("concepts") List<AnalyzeConceptPayload> concepts) {
        this.concepts = concepts == null ? List.of() : new ArrayList<AnalyzeConceptPayload>(concepts);
    }

    /**
     * 返回概念载荷列表。
     *
     * @return 概念载荷列表
     */
    public List<AnalyzeConceptPayload> getConcepts() {
        return concepts;
    }

    /**
     * Analyze 概念载荷
     *
     * 职责：承载单个概念的最小结构化字段
     *
     * @author xiexu
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnalyzeConceptPayload {

        private final String id;

        private final String title;

        private final String description;

        private final List<String> snippets;

        private final List<AnalyzeSectionPayload> sections;

        private final List<AnalyzeSourcePayload> sources;

        /**
         * 创建 Analyze 概念载荷。
         *
         * @param id 概念标识
         * @param title 概念标题
         * @param description 概念描述
         * @param snippets 概念片段
         * @param sections 概念章节
         * @param sources 概念来源
         */
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

        /**
         * 返回概念标识。
         *
         * @return 概念标识
         */
        public String getId() {
            return id;
        }

        /**
         * 返回概念标题。
         *
         * @return 概念标题
         */
        public String getTitle() {
            return title;
        }

        /**
         * 返回概念描述。
         *
         * @return 概念描述
         */
        public String getDescription() {
            return description;
        }

        /**
         * 返回概念片段。
         *
         * @return 概念片段
         */
        public List<String> getSnippets() {
            return snippets;
        }

        /**
         * 返回概念章节。
         *
         * @return 概念章节
         */
        public List<AnalyzeSectionPayload> getSections() {
            return sections;
        }

        /**
         * 返回概念来源。
         *
         * @return 概念来源
         */
        public List<AnalyzeSourcePayload> getSources() {
            return sources;
        }
    }

    /**
     * Analyze 章节载荷
     *
     * 职责：承载单个概念章节的结构化字段
     *
     * @author xiexu
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnalyzeSectionPayload {

        private final String heading;

        private final List<String> content;

        private final List<String> sources;

        /**
         * 创建 Analyze 章节载荷。
         *
         * @param heading 章节标题
         * @param content 章节内容
         * @param sources 章节来源
         */
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

        /**
         * 返回章节标题。
         *
         * @return 章节标题
         */
        public String getHeading() {
            return heading;
        }

        /**
         * 返回章节内容。
         *
         * @return 章节内容
         */
        public List<String> getContent() {
            return content;
        }

        /**
         * 返回章节来源。
         *
         * @return 章节来源
         */
        public List<String> getSources() {
            return sources;
        }
    }

    /**
     * Analyze 来源载荷
     *
     * 职责：承载概念来源的最小定位信息
     *
     * @author xiexu
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnalyzeSourcePayload {

        private final String path;

        private final String location;

        /**
         * 创建 Analyze 来源载荷。
         *
         * @param path 来源路径
         * @param location 来源位置
         */
        @JsonCreator
        public AnalyzeSourcePayload(
                @JsonProperty("path") String path,
                @JsonProperty("location") String location
        ) {
            this.path = path == null ? "" : path;
            this.location = location == null ? "" : location;
        }

        /**
         * 返回来源路径。
         *
         * @return 来源路径
         */
        public String getPath() {
            return path;
        }

        /**
         * 返回来源位置。
         *
         * @return 来源位置
         */
        public String getLocation() {
            return location;
        }
    }
}
