package com.xbk.lattice.query.service;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 结构化检索 topK 质量服务
 *
 * 职责：统计结构化问题的目标证据进入 topK 比例
 *
 * @author xiexu
 */
@Service
public class StructuredRetrievalTopKQualityService {

    /**
     * 度量结构化检索 topK 质量。
     *
     * @param samples 质量样本列表
     * @return topK 质量报告
     */
    public StructuredRetrievalTopKReport measure(List<StructuredRetrievalTopKSample> samples) {
        int sampleCount = 0;
        int targetCount = 0;
        int targetHitCount = 0;
        int fullyCoveredSampleCount = 0;

        for (StructuredRetrievalTopKSample sample : safeSamples(samples)) {
            sampleCount++;
            int sampleTargetCount = 0;
            int sampleHitCount = 0;
            for (StructuredRetrievalTopKTarget target : sample.getExpectedTargets()) {
                if (!isUsableTarget(target)) {
                    continue;
                }
                sampleTargetCount++;
                targetCount++;
                if (containsTarget(sample.getTopKHits(), target)) {
                    sampleHitCount++;
                    targetHitCount++;
                }
            }
            if (sampleTargetCount > 0 && sampleHitCount == sampleTargetCount) {
                fullyCoveredSampleCount++;
            }
        }

        return new StructuredRetrievalTopKReport(
                sampleCount,
                targetCount,
                targetHitCount,
                fullyCoveredSampleCount
        );
    }

    /**
     * 安全返回样本列表。
     *
     * @param samples 原始样本列表
     * @return 非空样本列表
     */
    private List<StructuredRetrievalTopKSample> safeSamples(List<StructuredRetrievalTopKSample> samples) {
        if (samples == null) {
            return List.of();
        }
        return samples;
    }

    /**
     * 判断目标证据是否可用于指标统计。
     *
     * @param target 目标证据
     * @return 可用返回 true
     */
    private boolean isUsableTarget(StructuredRetrievalTopKTarget target) {
        return target != null && target.getEvidenceType() != null && target.hasIdentity();
    }

    /**
     * 判断 topK 命中中是否包含目标证据。
     *
     * @param topKHits topK 命中
     * @param target 目标证据
     * @return 包含返回 true
     */
    private boolean containsTarget(List<QueryArticleHit> topKHits, StructuredRetrievalTopKTarget target) {
        for (QueryArticleHit topKHit : topKHits) {
            if (target.matches(topKHit)) {
                return true;
            }
        }
        return false;
    }
}
