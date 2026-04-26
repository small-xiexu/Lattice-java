package com.xbk.lattice.query.deepresearch;

import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.evidence.domain.AnswerProjection;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchorSourceType;
import com.xbk.lattice.query.evidence.domain.FactFinding;
import com.xbk.lattice.query.evidence.domain.FactValueType;
import com.xbk.lattice.query.evidence.domain.FindingSupportLevel;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import com.xbk.lattice.query.evidence.domain.ProjectionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EvidenceLedger 测试
 *
 * 职责：验证 v2.6 的 finding merge 与冲突判定规则
 *
 * @author xiexu
 */
class EvidenceLedgerTests {

    /**
     * 验证同一 factKey 且值相同的 finding 会合并 anchor 集合。
     */
    @Test
    void shouldMergeFindingsByFactKeyAndValueIdentity() {
        EvidenceLedger evidenceLedger = new EvidenceLedger();
        evidenceLedger.addFactFinding(factFinding(
                "payment.retry.maxAttempts.current_config",
                "5",
                "times",
                "ev#1"
        ));
        evidenceLedger.addFactFinding(factFinding(
                "payment.retry.maxAttempts.current_config",
                "5",
                "times",
                "ev#2"
        ));

        assertThat(evidenceLedger.hasConflicts()).isFalse();
        assertThat(evidenceLedger.getFindingsByFactKey()).containsKey("payment.retry.maxAttempts.current_config");
        assertThat(evidenceLedger.getFindingsByFactKey().get("payment.retry.maxAttempts.current_config")).hasSize(1);
        assertThat(evidenceLedger.getFindingsByFactKey().get("payment.retry.maxAttempts.current_config").get(0).getAnchorIds())
                .containsExactly("ev#1", "ev#2");
    }

    /**
     * 验证同一 factKey 但值不同的 finding 会被标记为冲突。
     */
    @Test
    void shouldDetectConflictsForSameFactKeyDifferentValues() {
        EvidenceLedger evidenceLedger = new EvidenceLedger();
        evidenceLedger.addFactFinding(factFinding(
                "payment.retry.maxAttempts.current_config",
                "5",
                "times",
                "ev#1"
        ));
        evidenceLedger.addFactFinding(factFinding(
                "payment.retry.maxAttempts.current_config",
                "3",
                "times",
                "ev#2"
        ));

        assertThat(evidenceLedger.hasConflicts()).isTrue();
        assertThat(evidenceLedger.getConflicts()).containsKey("payment.retry.maxAttempts.current_config");
        assertThat(evidenceLedger.getConflicts().get("payment.retry.maxAttempts.current_config"))
                .containsExactlyInAnyOrder("5|times", "3|times");
    }

    /**
     * 验证证据卡中的 v2.6 结构化 finding/anchor 会优先进入 ledger，并且内部锚点不会生成出站 projection 候选。
     */
    @Test
    void shouldPreferStructuredFindingsAndOnlyProjectArticleOrSourceAnchors() {
        EvidenceCard evidenceCard = new EvidenceCard();
        evidenceCard.setEvidenceId("ev#1");
        evidenceCard.setTaskId("task-1");
        evidenceCard.getEvidenceAnchors().add(articleAnchor("ev#1", "payment-routing"));
        evidenceCard.getEvidenceAnchors().add(graphAnchor("ev#2", "payment.retry.maxAttempts.current_config"));
        evidenceCard.getFactFindings().add(factFinding(
                "payment.retry.maxAttempts.current_config",
                "5",
                "times",
                "ev#1"
        ));
        evidenceCard.getFactFindings().add(factFinding(
                "payment.retry.maxAttempts.current_config",
                "5",
                "times",
                "ev#2"
        ));

        EvidenceLedger evidenceLedger = new EvidenceLedger();
        evidenceLedger.addCard(evidenceCard);

        assertThat(evidenceLedger.getCardsByTaskId()).containsKey("task-1");
        assertThat(evidenceLedger.getAnchorsById()).containsKeys("ev#1", "ev#2");
        assertThat(evidenceLedger.getFindingsByFactKey()).containsKey("payment.retry.maxAttempts.current_config");
        assertThat(evidenceLedger.getProjectionCandidates()).hasSize(1);
        assertThat(evidenceLedger.getProjectionCandidates().get(0).getPreferredCitationFormat())
                .isEqualTo(ProjectionCitationFormat.ARTICLE);
        assertThat(evidenceLedger.getProjectionCandidates().get(0).getAnchorId()).isEqualTo("ev#1");
    }

    /**
     * 验证低置信度 finding 不进入 ledger，也不会生成投影候选。
     */
    @Test
    void shouldFilterLowConfidenceFindingsBeforeAggregation() {
        EvidenceCard evidenceCard = new EvidenceCard();
        evidenceCard.setEvidenceId("ev#1");
        evidenceCard.setTaskId("task-1");
        evidenceCard.getEvidenceAnchors().add(articleAnchor("ev#1", "payment-routing"));
        FactFinding factFinding = factFinding(
                "payment.retry.maxAttempts.current_config",
                "5",
                "times",
                "ev#1"
        );
        factFinding.setConfidence(0.54D);
        evidenceCard.getFactFindings().add(factFinding);

        EvidenceLedger evidenceLedger = new EvidenceLedger();
        evidenceLedger.addCard(evidenceCard);

        assertThat(evidenceLedger.findingCount()).isZero();
        assertThat(evidenceLedger.getFindingsByFactKey()).isEmpty();
        assertThat(evidenceLedger.getProjectionCandidates()).isEmpty();
        assertThat(evidenceLedger.getAnchorsById()).containsKey("ev#1");
    }

