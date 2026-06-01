package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧 Fact Card 条目响应。
 *
 * <p>承载结构化证据卡的完整展示字段——含标识、类型、结论、证据和审查状态，
 * 由 {@code AdminFactCardController} 组装返回。
 * 含大文本字段（{@code itemsJson}、{@code evidenceText}），禁止引入 {@code @Data}。
 *
 * @author xiexu
 */
@Getter
public class AdminFactCardItemResponse {

    /** 数据库主键。 */
    private final Long id;

    /**
     * 稳定标识（跨 source 唯一）。
     *
     * <p>基于事实内容哈希生成，不受数据库主键变更影响。</p>
     */
    private final String cardId;

    /** 资料源主键。为 {@code null} 表示无关联 source。 */
    private final Long sourceId;

    /** 源文件主键。为 {@code null} 表示无关联文件。 */
    private final Long sourceFileId;

    /** 源文件路径。 */
    private final String sourceFilePath;

    /**
     * Fact Card 类型（枚举名，如 {@code STRUCTURED_ITEM} / {@code SUMMARY}）。
     *
     * <p>驱动前端展示样式——不同类型使用不同的卡片布局。</p>
     */
    private final String cardType;

    /**
     * 答案形态（枚举名）。
     *
     * <p>影响卡片的回答呈现方式（如单值/列表/表格）。</p>
     */
    private final String answerShape;

    /** 卡片标题。 */
    private final String title;

    /** 事实结论文本。 */
    private final String claim;

    /**
     * 结构化证据条目 JSON。
     *
     * <p>可能较大，包含结构化的事实条目数据。仅用于管理侧展示，禁止参与 {@code toString()}。</p>
     */
    private final String itemsJson;

    /**
     * 证据文本全文。
     *
     * <p>可能为长文本，包含支撑该事实的原始证据段落。仅用于管理侧预览，禁止参与 {@code toString()}。</p>
     */
    private final String evidenceText;

    /** 关联的 source chunk 主键列表。 */
    private final List<Long> sourceChunkIds;

    /** 关联的文章主键列表。 */
    private final List<Long> articleIds;

    /**
     * 置信度（0.0–1.0）。
     *
     * <p>反映事实抽取和验证的可信程度。</p>
     */
    private final double confidence;

    /**
     * 审查状态（数据库值，非枚举名）。
     *
     * <p>可选值：{@code accepted} / {@code needs_human_review} / {@code published}。
     * 驱动前端展示审查标签。</p>
     */
    private final String reviewStatus;

    /**
     * 内容哈希。
     *
     * <p>基于卡片关键字段计算，用于检测内容变更（如重新编译后事实是否变化）。</p>
     */
    private final String contentHash;

    /** 创建时间（ISO-8601 字符串）。 */
    private final String createdAt;

    /** 最后更新时间（ISO-8601 字符串）。 */
    private final String updatedAt;

    /**
     * 创建管理侧 Fact Card 条目响应。
     *
     * @param id 主键
     * @param cardId 稳定标识
     * @param sourceId 资料源主键
     * @param sourceFileId 源文件主键
     * @param sourceFilePath 源文件路径
     * @param cardType 卡类型
     * @param answerShape 答案形态
     * @param title 标题
     * @param claim 结论
     * @param itemsJson 结构化条目 JSON
     * @param evidenceText 证据文本
     * @param sourceChunkIds source chunk 主键
     * @param articleIds article 主键
     * @param confidence 置信度
     * @param reviewStatus 审查状态
     * @param contentHash 内容哈希
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public AdminFactCardItemResponse(
            Long id,
            String cardId,
            Long sourceId,
            Long sourceFileId,
            String sourceFilePath,
            String cardType,
            String answerShape,
            String title,
            String claim,
            String itemsJson,
            String evidenceText,
            List<Long> sourceChunkIds,
            List<Long> articleIds,
            double confidence,
            String reviewStatus,
            String contentHash,
            String createdAt,
            String updatedAt
    ) {
        this.id = id;
        this.cardId = cardId;
        this.sourceId = sourceId;
        this.sourceFileId = sourceFileId;
        this.sourceFilePath = sourceFilePath;
        this.cardType = cardType;
        this.answerShape = answerShape;
        this.title = title;
        this.claim = claim;
        this.itemsJson = itemsJson;
        this.evidenceText = evidenceText;
        this.sourceChunkIds = sourceChunkIds;
        this.articleIds = articleIds;
        this.confidence = confidence;
        this.reviewStatus = reviewStatus;
        this.contentHash = contentHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
