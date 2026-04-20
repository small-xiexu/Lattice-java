package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 搜索响应
 *
 * 职责：承载无答案生成场景下的多路融合搜索结果
 *
 * @author xiexu
 */
public class SearchResponse {

    private final int count;

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

    public int getCount() {
        return count;
    }

    public List<SearchHitResponse> getItems() {
        return items;
    }
}