    /**
     * 验证同主体不同事实槽位会建立 complement 关系，并可基于 ACTIVE projection 刷新 mustResolve 覆盖状态。
     */
    @Test
    void shouldTrackComplementsAndRefreshCoverageFromActiveProjections() {
        EvidenceCard evidenceCard = new EvidenceCard();
        evidenceCard.setEvidenceId("ev#1");
        evidenceCard.setTaskId("task-1");
        evidenceCard.getEvidenceAnchors().add(articleAnchor("ev#1", "payment-routing"));
        evidenceCard.getEvidenceAnchors().add(articleAnchor("ev#2", "payment-timeout"));
        evidenceCard.getFactFindings().add(factFinding(
                "payment.retry",
                "maxAttempts",
                "current_config",
                "5",
                "times",
                "ev#1"
        ));
        evidenceCard.getFactFindings().add(factFinding(
                "payment.retry",
                "timeout",
                "current_config",
                "30",
                "seconds",
                "ev#2"
        ));
        EvidenceLedger evidenceLedger = new EvidenceLedger();
        evidenceLedger.addCard(evidenceCard);
        evidenceLedger.registerMustResolveFactKeys(List.of(
                "payment.retry.maxAttempts.current_config",
                "payment.retry.timeout.current_config"
        ));
        AnswerProjection answerProjection = new AnswerProjection(
                1,
                "ev#1",
                ProjectionCitationFormat.ARTICLE,
                "[[payment-routing]]",
                "payment-routing",
                ProjectionStatus.ACTIVE,
                0,
                null
        );

        evidenceLedger.refreshCoverageState(List.of(answerProjection));

        assertThat(evidenceLedger.getComplements().get("payment.retry.maxAttempts.current_config"))
                .containsExactly("payment.retry.timeout.current_config");
        assertThat(evidenceLedger.getCoverageState())
                .containsEntry("payment.retry.maxAttempts.current_config", Boolean.TRUE)
                .containsEntry("payment.retry.timeout.current_config", Boolean.FALSE);
    }

    /**
     * 构造最小结构化 finding。
     *
     * @param factKey 事实键
     * @param valueText 值文本
     * @param unit 单位
     * @param anchorId 锚点标识
     * @return 结构化 finding
     */
    private FactFinding factFinding(String factKey, String valueText, String unit, String anchorId) {
        FactFinding factFinding = factFinding("payment.retry", "maxAttempts", "current_config", valueText, unit, anchorId);
        factFinding.setFactKey(factKey);
        return factFinding;
    }

    /**
     * 构造指定事实槽位的结构化 finding。
     *
     * @param subject 主体
     * @param predicate 谓词
     * @param qualifier 限定语
     * @param valueText 值文本
     * @param unit 单位
     * @param anchorId 锚点标识
     * @return 结构化 finding
     */
    private FactFinding factFinding(
            String subject,
            String predicate,
            String qualifier,
            String valueText,
            String unit,
            String anchorId
    ) {
        FactFinding factFinding = new FactFinding();
        factFinding.setFindingId(anchorId + "-finding");
        factFinding.setSubject(subject);
        factFinding.setPredicate(predicate);
        factFinding.setQualifier(qualifier);
        factFinding.setFactKey(factFinding.expectedFactKey());
        factFinding.setValueText(valueText);
        factFinding.setValueType(FactValueType.NUMBER);
        factFinding.setUnit(unit);
        factFinding.setClaimText("当前配置最多重试 " + valueText + " 次");
        factFinding.setConfidence(0.9D);
        factFinding.setSupportLevel(FindingSupportLevel.DIRECT);
        factFinding.setAnchorIds(List.of(anchorId));
        return factFinding;
    }

    private EvidenceAnchor articleAnchor(String anchorId, String articleKey) {
        EvidenceAnchor evidenceAnchor = new EvidenceAnchor();
        evidenceAnchor.setAnchorId(anchorId);
        evidenceAnchor.setSourceType(EvidenceAnchorSourceType.ARTICLE);
        evidenceAnchor.setSourceId(articleKey);
        evidenceAnchor.setQuoteText("默认最多重试 5 次");
        evidenceAnchor.setRetrievalScore(0.9D);
        return evidenceAnchor;
    }

    private EvidenceAnchor graphAnchor(String anchorId, String factKey) {
        EvidenceAnchor evidenceAnchor = new EvidenceAnchor();
        evidenceAnchor.setAnchorId(anchorId);
        evidenceAnchor.setSourceType(EvidenceAnchorSourceType.GRAPH_FACT);
        evidenceAnchor.setSourceId(factKey);
        evidenceAnchor.setQuoteText("payment.retry.maxAttempts.current_config=5");
        evidenceAnchor.setRetrievalScore(0.7D);
        return evidenceAnchor;
    }
}
