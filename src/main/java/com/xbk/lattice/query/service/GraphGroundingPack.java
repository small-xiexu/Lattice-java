package com.xbk.lattice.query.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 图谱 grounding 包
 *
 * 职责：承载单个图谱实体检索命中的结构化摘要、引用与 facts block
 *
 * @author xiexu
 */
public class GraphGroundingPack {

    private final String entityId;

    private final String canonicalName;

    private final List<String> articleKeys;

    private final List<String> sourcePaths;

    private final String factsBlock;

    private final double score;

    /**
     * 创建图谱 grounding 包。
     *
     * @param entityId 实体标识
     * @param canonicalName 实体全名
     * @param articleKeys 关联文章键
     * @param sourcePaths 来源路径
     * @param factsBlock 图谱 facts block
     * @param score 检索分
     */
    public GraphGroundingPack(
            String entityId,
            String canonicalName,
            List<String> articleKeys,
            List<String> sourcePaths,
            String factsBlock,
            double score
    ) {
        this.entityId = entityId;
        this.canonicalName = canonicalName;
        this.articleKeys = articleKeys == null ? List.of() : new ArrayList<String>(articleKeys);
        this.sourcePaths = sourcePaths == null ? List.of() : new ArrayList<String>(sourcePaths);
        this.factsBlock = factsBlock;
        this.score = score;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public List<String> getArticleKeys() {
        return articleKeys;
    }

    public List<String> getSourcePaths() {
        return sourcePaths;
    }

    public String getFactsBlock() {
        return factsBlock;
    }

    public double getScore() {
        return score;
    }
}
