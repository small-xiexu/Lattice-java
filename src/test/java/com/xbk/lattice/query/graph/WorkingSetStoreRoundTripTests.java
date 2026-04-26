package com.xbk.lattice.query.graph;

import com.xbk.lattice.query.citation.Citation;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationSourceType;
import com.xbk.lattice.query.citation.CitationValidationResult;
import com.xbk.lattice.query.citation.CitationValidationStatus;
import com.xbk.lattice.query.citation.ClaimSegment;
import com.xbk.lattice.query.citation.QueryAnswerAuditSnapshot;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchAuditSnapshot;
import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.store.InMemoryDeepResearchWorkingSetStore;
import com.xbk.lattice.query.evidence.domain.AnswerProjection;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchorSourceType;
import com.xbk.lattice.query.evidence.domain.FactFinding;
import com.xbk.lattice.query.evidence.domain.FactValueType;
import com.xbk.lattice.query.evidence.domain.FindingSupportLevel;
import com.xbk.lattice.query.evidence.domain.ProjectionCandidate;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import com.xbk.lattice.query.evidence.domain.ProjectionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkingSetStore 往返测试
 *
 * 职责：验证新增 projection bundle / projection candidate 外置存储槽位可正常读写
 *
 * @author xiexu
 */
class WorkingSetStoreRoundTripTests {

    /**
     * 验证 QueryWorkingSetStore 可往返保存 Citation 审计与答案投影对象。
     */
    @Test
    void shouldRoundTripCitationArtifactsInQueryWorkingSetStore() {
        InMemoryQueryWorkingSetStore queryWorkingSetStore = new InMemoryQueryWorkingSetStore();
        Citation citation = new Citation(
                1,
                "[[payment-routing]]",
                CitationSourceType.ARTICLE,
                "payment-routing",
                "支付路由默认最多重试 3 次",
                "支付路由默认最多重试 3 次 [[payment-routing]]"
        );
        ClaimSegment claimSegment = new ClaimSegment(
                0,
                "支付路由默认最多重试 3 次",
                "支付路由默认最多重试 3 次 [[payment-routing]]",
                List.of(citation)
        );
        CitationValidationResult validationResult = new CitationValidationResult(
                "payment-routing",
                CitationSourceType.ARTICLE,
                CitationValidationStatus.VERIFIED,
                0.95D,
                "projection_matched",
                "默认最多重试 3 次",
                1
        );
        CitationCheckReport citationCheckReport = new CitationCheckReport(
                "支付路由默认最多重试 3 次 [[payment-routing]]",
                List.of(claimSegment),
                List.of(validationResult),
                1,
                0,
                0,
                false,
                1.0D,
                0,
                0,
                0,
                0
        );
        QueryAnswerAuditSnapshot answerAuditSnapshot = new QueryAnswerAuditSnapshot(11L, 2, citationCheckReport);
        AnswerProjection projection = new AnswerProjection(
                1,
                "ev#1",
                ProjectionCitationFormat.ARTICLE,
                "[[payment-routing]]",
                "payment-routing",
                ProjectionStatus.ACTIVE,
                0,
                null
        );
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                "结论 [[payment-routing]]",
                List.of(projection)
        );

        String reportRef = queryWorkingSetStore.saveCitationCheckReport("query-1", citationCheckReport);
        String auditRef = queryWorkingSetStore.saveAnswerAudit("query-1", answerAuditSnapshot);
        String bundleRef = queryWorkingSetStore.saveAnswerProjectionBundle("query-1", answerProjectionBundle);

        CitationCheckReport loadedReport = queryWorkingSetStore.loadCitationCheckReport(reportRef);
        QueryAnswerAuditSnapshot loadedAudit = queryWorkingSetStore.loadAnswerAudit(auditRef);
        AnswerProjectionBundle loadedBundle = queryWorkingSetStore.loadAnswerProjectionBundle(bundleRef);

