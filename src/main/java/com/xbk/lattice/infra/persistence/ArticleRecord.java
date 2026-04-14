package com.xbk.lattice.infra.persistence;

import java.time.OffsetDateTime;

/**
 * 文章记录
 *
 * 职责：表示最小文章落盘对象
 *
 * @author xiexu
 */
public class ArticleRecord {

    private final String conceptId;

    private final String title;

    private final String content;

    private final String lifecycle;

    private final OffsetDateTime compiledAt;

    /**
     * 创建文章记录。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @param lifecycle 生命周期
     * @param compiledAt 编译时间
     */
    public ArticleRecord(String conceptId, String title, String content, String lifecycle, OffsetDateTime compiledAt) {
        this.conceptId = conceptId;
        this.title = title;
        this.content = content;
        this.lifecycle = lifecycle;
        this.compiledAt = compiledAt;
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
     * 获取内容。
     *
     * @return 内容
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
     * 获取编译时间。
     *
     * @return 编译时间
     */
    public OffsetDateTime getCompiledAt() {
        return compiledAt;
    }
}
