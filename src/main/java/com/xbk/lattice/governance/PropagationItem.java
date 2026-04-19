package com.xbk.lattice.governance;

import java.util.List;

/**
 * 传播影响项
 *
 * 职责：表示一篇受上游纠错影响的下游文章
 *
 * @author xiexu
 */
public class PropagationItem {

    private final Long sourceId;

    private final String articleKey;

    private final String conceptId;

    private final String title;

    private final int depth;

    private final List<String> triggers;

    /**
     * 创建传播影响项。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param depth 传播深度
     * @param triggers 触发关系
     */
    public PropagationItem(String conceptId, String title, int depth, List<String> triggers) {
        this(null, conceptId, conceptId, title, depth, triggers);
    }

    /**
     * 创建 source-aware 传播影响项。
     *
     * @param sourceId 资料源主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param depth 传播深度
     * @param triggers 触发关系
     */
    public PropagationItem(
            Long sourceId,
            String articleKey,
            String conceptId,
            String title,
            int depth,
            List<String> triggers
    ) {
        this.sourceId = sourceId;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
        this.title = title;
        this.depth = depth;
        this.triggers = triggers;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getArticleKey() {
        return articleKey;
    }

    public String getConceptId() {
        return conceptId;
    }

    public String getTitle() {
        return title;
    }

    public int getDepth() {
        return depth;
    }

    public List<String> getTriggers() {
        return triggers;
    }
}
