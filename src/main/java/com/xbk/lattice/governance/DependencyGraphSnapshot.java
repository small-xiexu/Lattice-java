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

    private final Map<String, String> titlesByNodeId;

    private final Map<String, Long> sourceIdsByNodeId;

    private final Map<String, String> articleKeysByNodeId;

    private final Map<String, String> conceptIdsByNodeId;

    /**
     * 创建依赖图快照。
     *
     * @param edges 传播边
     * @param conceptTitles 概念标题映射
     */
    public DependencyGraphSnapshot(List<DependencyGraphEdge> edges, Map<String, String> conceptTitles) {
        this(edges, conceptTitles, Map.of(), Map.of(), conceptTitles);
    }

    /**
     * 创建 source-aware 依赖图快照。
     *
     * @param edges 传播边
     * @param titlesByNodeId 节点标题映射
     * @param sourceIdsByNodeId 节点资料源映射
     * @param articleKeysByNodeId 节点文章键映射
     * @param conceptIdsByNodeId 节点概念标识映射
     */
    public DependencyGraphSnapshot(
            List<DependencyGraphEdge> edges,
            Map<String, String> titlesByNodeId,
            Map<String, Long> sourceIdsByNodeId,
            Map<String, String> articleKeysByNodeId,
            Map<String, String> conceptIdsByNodeId
    ) {
        this.edges = edges;
        this.titlesByNodeId = titlesByNodeId;
        this.sourceIdsByNodeId = sourceIdsByNodeId;
        this.articleKeysByNodeId = articleKeysByNodeId;
        this.conceptIdsByNodeId = conceptIdsByNodeId;
    }

    public List<DependencyGraphEdge> getEdges() {
        return edges;
    }

    public String findTitle(String nodeId) {
        return titlesByNodeId.get(nodeId);
    }

    public Long findSourceId(String nodeId) {
        return sourceIdsByNodeId.get(nodeId);
    }

    public String findArticleKey(String nodeId) {
        return articleKeysByNodeId.get(nodeId);
    }

    public String findConceptId(String nodeId) {
        String conceptId = conceptIdsByNodeId.get(nodeId);
        return conceptId == null ? nodeId : conceptId;
    }
}
