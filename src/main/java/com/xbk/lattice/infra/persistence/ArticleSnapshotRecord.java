package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 文章快照记录
 *
 * 职责：表示 articles 表自动留痕后的单次文章快照
 *
 * @author xiexu
 */
public class ArticleSnapshotRecord {

    private final long snapshotId;

    private final Long sourceId;

    private final String articleKey;

    private final String conceptId;

    private final String title;

    private final String content;

    private final String lifecycle;

    private final OffsetDateTime compiledAt;

    private final List<String> sourcePaths;

    private final String metadataJson;

    private final String summary;

    private final List<String> referentialKeywords;

    private final List<String> dependsOn;

    private final List<String> related;

    private final String confidence;

    private final String reviewStatus;

    private final String snapshotReason;

    private final OffsetDateTime capturedAt;

    /**
     * 创建文章快照记录。
     *
     * @param snapshotId 快照标识
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 正文
     * @param lifecycle 生命周期
     * @param compiledAt 文章编译时间
     * @param sourcePaths 来源路径
     * @param metadataJson 元数据 JSON
     * @param summary 摘要
     * @param referentialKeywords 参照关键词
     * @param dependsOn 上游依赖
     * @param related 相关概念
     * @param confidence 置信度
     * @param reviewStatus 审查状态
     * @param snapshotReason 快照原因
     * @param capturedAt 快照留痕时间
     */
    public ArticleSnapshotRecord(
            long snapshotId,
            Long sourceId,
            String articleKey,
            String conceptId,
            String title,
            String content,
            String lifecycle,
            OffsetDateTime compiledAt,
            List<String> sourcePaths,
            String metadataJson,
            String summary,
            List<String> referentialKeywords,
            List<String> dependsOn,
            List<String> related,
            String confidence,
            String reviewStatus,
            String snapshotReason,
            OffsetDateTime capturedAt
    ) {
        this.snapshotId = snapshotId;
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.content = content;
        this.lifecycle = lifecycle;
        this.compiledAt = compiledAt;
        this.sourcePaths = sourcePaths;
        this.metadataJson = metadataJson;
        this.summary = summary;
        this.referentialKeywords = referentialKeywords;
        this.dependsOn = dependsOn;
        this.related = related;
        this.confidence = confidence;
        this.reviewStatus = reviewStatus;
        this.snapshotReason = snapshotReason;
        this.capturedAt = capturedAt;
    }

    /**
     * 创建兼容旧调用的文章快照记录。
     *
     * @param snapshotId 快照标识
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 正文
     * @param lifecycle 生命周期
     * @param compiledAt 编译时间
     * @param sourcePaths 来源路径
     * @param metadataJson 元数据 JSON
     * @param summary 摘要
     * @param referentialKeywords 参照关键词
     * @param dependsOn 上游依赖
     * @param related 相关概念
     * @param confidence 置信度
     * @param reviewStatus 审查状态
     * @param snapshotReason 快照原因
     * @param capturedAt 快照时间
     */
    public ArticleSnapshotRecord(
            long snapshotId,
            String conceptId,
            String title,
            String content,
            String lifecycle,
            OffsetDateTime compiledAt,
            List<String> sourcePaths,
            String metadataJson,
            String summary,
            List<String> referentialKeywords,
            List<String> dependsOn,
            List<String> related,
            String confidence,
            String reviewStatus,
            String snapshotReason,
            OffsetDateTime capturedAt
    ) {
        this(
                snapshotId,
                null,
                conceptId,
                conceptId,
                title,
                content,
                lifecycle,
                compiledAt,
                sourcePaths,
                metadataJson,
                summary,
                referentialKeywords,
                dependsOn,
                related,
                confidence,
                reviewStatus,
                snapshotReason,
                capturedAt
        );
    }

    /**
     * 获取快照标识。
     *
     * @return 快照标识
     */
    public long getSnapshotId() {
        return snapshotId;
    }

    /**
     * 获取资料源主键。
     *
     * @return 资料源主键
     */
    public Long getSourceId() {
        return sourceId;
    }

    /**
     * 获取文章唯一键。
     *
     * @return 文章唯一键
     */
    public String getArticleKey() {
        return articleKey;
    }

    /**
     * 获取概念标识。
     *
     * @return 概念标识
     */
    public String getConceptId() {
        return conceptId;
    }

    /**
     * 获取标题。
     *
     * @return 标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 获取正文。
     *
     * @return 正文
     */
    public String getContent() {
        return content;
    }

    /**
     * 获取生命周期。
     *
     * @return 生命周期
     */
    public String getLifecycle() {
        return lifecycle;
    }

    /**
     * 获取文章编译时间。
     *
     * @return 编译时间
     */
    public OffsetDateTime getCompiledAt() {
        return compiledAt;
    }

    /**
     * 获取来源路径。
     *
     * @return 来源路径
     */
    public List<String> getSourcePaths() {
        return sourcePaths;
    }

    /**
     * 获取元数据 JSON。
     *
     * @return 元数据 JSON
     */
    public String getMetadataJson() {
        return metadataJson;
    }

    /**
     * 获取摘要。
     *
     * @return 摘要
     */
    public String getSummary() {
        return summary;
    }

    /**
     * 获取参照关键词。
     *
     * @return 参照关键词
     */
    public List<String> getReferentialKeywords() {
        return referentialKeywords;
    }

    /**
     * 获取上游依赖。
     *
     * @return 上游依赖
     */
    public List<String> getDependsOn() {
        return dependsOn;
    }

    /**
     * 获取相关概念。
     *
     * @return 相关概念
     */
    public List<String> getRelated() {
        return related;
    }

    /**
     * 获取置信度。
     *
     * @return 置信度
     */
    public String getConfidence() {
        return confidence;
    }

    /**
     * 获取审查状态。
     *
     * @return 审查状态
     */
    public String getReviewStatus() {
        return reviewStatus;
    }

    /**
     * 获取快照原因。
     *
     * @return 快照原因
     */
    public String getSnapshotReason() {
        return snapshotReason;
    }

    /**
     * 获取快照留痕时间。
     *
     * @return 快照留痕时间
     */
    public OffsetDateTime getCapturedAt() {
        return capturedAt;
    }

    /**
     * 根据文章记录创建快照。
     *
     * @param articleRecord 文章记录
     * @param snapshotReason 快照原因
     * @param capturedAt 快照时间
     * @return 快照记录
     */
    public static ArticleSnapshotRecord fromArticle(
            ArticleRecord articleRecord,
            String snapshotReason,
            OffsetDateTime capturedAt
    ) {
        return new ArticleSnapshotRecord(
                -1L,
                articleRecord.getSourceId(),
                articleRecord.getArticleKey(),
                articleRecord.getConceptId(),
                articleRecord.getTitle(),
                articleRecord.getContent(),
                articleRecord.getLifecycle(),
                articleRecord.getCompiledAt(),
                articleRecord.getSourcePaths(),
                articleRecord.getMetadataJson(),
                articleRecord.getSummary(),
                articleRecord.getReferentialKeywords(),
                articleRecord.getDependsOn(),
                articleRecord.getRelated(),
                articleRecord.getConfidence(),
                articleRecord.getReviewStatus(),
                snapshotReason,
                capturedAt
        );
    }
}
