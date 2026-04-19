package com.xbk.lattice.infra.persistence;

/**
 * 文章来源关联记录
 *
 * 职责：表示 article -> source_file 的来源溯源关联
 *
 * @author xiexu
 */
public class ArticleSourceRefRecord {

    private final String articleKey;

    private final Long sourceId;

    private final Long sourceFileId;

    private final String refType;

    private final String refLabel;

    /**
     * 创建文章来源关联记录。
     *
     * @param articleKey 文章唯一键
     * @param sourceId 资料源主键
     * @param sourceFileId 源文件主键
     * @param refType 关联类型
     * @param refLabel 展示标签
     */
    public ArticleSourceRefRecord(
            String articleKey,
            Long sourceId,
            Long sourceFileId,
            String refType,
            String refLabel
    ) {
        this.articleKey = articleKey;
        this.sourceId = sourceId;
        this.sourceFileId = sourceFileId;
        this.refType = refType;
        this.refLabel = refLabel;
    }

    /**
     * 返回文章唯一键。
     *
     * @return 文章唯一键
     */
    public String getArticleKey() {
        return articleKey;
    }

    /**
     * 返回资料源主键。
     *
     * @return 资料源主键
     */
    public Long getSourceId() {
        return sourceId;
    }

    /**
     * 返回源文件主键。
     *
     * @return 源文件主键
     */
    public Long getSourceFileId() {
        return sourceFileId;
    }

    /**
     * 返回关联类型。
     *
     * @return 关联类型
     */
    public String getRefType() {
        return refType;
    }

    /**
     * 返回展示标签。
     *
     * @return 展示标签
     */
    public String getRefLabel() {
        return refLabel;
    }
}
