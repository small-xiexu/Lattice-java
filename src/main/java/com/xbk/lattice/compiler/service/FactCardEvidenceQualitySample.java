package com.xbk.lattice.compiler.service;

import com.xbk.lattice.infra.persistence.FactCardRecord;

/**
 * 事实证据卡质量样本
 *
 * 职责：把事实证据卡与审查结果组合为可统计的质量样本
 *
 * @author xiexu
 */
public class FactCardEvidenceQualitySample {

    private final FactCardRecord factCardRecord;

    private final FactCardReviewResult reviewResult;

    /**
     * 创建事实证据卡质量样本。
     *
     * @param factCardRecord 事实证据卡
     * @param reviewResult 审查结果
     */
    public FactCardEvidenceQualitySample(
            FactCardRecord factCardRecord,
            FactCardReviewResult reviewResult
    ) {
        this.factCardRecord = factCardRecord;
        this.reviewResult = reviewResult;
    }

    /**
     * 获取事实证据卡。
     *
     * @return 事实证据卡
     */
    public FactCardRecord getFactCardRecord() {
        return factCardRecord;
    }

    /**
     * 获取审查结果。
     *
     * @return 审查结果
     */
    public FactCardReviewResult getReviewResult() {
        return reviewResult;
    }
}
