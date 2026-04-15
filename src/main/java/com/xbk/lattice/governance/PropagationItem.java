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
        this.conceptId = conceptId;
        this.title = title;
        this.depth = depth;
        this.triggers = triggers;
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
