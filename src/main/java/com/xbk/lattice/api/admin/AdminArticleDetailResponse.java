package com.xbk.lattice.api.admin;

import lombok.AccessLevel;
import lombok.Getter;

import java.util.List;

/**
 * 管理侧文章详情响应。
 *
 * <p>承载管理侧文章详情的完整展示内容——标识、正文、审查、风险、关系图与标题画像，
 * 由 {@code AdminArticleController} 从文章记录与关联数据组装返回。
 * 含大文本字段（{@code content}、{@code metadataJson}），禁止引入 {@code @Data}。
 * {@code hotspot} 和 {@code requiresResultVerification} 的 getter 保留手写命名
 * （{@code getIsHotspot()} / {@code getRequiresResultVerification()}），
 * 避免 JSON 序列化属性名变化。
 *
 * @author xiexu
 */
@Getter
public class AdminArticleDetailResponse {

    /**
     * 资料源主键。
     *
     * <p>为 {@code null} 表示多源或无固定 source。</p>
     */
    private final Long sourceId;

    /** 文章唯一键（编译生成，跨 source 稳定）。 */
    private final String articleKey;

    /** 概念标识（编译输入，用于跨 source 去重）。 */
    private final String conceptId;

    /** 文章标题。 */
    private final String title;

    /**
     * 文章正文全文。
     *
     * <p>可能为长文本，仅管理侧预览用。不应参与 {@code toString()}。</p>
     */
    private final String content;

    /**
     * 文章生命周期状态。
     *
     * <p>可选值：{@code active} / {@code deprecated} / {@code archived}。
     * 影响文章在前端是否可用以及可执行的操作（如纠错、回滚）。</p>
     */
    private final String lifecycle;

    /**
     * 最近编译时间（ISO-8601 字符串）。
     *
     * <p>为 {@code null} 表示原始录入，未经编译。</p>
     */
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

    /**
     * 审查状态。
     *
     * <p>可选值：{@code accepted} / {@code needs_human_review} / {@code published}。
     * 驱动前端展示审查标签颜色和可用的交互按钮（如发布/驳回）。</p>
     */
    private final String reviewStatus;

    /**
     * 风险等级。
     *
     * <p>可选值：{@code low} / {@code medium} / {@code high}。
     * 影响前端展示的警示颜色和风险提示强度。</p>
     */
    private final String riskLevel;

    /**
     * 风险原因列表。
     *
     * <p>与 {@code riskLevel} 配合解释风险来源（如"引用源缺失""置信度过低"）。</p>
     */
    private final List<String> riskReasons;

    /**
     * 是否热点文章。
     *
     * <p>基于 usage stats 热度分动态计算。getter 保留手写 {@code getIsHotspot()}，
     * Lombok 已排除此字段以防止 JSON 属性名变化。</p>
     */
    @Getter(AccessLevel.NONE)
    private final boolean hotspot;

    /**
     * 是否需要结果抽检。
     *
     * <p>由质量抽检策略决定，用于标识文章需要人工复核结果质量。
     * getter 保留手写 {@code getRequiresResultVerification()}，Lombok 已排除。</p>
     */
    @Getter(AccessLevel.NONE)
    private final boolean requiresResultVerification;

    /**
     * 置信度标签。
     *
     * <p>反映编译/生成质量评估结果，供管理侧快速判断文章可信度。</p>
     */
    private final String confidence;

    /** 来源文件数（由 sourcePaths.size() 计算）。 */
    private final int sourceCount;

    /** 首个来源文件路径。 */
    private final String primarySourcePath;

    /** 全部来源文件路径列表。 */
    private final List<String> sourcePaths;

    /** 文章关联的明确性关键词列表。 */
    private final List<String> referentialKeywords;

    /** 依赖的文章 conceptId 列表。 */
    private final List<String> dependsOn;

    /** 相关的文章 conceptId 列表。 */
    private final List<String> related;

    /**
     * 扩展元数据 JSON。
     *
     * <p>可能较大，仅用于管理侧展示。不应参与 {@code toString()}。</p>
     */
    private final String metadataJson;

    /**
     * 标题画像（来源标题/切分标题/代表标题/生成模式）。
     *
     * <p>为 {@code null} 时前端降级展示 {@code title} 字段。</p>
     */
    private final AdminArticleTitleProfile titleProfile;

    /**
     * 创建管理侧文章详情响应。
     *
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 正文
     * @param lifecycle 生命周期
     * @param compiledAt 编译时间
     * @param createdAt 首次入库时间
     * @param updatedAt 最近入库时间
     * @param summary 摘要
     * @param reviewStatus 审查状态
     * @param riskLevel 风险等级
     * @param riskReasons 风险原因
     * @param hotspot 是否热点
     * @param requiresResultVerification 是否需要结果抽检
     * @param confidence 置信度
     * @param sourceCount 来源数量
     * @param primarySourcePath 首个来源路径
     * @param sourcePaths 来源路径
     * @param referentialKeywords 明确性关键词
     * @param dependsOn 依赖关系
     * @param related 相关关系
     * @param metadataJson 元数据 JSON
     * @param titleProfile 标题画像
     */
    public AdminArticleDetailResponse(
            Long sourceId,
            String articleKey,
            String conceptId,
            String title,
            String content,
            String lifecycle,
            String compiledAt,
            String createdAt,
            String updatedAt,
            String summary,
            String reviewStatus,
            String riskLevel,
            List<String> riskReasons,
            boolean hotspot,
            boolean requiresResultVerification,
            String confidence,
            int sourceCount,
            String primarySourcePath,
            List<String> sourcePaths,
            List<String> referentialKeywords,
            List<String> dependsOn,
            List<String> related,
            String metadataJson,
            AdminArticleTitleProfile titleProfile
    ) {
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.content = content;
        this.lifecycle = lifecycle;
        this.compiledAt = compiledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.summary = summary;
        this.reviewStatus = reviewStatus;
        this.riskLevel = riskLevel;
        this.riskReasons = riskReasons;
        this.hotspot = hotspot;
        this.requiresResultVerification = requiresResultVerification;
        this.confidence = confidence;
        this.sourceCount = sourceCount;
        this.primarySourcePath = primarySourcePath;
        this.sourcePaths = sourcePaths;
        this.referentialKeywords = referentialKeywords;
        this.dependsOn = dependsOn;
        this.related = related;
        this.metadataJson = metadataJson;
        this.titleProfile = titleProfile;
    }

    /**
     * 返回是否热点文章。
     *
     * <p>基于 usage stats 热度分动态计算。getter 命名为 {@code getIsHotspot()}
     * 以保持 JSON 序列化属性名 {@code "isHotspot"} 不变。</p>
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
