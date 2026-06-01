package com.xbk.lattice.query.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * 查询文章命中。
 *
 * <p>检索链路中各通道返回的统一命中结构，承载命中内容的身份、正文、元数据和评分信息。
 * 经过 RRF 融合后进入后续 answer/citation/fallback 链路，最终被消费为
 * {@link com.xbk.lattice.api.query.QueryArticleResponse} 或引用来源。
 *
 * @author xiexu
 */
@Getter
public class QueryArticleHit {

    /**
     * 证据类型。
     *
     * <p>标识这条命中来自哪个证据通道——ARTICLE 表示编译文章命中，FACT_CARD 表示结构化事实卡命中，
     * SOURCE 表示源文件命中等。RRF 融合、citation 组装和 fallback 证据选择都会根据这个类型做不同的处理。</p>
     */
    private final QueryEvidenceType evidenceType;

    /**
     * 资料源主键。
     *
     * <p>对应原始资料在系统中的唯一 ID。FACT_CARD 和 SOURCE 类型命中通常有值，
     * 纯编译文章可能为空。</p>
     */
    private final Long sourceId;

    /**
     * 文章唯一键。
     *
     * <p>编译后文章的业务标识，RRF 融合用它做同文章内不同 chunk 的去重和身份合并。
     * 普通 article 按 articleKey 融合，带 chunkIdentity 的 chunk 级命中按 chunk 身份融合。</p>
     */
    private final String articleKey;

    /**
     * 概念标识。
     *
     * <p>命中所属概念的稳定标识，用于按概念维度聚合命中结果和生成响应中的概念归属信息。</p>
     */
    private final String conceptId;

    /**
     * 命中标题。
     *
     * <p>检索结果展示、引用溯源和响应 projection 都会用到这个标题。
     * 标题由编译阶段从源文件内容中提取或由系统自动生成。</p>
     */
    private final String title;

    /**
     * 命中内容正文。
     *
     * <p>检索命中的实际文本片段或结构化数据摘要。这是 RRF 评分计算、LLM prompt 证据组装
     * 和 citation 校验的直接输入。内容质量和相关性直接影响最终答案的准确性。</p>
     */
    private final String content;

    /**
     * 元数据 JSON。
     *
     * <p>附加的机器可读元信息，如 chunk 身份（chunkIdentity、chunkIndex、sectionAnchor）、
     * 结构化字段路径（fieldPath）或事实卡终端单元（terminalUnit）标识。
     * citation 组装和审计链路通过解析这个字段获取溯源所需的附加上下文。</p>
     */
    private final String metadataJson;

    /**
     * 审查状态。
     *
     * <p>对应命中文章在 compile review 阶段的审查结论。只有 reviewStatus=passed 且
     * lifecycle=ACTIVE 的文章才会被 query visibility hard filter 放行进入检索结果。
     * 当命中的是非文章类证据时可能为空。</p>
     */
    private final String reviewStatus;

    /**
     * 来源文件路径列表。
     *
     * <p>命中内容在代码库或文档库中的原始文件路径，用于 citation 溯源展示和生成可点击的文件链接。</p>
     */
    private final List<String> sourcePaths;

    /**
     * 检索评分。
     *
     * <p>各通道 FTS/向量相似度计算后、经 RRF 融合再排序后的最终分数。分数越高，
     * 该命中在最终结果排序中越靠前，fallback 证据选择和 answer conclusion 构建都会优先取高分命中。</p>
     */
    private final double score;

    /**
     * 创建查询文章命中。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param sourcePaths 来源路径
     * @param score 评分
     */
    public QueryArticleHit(
            String conceptId,
            String title,
            String content,
            String metadataJson,
            List<String> sourcePaths,
            double score
    ) {
        this(conceptId, title, content, metadataJson, null, sourcePaths, score);
    }

    /**
     * 创建查询文章命中。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param reviewStatus 审查状态
     * @param sourcePaths 来源路径
     * @param score 评分
     */
    public QueryArticleHit(
            String conceptId,
            String title,
            String content,
            String metadataJson,
            String reviewStatus,
            List<String> sourcePaths,
            double score
    ) {
        this(QueryEvidenceType.ARTICLE, null, null, conceptId, title, content, metadataJson, reviewStatus, sourcePaths, score);
    }

    /**
     * 创建查询命中。
     *
     * @param evidenceType 证据类型
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param sourcePaths 来源路径
     * @param score 评分
     */
    public QueryArticleHit(
            QueryEvidenceType evidenceType,
            String conceptId,
            String title,
            String content,
            String metadataJson,
            List<String> sourcePaths,
            double score
    ) {
        this(evidenceType, conceptId, title, content, metadataJson, null, sourcePaths, score);
    }

    /**
     * 创建查询命中。
     *
     * @param evidenceType 证据类型
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param reviewStatus 审查状态
     * @param sourcePaths 来源路径
     * @param score 评分
     */
    public QueryArticleHit(
            QueryEvidenceType evidenceType,
            String conceptId,
            String title,
            String content,
            String metadataJson,
            String reviewStatus,
            List<String> sourcePaths,
            double score
    ) {
        this(evidenceType, null, null, conceptId, title, content, metadataJson, reviewStatus, sourcePaths, score);
    }

    /**
     * 创建查询文章命中。
     *
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param sourcePaths 来源路径
     * @param score 评分
     */
    public QueryArticleHit(
            QueryEvidenceType evidenceType,
            Long sourceId,
            String articleKey,
            String conceptId,
            String title,
            String content,
            String metadataJson,
            List<String> sourcePaths,
            double score
    ) {
        this(evidenceType, sourceId, articleKey, conceptId, title, content, metadataJson, null, sourcePaths, score);
    }

    /**
     * 创建查询文章命中。
     *
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param sourcePaths 来源路径
     * @param score 评分
     */
    public QueryArticleHit(
            Long sourceId,
            String articleKey,
            String conceptId,
            String title,
            String content,
            String metadataJson,
            List<String> sourcePaths,
            double score
    ) {
        this(sourceId, articleKey, conceptId, title, content, metadataJson, null, sourcePaths, score);
    }

    /**
     * 创建查询文章命中。
     *
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param reviewStatus 审查状态
     * @param sourcePaths 来源路径
     * @param score 评分
     */
    public QueryArticleHit(
            Long sourceId,
            String articleKey,
            String conceptId,
            String title,
            String content,
            String metadataJson,
            String reviewStatus,
            List<String> sourcePaths,
            double score
    ) {
        this(QueryEvidenceType.ARTICLE, sourceId, articleKey, conceptId, title, content, metadataJson, reviewStatus, sourcePaths, score);
    }

    /**
     * 创建查询命中。
     *
     * @param evidenceType 证据类型
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param metadataJson 元数据 JSON
     * @param sourcePaths 来源路径
     * @param score 评分
     */
    @JsonCreator
    public QueryArticleHit(
            @JsonProperty("evidenceType") QueryEvidenceType evidenceType,
            @JsonProperty("sourceId") Long sourceId,
            @JsonProperty("articleKey") String articleKey,
            @JsonProperty("conceptId") String conceptId,
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("metadataJson") String metadataJson,
            @JsonProperty("reviewStatus") String reviewStatus,
            @JsonProperty("sourcePaths") List<String> sourcePaths,
            @JsonProperty("score") double score
    ) {
        this.evidenceType = evidenceType;
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.content = content;
        this.metadataJson = metadataJson;
        this.reviewStatus = reviewStatus;
        this.sourcePaths = sourcePaths;
        this.score = score;
    }
}
