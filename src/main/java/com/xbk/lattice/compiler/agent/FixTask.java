package com.xbk.lattice.compiler.agent;

import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.query.domain.ReviewIssue;

import java.util.List;

/**
 * FixerAgent 输入任务
 *
 * 职责：承载文章修复所需的草稿、问题列表与来源内容
 *
 * @author xiexu
 */
public class FixTask {

    private final ArticleRecord articleRecord;

    private final List<ReviewIssue> reviewIssues;

    private final String sourceContents;

    private final String scopeId;

    private final String scene;

    /**
     * 创建 FixerAgent 输入任务。
     *
     * @param articleRecord 草稿文章
     * @param reviewIssues 审查问题
     * @param sourceContents 来源正文
     */
    public FixTask(ArticleRecord articleRecord, List<ReviewIssue> reviewIssues, String sourceContents) {
        this(articleRecord, reviewIssues, sourceContents, null, null);
    }

    /**
     * 创建 FixerAgent 输入任务。
     *
     * @param articleRecord 草稿文章
     * @param reviewIssues 审查问题
     * @param sourceContents 来源正文
     * @param scopeId 作用域标识
     * @param scene 场景
     */
    public FixTask(
            ArticleRecord articleRecord,
            List<ReviewIssue> reviewIssues,
            String sourceContents,
            String scopeId,
            String scene
    ) {
        this.articleRecord = articleRecord;
        this.reviewIssues = reviewIssues;
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
     * 返回审查问题。
     *
     * @return 审查问题
     */
    public List<ReviewIssue> getReviewIssues() {
        return reviewIssues;
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
