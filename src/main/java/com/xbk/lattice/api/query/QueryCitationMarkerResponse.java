package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 查询引用点响应
 *
 * 职责：承载答案正文中一个可见引用点及其对应资料集合
 *
 * @author xiexu
 */
public class QueryCitationMarkerResponse {

    private final int markerOrdinal;

    private final String markerId;

    private final String citationLiteral;

    private final List<String> citationLiterals;

    private final String claimText;

    private final int sourceCount;

    private final List<QueryCitationSourceResponse> sources;

    /**
     * 创建查询引用点响应。
     *
     * @param markerOrdinal 引用点顺序号
     * @param markerId 引用点标识
     * @param citationLiteral 答案正文中的原始引用文本
     * @param citationLiterals 答案正文中的原始引用文本列表
     * @param claimText 引用所属 claim 文本
     * @param sourceCount 引用资料数量
     * @param sources 引用资料明细
     */
    @JsonCreator
    public QueryCitationMarkerResponse(
            @JsonProperty("markerOrdinal") int markerOrdinal,
            @JsonProperty("markerId") String markerId,
            @JsonProperty("citationLiteral") String citationLiteral,
            @JsonProperty("citationLiterals") List<String> citationLiterals,
            @JsonProperty("claimText") String claimText,
            @JsonProperty("sourceCount") int sourceCount,
            @JsonProperty("sources") List<QueryCitationSourceResponse> sources
    ) {
        this.markerOrdinal = markerOrdinal;
        this.markerId = markerId;
        this.citationLiteral = citationLiteral;
        this.citationLiterals = citationLiterals == null ? List.of() : citationLiterals;
        this.claimText = claimText;
        this.sources = sources == null ? List.of() : sources;
        this.sourceCount = sourceCount <= 0 ? this.sources.size() : sourceCount;
    }

    /**
     * 获取引用点顺序号。
     *
     * @return 引用点顺序号
     */
    public int getMarkerOrdinal() {
        return markerOrdinal;
    }

    /**
     * 获取引用点标识。
     *
     * @return 引用点标识
     */
    public String getMarkerId() {
        return markerId;
    }

    /**
     * 获取答案正文中的原始引用文本。
     *
     * @return 原始引用文本
     */
    public String getCitationLiteral() {
        return citationLiteral;
    }

    /**
     * 获取答案正文中的原始引用文本列表。
     *
     * @return 原始引用文本列表
     */
    public List<String> getCitationLiterals() {
        return citationLiterals;
    }

    /**
     * 获取引用所属 claim 文本。
     *
     * @return claim 文本
     */
    public String getClaimText() {
        return claimText;
    }

    /**
     * 获取引用资料数量。
     *
     * @return 引用资料数量
     */
    public int getSourceCount() {
        return sourceCount;
    }

    /**
     * 获取引用资料明细。
     *
     * @return 引用资料明细
     */
    public List<QueryCitationSourceResponse> getSources() {
        return sources;
    }
}
