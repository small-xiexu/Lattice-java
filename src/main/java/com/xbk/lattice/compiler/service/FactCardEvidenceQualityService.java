package com.xbk.lattice.compiler.service;

import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 事实证据卡质量服务
 *
 * 职责：统计证据层 source 回指、状态解释与问题原因覆盖指标
 *
 * @author xiexu
 */
@Service
public class FactCardEvidenceQualityService {

    /**
     * 度量事实证据卡质量。
     *
     * @param samples 质量样本列表
     * @return 质量报告
     */
    public FactCardEvidenceQualityReport measure(List<FactCardEvidenceQualitySample> samples) {
        int totalCount = 0;
        int sourceReferencePassedCount = 0;
        int statusExplainedCount = 0;
        int issueCount = 0;
        int issueWithReasonCount = 0;

        for (FactCardEvidenceQualitySample sample : safeSamples(samples)) {
            totalCount++;
            FactCardRecord factCardRecord = sample.getFactCardRecord();
            FactCardReviewResult reviewResult = sample.getReviewResult();
            if (isSourceReferencePassed(factCardRecord, reviewResult)) {
                sourceReferencePassedCount++;
            }
            if (isStatusExplained(reviewResult)) {
                statusExplainedCount++;
            }
            if (isIssue(reviewResult)) {
                issueCount++;
                if (hasReasons(reviewResult)) {
                    issueWithReasonCount++;
                }
            }
        }

        return new FactCardEvidenceQualityReport(
                totalCount,
                sourceReferencePassedCount,
                statusExplainedCount,
                issueCount,
                issueWithReasonCount
        );
    }

    /**
     * 安全返回样本列表。
     *
     * @param samples 原始样本列表
     * @return 非空样本列表
     */
    private List<FactCardEvidenceQualitySample> safeSamples(List<FactCardEvidenceQualitySample> samples) {
        if (samples == null) {
            return List.of();
        }
        return samples;
    }

    /**
     * 判断 source 回指是否通过。
     *
     * @param factCardRecord 事实证据卡
     * @param reviewResult 审查结果
     * @return 通过返回 true
     */
    private boolean isSourceReferencePassed(FactCardRecord factCardRecord, FactCardReviewResult reviewResult) {
        if (factCardRecord == null || factCardRecord.getSourceChunkIds().isEmpty()) {
            return false;
        }
        return reviewStatus(reviewResult) != FactCardReviewStatus.LOW_CONFIDENCE;
    }

    /**
     * 判断审查状态是否可解释。
     *
     * @param reviewResult 审查结果
     * @return 可解释返回 true
     */
    private boolean isStatusExplained(FactCardReviewResult reviewResult) {
        FactCardReviewStatus reviewStatus = reviewStatus(reviewResult);
        if (reviewStatus == null) {
            return false;
        }
        if (reviewStatus == FactCardReviewStatus.VALID) {
            return true;
        }
        return hasReasons(reviewResult);
    }

    /**
     * 判断是否属于问题状态。
     *
     * @param reviewResult 审查结果
     * @return 问题状态返回 true
     */
    private boolean isIssue(FactCardReviewResult reviewResult) {
        FactCardReviewStatus reviewStatus = reviewStatus(reviewResult);
        return reviewStatus == null || reviewStatus != FactCardReviewStatus.VALID;
    }

    /**
     * 判断审查结果是否带原因。
     *
     * @param reviewResult 审查结果
     * @return 带原因返回 true
     */
    private boolean hasReasons(FactCardReviewResult reviewResult) {
        return reviewResult != null && !reviewResult.getReasons().isEmpty();
    }

    /**
     * 读取审查状态。
     *
     * @param reviewResult 审查结果
     * @return 审查状态
     */
    private FactCardReviewStatus reviewStatus(FactCardReviewResult reviewResult) {
        if (reviewResult == null) {
            return null;
        }
        return reviewResult.getReviewStatus();
    }
}
