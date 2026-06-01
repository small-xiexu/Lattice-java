package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * 查询文章响应。
 *
 * <p>承载单篇命中文章的摘要信息，包括文章标识、标题和推导方式。
 * 与 {@link QuerySourceResponse} 互补——sources 偏向原始文件溯源，articles 偏向编译加工后的知识文章。
 *
 * @author xiexu
 */
@Getter
public class QueryArticleResponse {

    /**
     * 资料源主键。
     *
     * <p>对应文章所关联的原始资料 ID。当文章来自纯编译产物（无对应原始资料）时可能为空。</p>
     */
    private final Long sourceId;

    /**
     * 文章唯一键。
     *
     * <p>文章在系统中的业务标识，用于跨查询关联和去重。调用方可以用它追溯同一篇文章在不同查询中的表现。</p>
     */
    private final String articleKey;

    /**
     * 概念标识。
     *
     * <p>文章所属概念的稳定标识，用于按概念聚合展示。调用方可通过它判断文章的知识领域归属。</p>
     */
    private final String conceptId;

    /**
     * 文章标题。
     *
     * <p>调用方在检索命中列表和引用面板中展示这个标题。标题来自文章编译阶段的元数据提取，
     * 也可能由系统根据内容摘要自动生成。</p>
     */
    private final String title;

    /**
     * 来源推导方式。
     *
     * <p>说明这篇文章是被检索命中的、被 projection 推导出来的、还是从 top-K 兜底列表取的。
     * 调用方可以据此判断文章命中的置信度。</p>
     */
    private final String derivation;

    /**
     * 创建查询文章响应。
     *
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param derivation 来源推导方式
     */
    @Builder
    @JsonCreator
    public QueryArticleResponse(
            @JsonProperty("sourceId") Long sourceId,
            @JsonProperty("articleKey") String articleKey,
            @JsonProperty("conceptId") String conceptId,
            @JsonProperty("title") String title,
            @JsonProperty("derivation") String derivation
    ) {
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.derivation = derivation;
    }
}
