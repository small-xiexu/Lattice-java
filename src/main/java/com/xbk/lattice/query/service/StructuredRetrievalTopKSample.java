package com.xbk.lattice.query.service;

import java.util.List;

/**
 * 结构化检索 topK 质量样本
 *
 * 职责：承载单个问题的期望证据与实际 topK 命中
 *
 * @author xiexu
 */
public class StructuredRetrievalTopKSample {

    private final String sampleId;

    private final List<StructuredRetrievalTopKTarget> expectedTargets;

    private final List<QueryArticleHit> topKHits;

    /**
     * 创建结构化检索 topK 质量样本。
     *
     * @param sampleId 样本标识
     * @param expectedTargets 期望进入 topK 的目标证据
     * @param topKHits 实际 topK 命中
     */
    public StructuredRetrievalTopKSample(
            String sampleId,
            List<StructuredRetrievalTopKTarget> expectedTargets,
            List<QueryArticleHit> topKHits
    ) {
        this.sampleId = sampleId;
        this.expectedTargets = safeTargets(expectedTargets);
        this.topKHits = safeHits(topKHits);
    }

    /**
     * 获取样本标识。
     *
     * @return 样本标识
     */
    public String getSampleId() {
        return sampleId;
    }

    /**
     * 获取期望进入 topK 的目标证据。
     *
     * @return 目标证据列表
     */
    public List<StructuredRetrievalTopKTarget> getExpectedTargets() {
        return expectedTargets;
    }

    /**
     * 获取实际 topK 命中。
     *
     * @return topK 命中
     */
    public List<QueryArticleHit> getTopKHits() {
        return topKHits;
    }

    /**
     * 安全返回目标证据列表。
     *
     * @param expectedTargets 原始目标证据列表
     * @return 非空目标证据列表
     */
    private List<StructuredRetrievalTopKTarget> safeTargets(
            List<StructuredRetrievalTopKTarget> expectedTargets
    ) {
        if (expectedTargets == null) {
            return List.of();
        }
        return List.copyOf(expectedTargets);
    }

    /**
     * 安全返回 topK 命中列表。
     *
     * @param topKHits 原始 topK 命中
     * @return 非空 topK 命中
     */
    private List<QueryArticleHit> safeHits(List<QueryArticleHit> topKHits) {
        if (topKHits == null) {
            return List.of();
        }
        return List.copyOf(topKHits);
    }
}
