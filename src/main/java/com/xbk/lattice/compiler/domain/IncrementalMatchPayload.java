package com.xbk.lattice.compiler.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 增量匹配载荷。
 *
 * <p>承载增量编译步骤的结构化输出——增强计划与新建文章计划。
 * 所有嵌套列表在构造时做防御性拷贝，运行时不可变。
 *
 * @author xiexu
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncrementalMatchPayload {

    /** 增强计划列表。不可变（构造时防御性拷贝）。 */
    private final List<EnhancementPayload> enhancements;

    /** 新建文章计划列表。不可变（构造时防御性拷贝）。 */
    private final List<NewArticlePayload> newArticles;

    @JsonCreator
    public IncrementalMatchPayload(
            @JsonProperty("enhancements") List<EnhancementPayload> enhancements,
            @JsonProperty("new_articles") List<NewArticlePayload> newArticles
    ) {
        this.enhancements = enhancements == null ? List.of() : new ArrayList<EnhancementPayload>(enhancements);
        this.newArticles = newArticles == null ? List.of() : new ArrayList<NewArticlePayload>(newArticles);
    }

    /**
     * 增强计划载荷。
     *
     * <p>承载单条增强计划——目标文章、新增信息和来源引用。
     */
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EnhancementPayload {

        /** 目标文章标识。为空时取 {@code ""}。 */
        private final String targetArticleId;

        /** 新增信息摘要。为空时取 {@code ""}。 */
        private final String newInfoSummary;

        /** 来源引用列表。不可变（构造时防御性拷贝）。 */
        private final List<String> sourceRefs;

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
    }

    /**
     * 新建文章计划载荷。
     *
     * <p>承载单条新建文章计划——标识、标题、来源和关联关系。
     */
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NewArticlePayload {

        /** 文章标识。为空时取 {@code ""}。 */
        private final String id;

        /** 文章标题。为空时取 {@code ""}。 */
        private final String title;

        /** 文章描述。为空时取 {@code ""}。 */
        private final String description;

        /** 来源引用列表。不可变（构造时防御性拷贝）。 */
        private final List<String> sourceRefs;

        /** 关联文章列表。不可变（构造时防御性拷贝）。 */
        private final List<String> relatedTo;

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
    }
}
