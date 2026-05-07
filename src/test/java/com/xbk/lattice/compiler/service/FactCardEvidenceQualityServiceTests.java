package com.xbk.lattice.compiler.service;

import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 事实证据卡质量服务测试
 *
 * 职责：验证证据层 source 回指、状态解释与问题原因覆盖指标
 *
 * @author xiexu
 */
class FactCardEvidenceQualityServiceTests {

    private final FactCardEvidenceQualityService qualityService = new FactCardEvidenceQualityService();

    /**
     * 验证正常证据集合可通过 B2 指标门槛。
     */
    @Test
    void shouldPassEvidenceLayerMetricGate() {
        List<FactCardEvidenceQualitySample> samples = List.of(
                validSample(FactCardType.FACT_ENUM, AnswerShape.ENUM),
                validSample(FactCardType.FACT_COMPARE, AnswerShape.COMPARE),
                validSample(FactCardType.FACT_SEQUENCE, AnswerShape.SEQUENCE),
                validSample(FactCardType.FACT_STATUS, AnswerShape.STATUS),
                validSample(FactCardType.FACT_POLICY, AnswerShape.POLICY)
        );

        FactCardEvidenceQualityReport report = qualityService.measure(samples);

        assertThat(report.getTotalCount()).isEqualTo(5);
        assertThat(report.getSourceReferencePassRate()).isEqualTo(1.0D);
        assertThat(report.getStatusExplainabilityRate()).isEqualTo(1.0D);
        assertThat(report.getIssueReasonCoverageRate()).isEqualTo(1.0D);
        assertThat(report.passesGate(0.90D, 1.0D, 1.0D)).isTrue();
    }

    /**
     * 验证低置信、冲突、缺边界状态均带可解释原因。
     */
    @Test
    void shouldExplainBlockingReviewStatuses() {
        List<FactCardEvidenceQualitySample> samples = List.of(
                issueSample(FactCardReviewStatus.LOW_CONFIDENCE, "evidenceText 未能在 source chunk 中定位"),
                issueSample(FactCardReviewStatus.CONFLICT, "状态卡存在互斥状态冲突"),
                issueSample(FactCardReviewStatus.INCOMPLETE, "规则卡缺少适用范围")
        );

        FactCardEvidenceQualityReport report = qualityService.measure(samples);

        assertThat(report.getIssueCount()).isEqualTo(3);
        assertThat(report.getIssueWithReasonCount()).isEqualTo(3);
        assertThat(report.getStatusExplainabilityRate()).isEqualTo(1.0D);
        assertThat(report.getIssueReasonCoverageRate()).isEqualTo(1.0D);
    }

    /**
     * 验证问题状态缺少原因时不能通过解释性门槛。
     */
    @Test
    void shouldFailExplainabilityGateWhenIssueReasonMissing() {
        FactCardEvidenceQualitySample sample = new FactCardEvidenceQualitySample(
                factCard(FactCardType.FACT_STATUS, AnswerShape.STATUS, List.of(10L)),
                FactCardReviewResult.of(FactCardReviewStatus.CONFLICT, List.of())
        );

        FactCardEvidenceQualityReport report = qualityService.measure(List.of(sample));

        assertThat(report.getStatusExplainabilityRate()).isEqualTo(0.0D);
        assertThat(report.getIssueReasonCoverageRate()).isEqualTo(0.0D);
        assertThat(report.passesGate(0.90D, 1.0D, 1.0D)).isFalse();
    }

    /**
     * 创建有效质量样本。
     *
     * @param factCardType 事实卡类型
     * @param answerShape 答案形态
     * @return 质量样本
     */
    private FactCardEvidenceQualitySample validSample(FactCardType factCardType, AnswerShape answerShape) {
        return new FactCardEvidenceQualitySample(
                factCard(factCardType, answerShape, List.of(10L)),
                FactCardReviewResult.valid()
        );
    }

    /**
     * 创建问题状态质量样本。
     *
     * @param reviewStatus 审查状态
     * @param reason 审查原因
     * @return 质量样本
     */
    private FactCardEvidenceQualitySample issueSample(FactCardReviewStatus reviewStatus, String reason) {
        return new FactCardEvidenceQualitySample(
                factCard(FactCardType.FACT_STATUS, AnswerShape.STATUS, List.of(10L)),
                FactCardReviewResult.of(reviewStatus, List.of(reason))
        );
    }

    /**
     * 构造事实证据卡。
     *
     * @param factCardType 事实卡类型
     * @param answerShape 答案形态
     * @param sourceChunkIds source chunk 主键
     * @return 事实证据卡
     */
    private FactCardRecord factCard(
            FactCardType factCardType,
            AnswerShape answerShape,
            List<Long> sourceChunkIds
    ) {
        return new FactCardRecord(
                "fact-card-quality-" + factCardType.name().toLowerCase(),
                1L,
                2L,
                factCardType,
                answerShape,
                "质量指标样本",
                "质量指标结论",
                "{\"items\":[{\"label\":\"alpha\",\"value\":\"enabled\"}]}",
                "alpha: enabled",
                sourceChunkIds,
                List.of(),
                0.91D,
                FactCardReviewStatus.VALID,
                "hash-" + factCardType.name().toLowerCase()
        );
    }
}
