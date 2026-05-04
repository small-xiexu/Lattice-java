package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 查询引用来源响应
 *
 * 职责：承载单个答案引用点下的一条资料明细
 *
 * @author xiexu
 */
public class QueryCitationSourceResponse {

    private final String sourceType;

    private final String targetKey;

    private final Long sourceId;

    private final String articleKey;

    private final String conceptId;

    private final String title;

    private final List<String> sourcePaths;

    private final String matchedExcerpt;

    private final String validationStatus;

    private final String reason;

    private final double score;

    /**
     * 创建查询引用来源响应。
     *
     * @param sourceType 来源类型
     * @param targetKey 引用目标键
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param sourcePaths 来源路径
     * @param matchedExcerpt 命中摘录
     * @param validationStatus 校验状态
     * @param reason 校验原因
     * @param score 检索得分
     */
    @JsonCreator
    public QueryCitationSourceResponse(
            @JsonProperty("sourceType") String sourceType,
            @JsonProperty("targetKey") String targetKey,
            @JsonProperty("sourceId") Long sourceId,
            @JsonProperty("articleKey") String articleKey,
            @JsonProperty("conceptId") String conceptId,
            @JsonProperty("title") String title,
            @JsonProperty("sourcePaths") List<String> sourcePaths,
            @JsonProperty("matchedExcerpt") String matchedExcerpt,
            @JsonProperty("validationStatus") String validationStatus,
            @JsonProperty("reason") String reason,
            @JsonProperty("score") double score
    ) {
        this.sourceType = sourceType;
        this.targetKey = targetKey;
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.sourcePaths = sourcePaths == null ? List.of() : sourcePaths;
        this.matchedExcerpt = matchedExcerpt;
        this.validationStatus = validationStatus;
        this.reason = reason;
        this.score = score;
    }

    /**
     * 获取来源类型。
     *
     * @return 来源类型
     */
    public String getSourceType() {
        return sourceType;
    }

    /**
     * 获取引用目标键。
     *
     * @return 引用目标键
     */
    public String getTargetKey() {
        return targetKey;
    }

    /**
     * 获取资料源主键。
     *
     * @return 资料源主键
     */
    public Long getSourceId() {
        return sourceId;
    }

    /**
     * 获取文章唯一键。
     *
     * @return 文章唯一键
     */
    public String getArticleKey() {
        return articleKey;
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
     * 获取来源路径。
     *
     * @return 来源路径
     */
    public List<String> getSourcePaths() {
        return sourcePaths;
    }

    /**
     * 获取命中摘录。
     *
     * @return 命中摘录
     */
    public String getMatchedExcerpt() {
        return matchedExcerpt;
    }

    /**
     * 获取校验状态。
     *
     * @return 校验状态
     */
    public String getValidationStatus() {
        return validationStatus;
    }

    /**
     * 获取校验原因。
     *
     * @return 校验原因
     */
    public String getReason() {
        return reason;
    }

    /**
     * 获取检索得分。
     *
     * @return 检索得分
     */
    public double getScore() {
        return score;
    }
}
