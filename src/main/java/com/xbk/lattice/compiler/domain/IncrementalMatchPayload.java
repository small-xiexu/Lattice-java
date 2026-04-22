package com.xbk.lattice.compiler.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 增量匹配载荷
 *
 * 职责：承载 `incremental-match` 结构化输出的最小语义
 *
 * @author xiexu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncrementalMatchPayload {

    private final List<EnhancementPayload> enhancements;

    private final List<NewArticlePayload> newArticles;

    /**
     * 创建增量匹配载荷。
     *
     * @param enhancements 增强计划列表
     * @param newArticles 新建文章计划列表
     */
    @JsonCreator
    public IncrementalMatchPayload(
            @JsonProperty("enhancements") List<EnhancementPayload> enhancements,
            @JsonProperty("new_articles") List<NewArticlePayload> newArticles
    ) {
        this.enhancements = enhancements == null ? List.of() : new ArrayList<EnhancementPayload>(enhancements);
        this.newArticles = newArticles == null ? List.of() : new ArrayList<NewArticlePayload>(newArticles);
    }

    /**
     * 返回增强计划列表。
     *
     * @return 增强计划列表
     */
    public List<EnhancementPayload> getEnhancements() {
        return enhancements;
    }

    /**
     * 返回新建文章计划列表。
     *
     * @return 新建文章计划列表
     */
    public List<NewArticlePayload> getNewArticles() {
        return newArticles;
    }

    /**
     * 增强计划载荷
     *
     * 职责：承载单条增强计划的结构化字段
     *
     * @author xiexu
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EnhancementPayload {

        private final String targetArticleId;

        private final String newInfoSummary;

        private final List<String> sourceRefs;

        /**
         * 创建增强计划载荷。
         *
         * @param targetArticleId 目标文章标识
         * @param newInfoSummary 新增信息摘要
         * @param sourceRefs 来源引用
         */
        @JsonCreator
        public EnhancementPayload(
                @JsonProperty("target_article_id") String targetArticleId,
                @JsonProperty("new_info_summary") String newInfoSummary,
                @JsonProperty("source_refs") List<String> sourceRefs
        ) {
            this.targetArticleId = targetArticleId == null ? "" : targetArticleId.trim();
            this.newInfoSummary = newInfoSummary == null ? "" : newInfoSummary.trim();
            this.sourceRefs = sourceRefs == null ? List.of() : new ArrayList<String>(sourceRefs);
        }

        /**
         * 返回目标文章标识。
         *
         * @return 目标文章标识
         */
        public String getTargetArticleId() {
            return targetArticleId;
        }

        /**
         * 返回新增信息摘要。
         *
         * @return 新增信息摘要
         */
        public String getNewInfoSummary() {
            return newInfoSummary;
        }

        /**
         * 返回来源引用。
         *
         * @return 来源引用
         */
        public List<String> getSourceRefs() {
            return sourceRefs;
        }
    }

    /**
     * 新建文章计划载荷
     *
     * 职责：承载单条新建文章计划的结构化字段
     *
     * @author xiexu
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NewArticlePayload {

        private final String id;

        private final String title;

        private final String description;

        private final List<String> sourceRefs;

        private final List<String> relatedTo;

        /**
         * 创建新建文章计划载荷。
         *
         * @param id 文章标识
         * @param title 标题
         * @param description 描述
         * @param sourceRefs 来源引用
         * @param relatedTo 关联文章
         */
        @JsonCreator
        public NewArticlePayload(
                @JsonProperty("id") String id,
                @JsonProperty("title") String title,
                @JsonProperty("description") String description,
                @JsonProperty("source_refs") List<String> sourceRefs,
                @JsonProperty("related_to") List<String> relatedTo
        ) {
            this.id = id == null ? "" : id.trim();
            this.title = title == null ? "" : title.trim();
            this.description = description == null ? "" : description.trim();
            this.sourceRefs = sourceRefs == null ? List.of() : new ArrayList<String>(sourceRefs);
            this.relatedTo = relatedTo == null ? List.of() : new ArrayList<String>(relatedTo);
        }

        /**
         * 返回文章标识。
         *
         * @return 文章标识
         */
        public String getId() {
            return id;
        }

        /**
         * 返回标题。
         *
         * @return 标题
         */
        public String getTitle() {
            return title;
        }

        /**
         * 返回描述。
         *
         * @return 描述
         */
        public String getDescription() {
            return description;
        }

        /**
         * 返回来源引用。
         *
         * @return 来源引用
         */
        public List<String> getSourceRefs() {
            return sourceRefs;
        }

        /**
         * 返回关联文章列表。
         *
         * @return 关联文章列表
         */
        public List<String> getRelatedTo() {
            return relatedTo;
        }
    }
}
