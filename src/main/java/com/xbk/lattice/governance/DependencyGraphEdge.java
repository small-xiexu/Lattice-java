package com.xbk.lattice.governance;

/**
 * 依赖图边
 *
 * 职责：表示从上游概念到下游概念的一条传播边
 *
 * @author xiexu
 */
public class DependencyGraphEdge {

    private final String upstreamConceptId;

    private final String downstreamConceptId;

    private final String relationType;

    /**
     * 创建依赖图边。
     *
     * @param upstreamConceptId 上游概念
     * @param downstreamConceptId 下游概念
     * @param relationType 关系类型
     */
    public DependencyGraphEdge(String upstreamConceptId, String downstreamConceptId, String relationType) {
        this.upstreamConceptId = upstreamConceptId;
        this.downstreamConceptId = downstreamConceptId;
        this.relationType = relationType;
    }

    public String getUpstreamConceptId() {
        return upstreamConceptId;
    }

    public String getDownstreamConceptId() {
        return downstreamConceptId;
    }

    public String getRelationType() {
        return relationType;
    }
}
