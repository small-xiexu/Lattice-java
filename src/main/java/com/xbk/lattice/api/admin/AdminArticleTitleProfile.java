package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 管理侧文章标题画像。
 *
 * <p>承载列表与详情页共享的标题来源链路字段——从原始文档标题到最终代表标题的完整生成路径，
 * 嵌套于 {@link AdminArticleDetailResponse} 和 {@link AdminArticleSummaryResponse} 中。
 *
 * @author xiexu
 */
@Getter
public class AdminArticleTitleProfile {

    /**
     * 来源文档中的原始标题。
     *
     * <p>为 {@code null} 表示未从源文档中提取到标题。</p>
     */
    private final String sourceTitle;

    /**
     * 文档切分时的锚点标题。
     *
     * <p>为 {@code null} 表示未做切分或无锚点标题。</p>
     */
    private final String anchorTitle;

    /**
     * 代表标题。
     *
     * <p>由服务端综合 {@code sourceTitle} 和文章 {@code title} 选取，
     * 用于列表展示和搜索索引。</p>
     */
    private final String representativeTitle;

    /**
     * 标题生成模式。
     *
     * <p>可选值：{@code LLM_GENERATED} / {@code SOURCE_EXTRACTED} / {@code LEGACY_UNSET}。
     * 反映标题的来源方式，前端可据此展示不同的标题来源标识。</p>
     */
    private final String titleGenerationMode;

    /**
     * 创建管理侧文章标题画像。
     *
     * @param sourceTitle 来源标题
     * @param anchorTitle 切分标题
     * @param representativeTitle 代表标题
     * @param titleGenerationMode 生成模式
     */
    public AdminArticleTitleProfile(
            String sourceTitle,
            String anchorTitle,
            String representativeTitle,
            String titleGenerationMode
    ) {
        this.sourceTitle = sourceTitle;
        this.anchorTitle = anchorTitle;
        this.representativeTitle = representativeTitle;
        this.titleGenerationMode = titleGenerationMode;
    }
}
