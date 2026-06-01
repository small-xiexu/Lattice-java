package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * 查询引用来源响应。
 *
 * <p>承载单个答案引用角标下的一条具体资料明细，说明引用来源是什么、来自哪个文件、
 * 经过了怎样的校验、以及匹配得分。前端通过这个结构在引用详情面板中展示每条引用的溯源信息。
 *
 * @author xiexu
 */
@Getter
public class QueryCitationSourceResponse {

    /**
     * 来源类型。
     *
     * <p>标识这条引用资料的来源类别，例如 SOURCE_FILE（原始文件）、ARTICLE（编译文章）、
     * FACT_CARD_CLASSIFICATION（事实卡分类）等。调用方可以据此决定展示样式——原始文件显示路径，
     * 编译文章显示标题和摘要。</p>
     */
    private final String sourceType;

    /**
     * 引用目标键。
     *
     * <p>在多投影引用场景下，用于区分同一条 citation marker 下不同 projection 的目标标识。
     * 例如 filePath、articleKey 等。调用方通过它精确关联引用点和具体来源实体。</p>
     */
    private final String targetKey;

    /**
     * 资料源主键。
     *
     * <p>对应原始资料在系统中的唯一 ID。当来源直接来自编译文章而非原始资料时可能为空。</p>
     */
    private final Long sourceId;

    /**
     * 文章唯一键。
     *
     * <p>编译后文章的业务标识，用于跨查询关联。当来源为原始源文件而非编译文章时可能为空。</p>
     */
    private final String articleKey;

    /**
     * 概念标识。
     *
     * <p>来源所属概念的稳定标识，用于按概念聚合展示引用来源的领域归属。</p>
     */
    private final String conceptId;

    /**
     * 来源标题。
     *
     * <p>调用方在引用详情面板中展示这个标题。标题来自编译阶段提取或系统自动生成。</p>
     */
    private final String title;

    /**
     * 来源文件路径列表。
     *
     * <p>记录引用资料在代码库或文档库中的文件路径。构造器保证不为 null——传入 null 时归一化为空列表。
     * 调用方据此生成可点击的文件链接。</p>
     */
    private final List<String> sourcePaths;

    /**
     * 引用的匹配摘录。
     *
     * <p>原始资料中与引用 claim 匹配的具体文本片段。调用方在引用详情中展示这个摘录，
     * 帮助用户快速判断引用是否准确支撑了答案中的断言。</p>
     */
    private final String matchedExcerpt;

    /**
     * 引用校验状态。
     *
     * <p>记录引用核验链路对这条来源的校验结果，例如 VERIFIED（已验证可支撑）、
     * DEMOTED（降级，疑似编造）、SKIPPED（跳过核验）等。调用方据此判断引用可信度，
     * 并在引用详情中以不同样式（绿色勾/红色警告/灰色跳过）展示。</p>
     */
    private final String validationStatus;

    /**
     * 校验原因。
     *
     * <p>当 validationStatus 为 DEMOTED 或 SKIPPED 时，说明具体的校验结论——
     * 例如"引用文本与来源内容不匹配""超出核验范围"。VERIFIED 时通常为空。</p>
     */
    private final String reason;

    /**
     * 检索得分。
     *
     * <p>这条来源在对应检索通道中拿到的相关性分数。分数越高，表示这条来源与查询的相关性越强。</p>
     */
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
}
