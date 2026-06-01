package com.xbk.lattice.api.admin;

import lombok.AccessLevel;
import lombok.Getter;

import java.util.List;

/**
 * 管理侧文章摘要响应。
 *
 * <p>承载管理侧文章列表中单篇文章的摘要信息——含标识、审查状态、风险等级、
 * 热点标记与标题画像。不含正文全文（与 {@link AdminArticleDetailResponse} 区分）。
 * {@code hotspot} 和 {@code requiresResultVerification} 的 getter 保留手写命名，
 * 避免 JSON 序列化属性名变化。
 *
 * @author xiexu
 */
@Getter
public class AdminArticleSummaryResponse {

    /** 资料源主键。为 {@code null} 表示多源或无固定 source。 */
    private final Long sourceId;

    /** 文章唯一键（编译生成，跨 source 稳定）。 */
    private final String articleKey;

    /** 概念标识（编译输入，用于跨 source 去重）。 */
    private final String conceptId;

    /** 文章标题。 */
    private final String title;

    /**
     * 文章生命周期状态。
     *
     * <p>可选值：{@code active} / {@code deprecated} / {@code archived}。
     * 影响列表中的状态标签展示和可用操作（如纠错、回滚）。</p>
     */
    private final String lifecycle;

    /**
     * 审查状态。
     *
     * <p>可选值：{@code accepted} / {@code needs_human_review} / {@code published}。
     * 驱动列表中的审查标签颜色和批量操作按钮。</p>
     */
    private final String reviewStatus;

    /**
     * 风险等级。
     *
     * <p>可选值：{@code low} / {@code medium} / {@code high}。
     * 影响列表中文章行的警示颜色。</p>
     */
    private final String riskLevel;

    /**
     * 风险原因列表。
     *
     * <p>与 {@code riskLevel} 配合解释风险来源。</p>
     */
    private final List<String> riskReasons;

    /**
     * 是否热点文章。
     *
     * <p>基于 usage stats 热度分动态计算。getter 保留手写 {@code getIsHotspot()}，
     * Lombok 已排除此字段以防止 JSON 属性名从 {@code "isHotspot"} 变为 {@code "hotspot"}。</p>
     */
    @Getter(AccessLevel.NONE)
    private final boolean hotspot;

    /**
     * 是否需要结果抽检。
     *
     * <p>由质量抽检策略决定。getter 保留手写 {@code getRequiresResultVerification()}，
     * Lombok 已排除此字段。</p>
     */
    @Getter(AccessLevel.NONE)
    private final boolean requiresResultVerification;

    /** 最近编译时间（ISO-8601 字符串）。为 {@code null} 表示原始录入。 */
    private final String compiledAt;

    /** 首次入库时间（ISO-8601 字符串）。 */
    private final String createdAt;

    /** 最近入库时间（ISO-8601 字符串）。 */
    private final String updatedAt;

    /**
     * 文章摘要。
     *
     * <p>为 {@code null} 表示未生成摘要。</p>
     */
    private final String summary;

    /** 来源文件数（由 sourcePaths.size() 计算）。 */
    private final int sourceCount;

    /** 首个来源文件路径。 */
    private final String primarySourcePath;

    /** 全部来源文件路径列表。 */
    private final List<String> sourcePaths;

    /**
     * 首个来源文件名。
     *
     * <p>仅 Summary 中有此字段，Detail 中无。用于列表中快速展示来源信息。</p>
     */
    private final String primarySourceName;

    /**
     * 标题画像。
     *
     * <p>为 {@code null} 时前端降级展示 {@code title} 字段。</p>
     */
    private final AdminArticleTitleProfile titleProfile;

    /**
     * 创建管理侧文章摘要响应。
     *
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param lifecycle 生命周期
     * @param reviewStatus 审查状态
     * @param riskLevel 风险等级
     * @param riskReasons 风险原因
     * @param hotspot 是否热点
     * @param requiresResultVerification 是否需要结果抽检
     * @param compiledAt 编译时间
     * @param createdAt 首次入库时间
     * @param updatedAt 最近入库时间
     * @param summary 摘要
     * @param sourceCount 来源数量
     * @param primarySourcePath 首个来源路径
     * @param sourcePaths 完整来源路径列表
     * @param primarySourceName 首个来源文件名
     * @param titleProfile 标题画像
     */
    public AdminArticleSummaryResponse(
            Long sourceId,
            String articleKey,
            String conceptId,
            String title,
            String lifecycle,
            String reviewStatus,
            String riskLevel,
            List<String> riskReasons,
            boolean hotspot,
            boolean requiresResultVerification,
            String compiledAt,
            String createdAt,
            String updatedAt,
            String summary,
            int sourceCount,
            String primarySourcePath,
            List<String> sourcePaths,
            String primarySourceName,
            AdminArticleTitleProfile titleProfile
    ) {
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.lifecycle = lifecycle;
        this.reviewStatus = reviewStatus;
        this.riskLevel = riskLevel;
        this.riskReasons = riskReasons;
        this.hotspot = hotspot;
        this.requiresResultVerification = requiresResultVerification;
        this.compiledAt = compiledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.summary = summary;
        this.sourceCount = sourceCount;
        this.primarySourcePath = primarySourcePath;
        this.sourcePaths = sourcePaths;
        this.primarySourceName = primarySourceName;
        this.titleProfile = titleProfile;
    }

    /**
     * 返回是否热点文章。
     *
     * <p>getter 命名为 {@code getIsHotspot()} 以保持
     * JSON 序列化属性名 {@code "isHotspot"} 不变。</p>
     *
     * @return 是否热点文章
     */
    public boolean getIsHotspot() {
        return hotspot;
    }

    /**
     * 返回是否需要结果抽检。
     *
     * <p>getter 命名为 {@code getRequiresResultVerification()} 以保持
     * JSON 序列化属性名 {@code "requiresResultVerification"} 不变。</p>
     *
     * @return 是否需要结果抽检
     */
    public boolean getRequiresResultVerification() {
        return requiresResultVerification;
    }
}
