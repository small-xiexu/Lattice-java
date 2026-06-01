package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * 搜索响应。
 *
 * <p>承载无答案生成场景下的多路融合搜索结果，直接返回检索命中的证据列表，
 * 不经过 LLM 回答生成和 citation 组装。调用方通过这个结构实现纯搜索模式的交互。
 *
 * @author xiexu
 */
@Getter
public class SearchResponse {

    /**
     * 命中总数。
     *
     * <p>所有通道融合后的搜索结果总数，调用方用于分页和总数展示。</p>
     */
    private final int count;

    /**
     * 搜索结果条目列表。
     *
     * <p>每条记录是融合排序后的一条搜索命中，包含证据类型、来源、标题、
     * 内容片段和评分。调用方逐条渲染搜索结果列表。</p>
     */
    private final List<SearchHitResponse> items;

    /**
     * 创建搜索响应。
     *
     * @param count 数量
     * @param items 条目
     */
    @JsonCreator
    public SearchResponse(
            @JsonProperty("count") int count,
            @JsonProperty("items") List<SearchHitResponse> items
    ) {
        this.count = count;
        this.items = items;
    }
}
