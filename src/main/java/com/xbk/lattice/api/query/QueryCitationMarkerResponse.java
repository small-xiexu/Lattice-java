package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * 查询引用点响应。
 *
 * <p>承载答案正文中一个可见引用角标及其对应的来源资料集合。
 * 前端通过这个结构在答案文本中渲染可点击的引用标记，并把角标和具体的来源资料关联起来。
 *
 * @author xiexu
 */
@Getter
public class QueryCitationMarkerResponse {

    /**
     * 引用点顺序号。
     *
     * <p>对应答案文本中第几个引用角标，从 1 开始计数。前端用它确定引用角标在答案中的展示顺序。</p>
     */
    private final int markerOrdinal;

    /**
     * 引用点标识。
     *
     * <p>系统生成的唯一引用点 ID，用于审计追踪和前后端引用定位。</p>
     */
    private final String markerId;

    /**
     * 答案正文中的原始引用文本。
     *
     * <p>通常是答案文本中 `[n]` 形式引用标记对应的具体引用文本片段。
     * 当引用只有一个来源时，这个字段为单一文本。</p>
     */
    private final String citationLiteral;

    /**
     * 答案正文中的原始引用文本列表。
     *
     * <p>当一个引用角标对应多个来源时，所有来源的引用文本都会出现在这个列表中。
     * 构造器保证不为 null——传入 null 时归一化为空列表。</p>
     */
    private final List<String> citationLiterals;

    /**
     * 引用所属 claim 的文本。
     *
     * <p>答案在生成后会被拆分为多个 claim（可独立核验的断言），每个 claim 可以包含多个引用。
     * 这个字段记录本条引用属于哪个 claim 的文本范围，调用方可据此在答案中定位引用的上下文。</p>
     */
    private final String claimText;

    /**
     * 引用资料数量。
     *
     * <p>这个引用角标背后关联的来源资料条数。构造器保证该值不小于实际来源列表大小——
     * 如果传入值无效，会自动取 sources 列表的实际大小。</p>
     */
    private final int sourceCount;

    /**
     * 引用资料明细列表。
     *
     * <p>每条明细描述一个支撑该引用的具体来源，包括来源类型、文件路径、校验状态等。
     * 构造器保证不为 null——传入 null 时归一化为空列表。</p>
     */
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
}
