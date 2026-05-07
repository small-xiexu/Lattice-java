package com.xbk.lattice.infra.persistence;

/**
 * 源文件关联文章键查询行
 *
 * 职责：承载 article_source_refs 按源文件查询后的轻量映射结果
 *
 * @author xiexu
 */
public class ArticleSourceFileArticleKeyRow {

    private final Long sourceFileId;

    private final String articleKey;

    /**
     * 创建源文件关联文章键查询行。
     *
     * @param sourceFileId 源文件主键
     * @param articleKey 文章唯一键
     */
    public ArticleSourceFileArticleKeyRow(Long sourceFileId, String articleKey) {
        this.sourceFileId = sourceFileId;
        this.articleKey = articleKey;
    }

    /**
     * 获取源文件主键。
     *
     * @return 源文件主键
     */
    public Long getSourceFileId() {
        return sourceFileId;
    }

    /**
     * 获取文章唯一键。
     *
     * @return 文章唯一键
     */
    public String getArticleKey() {
        return articleKey;
    }
}
