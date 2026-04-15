package com.xbk.lattice.governance;

import java.util.List;
import java.util.Map;

/**
 * 依赖图快照
 *
 * 职责：承载当前文章集合对应的传播边与概念标题
 *
 * @author xiexu
 */
public class DependencyGraphSnapshot {

    private final List<DependencyGraphEdge> edges;

    private final Map<String, String> conceptTitles;

    /**
     * 创建依赖图快照。
     *
     * @param edges 传播边
     * @param conceptTitles 概念标题映射
     */
    public DependencyGraphSnapshot(List<DependencyGraphEdge> edges, Map<String, String> conceptTitles) {
        this.edges = edges;
        this.conceptTitles = conceptTitles;
    }

    public List<DependencyGraphEdge> getEdges() {
        return edges;
    }

    public String findTitle(String conceptId) {
        return conceptTitles.get(conceptId);
    }
}
