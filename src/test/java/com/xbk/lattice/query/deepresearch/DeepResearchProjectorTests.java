package com.xbk.lattice.query.deepresearch;

import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.InternalAnswerDraft;
import com.xbk.lattice.query.deepresearch.projector.DeepResearchProjector;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchorSourceType;
import com.xbk.lattice.query.evidence.domain.FactFinding;
import com.xbk.lattice.query.evidence.domain.FactValueType;
import com.xbk.lattice.query.evidence.domain.FindingSupportLevel;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepResearchProjector 测试
 *
 * 职责：验证内部 ev#N 证据号会被投影为最终 citation 白名单
 *
 * @author xiexu
 */
class DeepResearchProjectorTests {

    /**
     * 验证 Projector 只会把 ARTICLE / SOURCE_FILE 投影成用户可见 citation。
     */
    @Test
    void shouldProjectOnlyOutboundCitationCandidates() {
        EvidenceLedger evidenceLedger = new EvidenceLedger();
        evidenceLedger.addCard(cardWithArticleAndGraphEvidence());
        InternalAnswerDraft internalAnswerDraft = new InternalAnswerDraft();
        internalAnswerDraft.setDraftMarkdown("""
                # 深度研究结论

                - PaymentService 默认最多重试 5 次 (ev#1)
                - payment.retry.maxAttempts.current_config 是内部图事实 (ev#2)
                """);

        AnswerProjectionBundle answerProjectionBundle = new DeepResearchProjector().project(internalAnswerDraft, evidenceLedger);

        assertThat(answerProjectionBundle.getAnswerMarkdown()).contains("[[payment-routing]]");
        assertThat(answerProjectionBundle.getAnswerMarkdown()).doesNotContain("([[payment-routing]])", "()");
        assertThat(answerProjectionBundle.getAnswerMarkdown()).doesNotContain("ev#1", "ev#2");
        assertThat(answerProjectionBundle.getProjections()).hasSize(1);
        assertThat(answerProjectionBundle.getProjections().get(0).getSourceType())
                .isEqualTo(ProjectionCitationFormat.ARTICLE);
    }

    /**
     * 验证同 literal 会归并为一个 canonical projection。
     */
    @Test
    void shouldMergeSameLiteralIntoCanonicalProjection() {
        EvidenceLedger evidenceLedger = new EvidenceLedger();
        EvidenceCard evidenceCard = new EvidenceCard();
        evidenceCard.setEvidenceId("ev#1");
        evidenceCard.setTaskId("task-1");
        evidenceCard.getEvidenceAnchors().add(articleAnchor("ev#1", "payment-routing"));
        evidenceCard.getEvidenceAnchors().add(articleAnchor("ev#2", "payment-routing"));
        evidenceCard.getFactFindings().add(factFinding("ev#1", "payment.retry.maxAttempts.current_config"));
        evidenceCard.getFactFindings().add(factFinding("ev#2", "payment.retry.timeout.current_config"));
        evidenceLedger.addCard(evidenceCard);
        InternalAnswerDraft internalAnswerDraft = new InternalAnswerDraft();
        internalAnswerDraft.setDraftMarkdown("结论 A (ev#1)\n结论 B (ev#2)");

        AnswerProjectionBundle answerProjectionBundle = new DeepResearchProjector().project(internalAnswerDraft, evidenceLedger);

        assertThat(answerProjectionBundle.getAnswerMarkdown()).contains("[[payment-routing]]");
        assertThat(answerProjectionBundle.getProjections()).hasSize(1);
    }

    /**
     * 验证 projector 清理内部证据号时不会把 Markdown 换行压成单行。
     */
    @Test
    void shouldPreserveMarkdownLineBreaksWhenProjecting() {
        EvidenceLedger evidenceLedger = new EvidenceLedger();
        evidenceLedger.addCard(cardWithArticleAndGraphEvidence());
        InternalAnswerDraft internalAnswerDraft = new InternalAnswerDraft();
        internalAnswerDraft.setDraftMarkdown("""
                # 深度研究结论

                ## 结论
                - PaymentService 默认最多重试 5 次 (ev#1)

                ## 分层摘要
                - 第 1 层：验证重试配置
                """);

        AnswerProjectionBundle answerProjectionBundle = new DeepResearchProjector().project(internalAnswerDraft, evidenceLedger);

        assertThat(answerProjectionBundle.getAnswerMarkdown()).contains("# 深度研究结论\n\n## 结论");
        assertThat(answerProjectionBundle.getAnswerMarkdown()).contains("\n\n## 分层摘要\n");
        assertThat(answerProjectionBundle.getAnswerMarkdown()).doesNotContain("## 结论 -");
    }

    private EvidenceCard cardWithArticleAndGraphEvidence() {
        EvidenceCard evidenceCard = new EvidenceCard();
        evidenceCard.setEvidenceId("ev#1");
        evidenceCard.setTaskId("task-1");
        evidenceCard.getEvidenceAnchors().add(articleAnchor("ev#1", "payment-routing"));
        EvidenceAnchor graphAnchor = new EvidenceAnchor();
        graphAnchor.setAnchorId("ev#2");
        graphAnchor.setSourceType(EvidenceAnchorSourceType.GRAPH_FACT);
        graphAnchor.setSourceId("payment.retry.maxAttempts.current_config");
        graphAnchor.setQuoteText("payment.retry.maxAttempts.current_config=5");
        graphAnchor.setRetrievalScore(0.9D);
        evidenceCard.getEvidenceAnchors().add(graphAnchor);
        evidenceCard.getFactFindings().add(factFinding("ev#1", "payment.retry.maxAttempts.current_config"));
        evidenceCard.getFactFindings().add(factFinding("ev#2", "payment.retry.graph.current_config"));
        return evidenceCard;
    }

    private EvidenceAnchor articleAnchor(String anchorId, String articleKey) {
        EvidenceAnchor evidenceAnchor = new EvidenceAnchor();
        evidenceAnchor.setAnchorId(anchorId);
        evidenceAnchor.setSourceType(EvidenceAnchorSourceType.ARTICLE);
        evidenceAnchor.setSourceId(articleKey);
        evidenceAnchor.setQuoteText("PaymentService 默认最多重试 5 次。");
        evidenceAnchor.setRetrievalScore(0.95D);
        return evidenceAnchor;
    }

    private FactFinding factFinding(String anchorId, String factKey) {
        FactFinding factFinding = new FactFinding();
        factFinding.setFindingId(anchorId + "-finding");
        factFinding.setSubject(factKey.substring(0, factKey.lastIndexOf('.')));
        factFinding.setPredicate("value");
        factFinding.setQualifier("current");
        factFinding.setFactKey(factFinding.expectedFactKey());
        factFinding.setValueText("5");
        factFinding.setValueType(FactValueType.NUMBER);
        factFinding.setClaimText("PaymentService 默认最多重试 5 次");
        factFinding.setConfidence(0.95D);
        factFinding.setSupportLevel(FindingSupportLevel.DIRECT);
        factFinding.setAnchorIds(List.of(anchorId));
        return factFinding;
    }
}
