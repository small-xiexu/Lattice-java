package com.xbk.lattice.governance;

/**
 * 依赖图边
 *
 * 职责：表示从上游概念到下游概念的一条传播边
 *
 * @author xiexu
 */
public class DependencyGraphEdge {

    private final String upstreamNodeId;

    private final String downstreamNodeId;

    private final Long upstreamSourceId;

    private final Long downstreamSourceId;

    private final String upstreamArticleKey;

    private final String downstreamArticleKey;

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
        this(
                upstreamConceptId,
                downstreamConceptId,
                null,
                null,
                upstreamConceptId,
                downstreamConceptId,
                upstreamConceptId,
                downstreamConceptId,
                relationType
        );
    }

    /**
     * 创建 source-aware 依赖图边。
     *
     * @param upstreamNodeId 上游节点标识
     * @param downstreamNodeId 下游节点标识
     * @param upstreamSourceId 上游资料源主键
     * @param downstreamSourceId 下游资料源主键
     * @param upstreamArticleKey 上游文章唯一键
     * @param downstreamArticleKey 下游文章唯一键
     * @param upstreamConceptId 上游概念标识
     * @param downstreamConceptId 下游概念标识
     * @param relationType 关系类型
     */
    public DependencyGraphEdge(
            String upstreamNodeId,
            String downstreamNodeId,
            Long upstreamSourceId,
            Long downstreamSourceId,
            String upstreamArticleKey,
            String downstreamArticleKey,
            String upstreamConceptId,
            String downstreamConceptId,
            String relationType
    ) {
        this.upstreamNodeId = upstreamNodeId;
        this.downstreamNodeId = downstreamNodeId;
        this.upstreamSourceId = upstreamSourceId;
        this.downstreamSourceId = downstreamSourceId;
        this.upstreamArticleKey = upstreamArticleKey;
        this.downstreamArticleKey = downstreamArticleKey;
        this.upstreamConceptId = upstreamConceptId;
        this.downstreamConceptId = downstreamConceptId;
        this.relationType = relationType;
    }

    public String getUpstreamNodeId() {
        return upstreamNodeId;
    }

    public String getDownstreamNodeId() {
        return downstreamNodeId;
    }

    public Long getUpstreamSourceId() {
        return upstreamSourceId;
    }

    public Long getDownstreamSourceId() {
        return downstreamSourceId;
    }

    public String getUpstreamArticleKey() {
        return upstreamArticleKey;
    }

    public String getDownstreamArticleKey() {
        return downstreamArticleKey;
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
