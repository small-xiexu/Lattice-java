package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 查询来源响应。
 *
 * <p>承载单条引用来源的元数据，包括来源标识、标题、文件路径和推导方式。
 * 调用方通过这个结构了解答案中的每条引用来自哪个资料、是如何被检索到的。
 *
 * @author xiexu
 */
@Getter
public class QuerySourceResponse {

    /**
     * 资料源主键。
     *
     * <p>对应原始资料在系统中的唯一 ID。调用方可以用它关联资料库做进一步的溯源查询。
     * 当来源为检索动态生成（非持久化资料）时可能为空。</p>
     */
    private final Long sourceId;

    /**
     * 文章唯一键。
     *
     * <p>对应编译后文章的业务标识，用于在文章维度关联和去重。当来源直接来自源文件而非编译文章时可能为空。</p>
     */
    private final String articleKey;

    /**
     * 概念标识。
     *
     * <p>来源所属概念的稳定标识，用于按概念聚合展示和关联检索。调用方可通过它判断多个来源是否属于同一主题领域。</p>
     */
    private final String conceptId;

    /**
     * 来源标题。
     *
     * <p>调用方在引用列表和溯源面板中展示这个标题，帮助用户快速识别每条引用的来源。
     * 标题可能来自资料库、文章编译产物，也可能由系统根据文件路径或内容摘要自动生成。</p>
     */
    private final String title;

    /**
     * 来源文件路径列表。
     *
     * <p>记录来源资料在代码库或文档库中的路径，用于支撑溯源跳转和文件级引用展示。
     * 调用方可以据此生成可点击的文件链接。</p>
     */
    private final List<String> sourcePaths;

    /**
     * 来源推导方式。
     *
     * <p>说明这条来源是被检索命中的、被 projection 推导出来的、还是从 top-K 兜底列表取的。
     * 调用方可以据此判断引用来源的置信度——检索命中通常比推导和兜底更可靠。</p>
     */
    private final String derivation;

    /**
     * 创建查询来源响应。
     *
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param sourcePaths 来源路径
     * @param derivation 来源推导方式
     */
    @Builder
    @JsonCreator
    public QuerySourceResponse(
            @JsonProperty("sourceId") Long sourceId,
            @JsonProperty("articleKey") String articleKey,
            @JsonProperty("conceptId") String conceptId,
            @JsonProperty("title") String title,
            @JsonProperty("sourcePaths") List<String> sourcePaths,
            @JsonProperty("derivation") String derivation
    ) {
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.sourcePaths = sourcePaths;
        this.derivation = derivation;
    }
}
