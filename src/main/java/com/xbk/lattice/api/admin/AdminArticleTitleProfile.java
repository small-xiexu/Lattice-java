package com.xbk.lattice.api.admin;

/**
 * 管理侧文章标题画像。
 *
 * 职责：承载列表与详情页共享的标题来源链路字段
 *
 * @author xiexu
 */
public class AdminArticleTitleProfile {

    private final String sourceTitle;

    private final String anchorTitle;

    private final String representativeTitle;

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

    public String getSourceTitle() {
        return sourceTitle;
    }

    public String getAnchorTitle() {
        return anchorTitle;
    }

    public String getRepresentativeTitle() {
        return representativeTitle;
    }

    public String getTitleGenerationMode() {
        return titleGenerationMode;
    }
}
