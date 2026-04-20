package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 搜索命中响应
 *
 * 职责：承载搜索接口中的单条融合证据命中
 *
 * @author xiexu
 */
public class SearchHitResponse {

    private final String evidenceType;

    private final Long sourceId;

    private final String articleKey;

    private final String conceptId;

    private final String title;

    private final String content;

    private final String metadataJson;

    private final java.util.List<String> sourcePaths;

    private final double score;

    /**
     * 创建搜索命中响应。
     *
     * @param evidenceType 证据类型
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据
     * @param sourcePaths 来源路径
     * @param score 得分
     */
    public SearchHitResponse(
            String evidenceType,
            String conceptId,
            String title,
            String content,
            String metadataJson,
            java.util.List<String> sourcePaths,
            double score
    ) {
        this(evidenceType, null, null, conceptId, title, content, metadataJson, sourcePaths, score);
    }

    /**
     * 创建搜索命中响应。
     *
     * @param evidenceType 证据类型
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据
     * @param sourcePaths 来源路径
     * @param score 得分
     */
    @JsonCreator
    public SearchHitResponse(
            @JsonProperty("evidenceType") String evidenceType,
            @JsonProperty("sourceId") Long sourceId,
            @JsonProperty("articleKey") String articleKey,
            @JsonProperty("conceptId") String conceptId,
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("metadataJson") String metadataJson,
            @JsonProperty("sourcePaths") java.util.List<String> sourcePaths,
            @JsonProperty("score") double score
    ) {
        this.evidenceType = evidenceType;
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.content = content;
        this.metadataJson = metadataJson;
        this.sourcePaths = sourcePaths;
        this.score = score;
    }

    public String getEvidenceType() {
        return evidenceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getArticleKey() {
        return articleKey;
    }

    public String getConceptId() {
        return conceptId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public java.util.List<String> getSourcePaths() {
        return sourcePaths;
    }

    public double getScore() {
        return score;
    }
}