        assertThat(loadedReport).isNotNull();
        assertThat(loadedReport.getCoverageRate()).isEqualTo(1.0D);
        assertThat(loadedReport.getClaimSegments()).hasSize(1);
        assertThat(loadedReport.getResults()).hasSize(1);
        assertThat(loadedAudit).isNotNull();
        assertThat(loadedAudit.getAuditId()).isEqualTo(11L);
        assertThat(loadedAudit.getAnswerVersion()).isEqualTo(2);
        assertThat(loadedAudit.getCitationCheckReport()).isSameAs(loadedReport);
        assertThat(loadedBundle).isNotNull();
        assertThat(loadedBundle.getAnswerMarkdown()).isEqualTo("结论 [[payment-routing]]");
        assertThat(loadedBundle.getProjections()).hasSize(1);
        assertThat(loadedBundle.getProjections().get(0).getCitationLiteral()).isEqualTo("[[payment-routing]]");
    }

    /**
     * 验证 DeepResearchWorkingSetStore 可往返保存 ledger、projection 与审计对象。
     */
    @Test
    void shouldRoundTripDeepResearchArtifactsInDeepResearchWorkingSetStore() {
        InMemoryDeepResearchWorkingSetStore deepResearchWorkingSetStore = new InMemoryDeepResearchWorkingSetStore();
        EvidenceAnchor evidenceAnchor = new EvidenceAnchor();
        evidenceAnchor.setAnchorId("ev#1");
        evidenceAnchor.setSourceType(EvidenceAnchorSourceType.ARTICLE);
        evidenceAnchor.setSourceId("payment-routing");
        evidenceAnchor.setChunkId("chunk-1");
        evidenceAnchor.setQuoteText("默认最多重试 3 次");
        evidenceAnchor.setRetrievalScore(0.95D);
        FactFinding factFinding = new FactFinding();
        factFinding.setFindingId("ev#1-finding");
        factFinding.setSubject("payment.retry");
        factFinding.setPredicate("maxAttempts");
        factFinding.setQualifier("current_config");
        factFinding.setFactKey(factFinding.expectedFactKey());
        factFinding.setValueText("3");
        factFinding.setValueType(FactValueType.NUMBER);
        factFinding.setUnit("times");
        factFinding.setClaimText("支付路由默认最多重试 3 次");
        factFinding.setConfidence(0.95D);
        factFinding.setSupportLevel(FindingSupportLevel.DIRECT);
        factFinding.setAnchorIds(List.of("ev#1"));
        EvidenceCard evidenceCard = new EvidenceCard();
        evidenceCard.setEvidenceId("ev#1");
        evidenceCard.setLayerIndex(0);
        evidenceCard.setTaskId("task-1");
        evidenceCard.setScope("支付路由");
        evidenceCard.setEvidenceAnchors(List.of(evidenceAnchor));
        evidenceCard.setFactFindings(List.of(factFinding));
        EvidenceLedger evidenceLedger = new EvidenceLedger();
        evidenceLedger.addCard(evidenceCard);
        ProjectionCandidate projectionCandidate = new ProjectionCandidate(
                "pc-1",
                "payment.retry.maxAttempts",
                "ev#1",
                ProjectionCitationFormat.SOURCE_FILE,
                "src/main/resources/application.yml",
                10,
                true,
                0.91D
        );
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                "结论 [→ src/main/resources/application.yml]",
                List.of()
        );
        DeepResearchAuditSnapshot deepResearchAuditSnapshot = new DeepResearchAuditSnapshot(88L, 1);

        String taskResultRef = deepResearchWorkingSetStore.saveTaskResults("dr-1", "layer-0-task-1", List.of(evidenceCard));
        String ledgerRef = deepResearchWorkingSetStore.saveEvidenceLedger("dr-1", evidenceLedger);
        String candidateRef = deepResearchWorkingSetStore.saveProjectionCandidates("dr-1", List.of(projectionCandidate));
        String bundleRef = deepResearchWorkingSetStore.saveAnswerProjectionBundle("dr-1", answerProjectionBundle);
        String auditRef = deepResearchWorkingSetStore.saveDeepResearchAudit("dr-1", deepResearchAuditSnapshot);

        EvidenceLedger loadedLedger = deepResearchWorkingSetStore.loadEvidenceLedger(ledgerRef);
        assertThat(deepResearchWorkingSetStore.loadTaskResults(taskResultRef)).hasSize(1);
        assertThat(deepResearchWorkingSetStore.loadTaskResults(taskResultRef).get(0).getTaskId()).isEqualTo("task-1");
        assertThat(deepResearchWorkingSetStore.loadProjectionCandidates(candidateRef)).hasSize(1);
        assertThat(deepResearchWorkingSetStore.loadProjectionCandidates(candidateRef).get(0).getAnchorId()).isEqualTo("ev#1");
        assertThat(deepResearchWorkingSetStore.loadAnswerProjectionBundle(bundleRef)).isNotNull();
        assertThat(deepResearchWorkingSetStore.loadAnswerProjectionBundle(bundleRef).getAnswerMarkdown())
                .isEqualTo("结论 [→ src/main/resources/application.yml]");
        assertThat(loadedLedger).isNotNull();
        assertThat(loadedLedger.cardCount()).isEqualTo(1);
        assertThat(loadedLedger.getCards().get(0).getFactFindings()).hasSize(1);
        assertThat(loadedLedger.getCards().get(0).getFactFindings().get(0).getFactKey())
                .isEqualTo("payment.retry.maxAttempts.current_config");
        assertThat(loadedLedger.getAnchorsById().get("ev#1").getSourceId()).isEqualTo("payment-routing");
        assertThat(deepResearchWorkingSetStore.loadDeepResearchAudit(auditRef)).isInstanceOf(DeepResearchAuditSnapshot.class);
        DeepResearchAuditSnapshot loadedAudit = (DeepResearchAuditSnapshot) deepResearchWorkingSetStore.loadDeepResearchAudit(auditRef);
        assertThat(loadedAudit.getRunId()).isEqualTo(88L);
        assertThat(loadedAudit.getEvidenceCardCount()).isEqualTo(1);
    }
}
