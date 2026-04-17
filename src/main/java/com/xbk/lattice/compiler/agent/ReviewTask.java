package com.xbk.lattice.compiler.agent;

import com.xbk.lattice.infra.persistence.ArticleRecord;

/**
 * ReviewerAgent 输入任务
 *
 * 职责：承载文章审查所需的草稿与来源内容
 *
 * @author xiexu
 */
public class ReviewTask {

    private final ArticleRecord articleRecord;

    private final String sourceContents;

    private final String scopeId;

    private final String scene;

    /**
     * 创建 ReviewerAgent 输入任务。
     *
     * @param articleRecord 草稿文章
     * @param sourceContents 来源正文
     */
    public ReviewTask(ArticleRecord articleRecord, String sourceContents) {
        this(articleRecord, sourceContents, null, null);
    }

    /**
     * 创建 ReviewerAgent 输入任务。
     *
     * @param articleRecord 草稿文章
     * @param sourceContents 来源正文
     * @param scopeId 作用域标识
     * @param scene 场景
     */
    public ReviewTask(ArticleRecord articleRecord, String sourceContents, String scopeId, String scene) {
        this.articleRecord = articleRecord;
        this.sourceContents = sourceContents;
        this.scopeId = scopeId;
        this.scene = scene;
    }

    /**
     * 返回草稿文章。
     *
     * @return 草稿文章
     */
    public ArticleRecord getArticleRecord() {
        return articleRecord;
    }

    /**
     * 返回来源正文。
     *
     * @return 来源正文
     */
    public String getSourceContents() {
        return sourceContents;
    }

    /**
     * 返回作用域标识。
     *
     * @return 作用域标识
     */
    public String getScopeId() {
        return scopeId;
    }

    /**
     * 返回场景。
     *
     * @return 场景
     */
    public String getScene() {
        return scene;
    }
}
