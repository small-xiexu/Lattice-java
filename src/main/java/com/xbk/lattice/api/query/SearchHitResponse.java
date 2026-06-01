package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 搜索命中响应。
 *
 * <p>承载搜索接口中单条融合证据命中的元数据，包含证据类型、来源标识、
 * 标题、内容片段和评分。与 {@link QueryArticleHit} 类似，
 * 但面向纯搜索场景，不需要 answer/citation 链路的额外字段。
 *
 * @author xiexu
 */
@Getter
public class SearchHitResponse {

    /**
     * 证据类型。
     *
     * <p>标识命中的证据类别，例如 ARTICLE（编译文章）、FACT_CARD（事实卡）、
     * SOURCE（源文件）等。调用方据此决定搜索结果卡片的展示样式。</p>
     */
    private final String evidenceType;

    /**
     * 资料源主键。
     *
     * <p>原始资料在系统中的唯一 ID。非原始资料命中时可能为空。</p>
     */
    private final Long sourceId;

    /**
     * 文章唯一键。
     *
     * <p>编译后文章的业务标识。非文章来源命中时可能为空。</p>
     */
    private final String articleKey;

    /**
     * 概念标识。
     *
     * <p>命中所属概念的稳定标识，用于按概念维度聚合搜索结果。</p>
     */
    private final String conceptId;

    /**
     * 命中标题。
     *
     * <p>调用方在搜索结果列表中展示这个标题，帮助用户快速识别每条命中的内容。</p>
     */
    private final String title;

    /**
     * 命中内容摘要。
     *
     * <p>检索命中的文本片段或结构化数据摘要。调用方在搜索结果卡片中展示这个片段，
     * 帮助用户判断命中是否与查询相关。</p>
     */
    private final String content;

    /**
     * 元数据 JSON。
     *
     * <p>附加的机器可读元信息，如 chunk 身份、结构化字段路径等。</p>
     */
    private final String metadataJson;

    /**
     * 来源文件路径列表。
     *
     * <p>命中内容在代码库或文档库中的原始文件路径。调用方据此生成可点击的文件链接。</p>
     */
    private final java.util.List<String> sourcePaths;

    /**
     * 检索评分。
     *
     * <p>RRF 融合后的最终分数，决定搜索结果列表中的排序位置。</p>
     */
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
}
