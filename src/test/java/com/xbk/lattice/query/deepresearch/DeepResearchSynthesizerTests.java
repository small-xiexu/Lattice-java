package com.xbk.lattice.query.deepresearch;

import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchSynthesisResult;
import com.xbk.lattice.query.deepresearch.domain.InternalAnswerDraft;
import com.xbk.lattice.query.deepresearch.domain.LayerSummary;
import com.xbk.lattice.query.deepresearch.service.DeepResearchSynthesizer;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchorSourceType;
import com.xbk.lattice.query.evidence.domain.FactFinding;
import com.xbk.lattice.query.evidence.domain.FactValueType;
import com.xbk.lattice.query.evidence.domain.FindingSupportLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepResearchSynthesizer 测试
 *
 * 职责：验证 Synthesizer 会先产出带 ev#N 的内部答案草稿
 *
 * @author xiexu
 */
class DeepResearchSynthesizerTests {

    /**
     * 验证内部草稿包含 resolved/missing/conflicting facts 与内部证据号。
     */
    @Test
    void shouldBuildInternalDraftWithEvidenceIdsAndFactStates() {
        EvidenceLedger evidenceLedger = new EvidenceLedger();
        EvidenceCard evidenceCard = new EvidenceCard();
        evidenceCard.setEvidenceId("ev#1");
        evidenceCard.setTaskId("task-1");
        evidenceCard.getEvidenceAnchors().add(articleAnchor("ev#1"));
        evidenceCard.getFactFindings().add(factFinding("ev#1", "5"));
        evidenceCard.getFactFindings().add(factFinding("ev#1", "3"));
        evidenceLedger.addCard(evidenceCard);
        evidenceLedger.registerMustResolveFactKeys(List.of(
                "payment.retry.maxAttempts.current_config",
                "payment.retry.timeout.current_config"
        ));
        evidenceLedger.markCoverage("payment.retry.maxAttempts.current_config", true);

        InternalAnswerDraft internalAnswerDraft = new DeepResearchSynthesizer(null)
                .buildInternalAnswerDraft("PaymentService 默认重试次数是多少", List.of(), evidenceLedger);

        assertThat(internalAnswerDraft.getDraftMarkdown()).contains("ev#1");
        assertThat(internalAnswerDraft.getResolvedFactKeys()).contains("payment.retry.maxAttempts.current_config");
        assertThat(internalAnswerDraft.getMissingFactKeys()).contains("payment.retry.timeout.current_config");
        assertThat(internalAnswerDraft.getConflictingFactKeys()).contains("payment.retry.maxAttempts.current_config");
    }

    /**
     * 验证内部草稿会保留同一 finding 的全部 anchorId，避免最终答案只投影第一个 citation。
     */
    @Test
    void shouldKeepAllAnchorIdsInInternalDraft() {
        EvidenceLedger evidenceLedger = new EvidenceLedger();
        EvidenceCard evidenceCard = new EvidenceCard();
        evidenceCard.setEvidenceId("ev#1");
        evidenceCard.setTaskId("task-1");
        evidenceCard.getEvidenceAnchors().add(articleAnchor("ev#1"));
        evidenceCard.getEvidenceAnchors().add(articleAnchor("ev#2"));
        FactFinding factFinding = factFinding("ev#1", "5");
        factFinding.setAnchorIds(List.of("ev#1", "ev#2"));
        evidenceCard.getFactFindings().add(factFinding);
        evidenceLedger.addCard(evidenceCard);

        InternalAnswerDraft internalAnswerDraft = new DeepResearchSynthesizer(null)
                .buildInternalAnswerDraft("PaymentService 默认重试次数是多少", List.of(), evidenceLedger);

        assertThat(internalAnswerDraft.getDraftMarkdown()).contains("(ev#1)");
        assertThat(internalAnswerDraft.getDraftMarkdown()).contains("(ev#2)");
    }

    /**
     * 验证冲突语义 finding 也会把 hasConflicts 置为 true。
     */
    @Test
    void shouldTreatConflictNarrativeAsConflictSignal() {
        EvidenceLedger evidenceLedger = new EvidenceLedger();
        EvidenceCard evidenceCard = new EvidenceCard();
        evidenceCard.setEvidenceId("ev#1");
        evidenceCard.setTaskId("task-1");
        evidenceCard.getEvidenceAnchors().add(articleAnchor("ev#1"));
        FactFinding factFinding = factFinding("ev#1", "5");
        factFinding.setClaimText("当前证据存在冲突，不能直接下单一结论");
        evidenceCard.getFactFindings().add(factFinding);
        evidenceLedger.addCard(evidenceCard);

        DeepResearchSynthesisResult synthesisResult = new DeepResearchSynthesizer(new NoopCitationCheckService())
                .synthesize("库存并发到底用什么锁", List.of(), evidenceLedger);

        assertThat(synthesisResult.isHasConflicts()).isTrue();
    }

    /**
     * 验证分层摘要会去掉内部 taskId 前缀与尾随标点噪音。
     */
    @Test
    void shouldNormalizeLayerSummaryNarrativeInInternalDraft() {
        LayerSummary layerSummary = new LayerSummary();
        layerSummary.setLayerIndex(0);
        layerSummary.setSummaryMarkdown("""
                - inventory_lock_compare：系统采用基于版本号校验的乐观锁。；
                - redis_lock_probe：资料也提到 Redis 分布式锁；
                """);

        InternalAnswerDraft internalAnswerDraft = new DeepResearchSynthesizer(null)
                .buildInternalAnswerDraft(
                        "库存并发到底是乐观锁还是 Redis 锁",
                        List.of(layerSummary),
                        new EvidenceLedger()
                );

        assertThat(internalAnswerDraft.getDraftMarkdown())
                .contains("- 第 1 层：系统采用基于版本号校验的乐观锁；资料也提到 Redis 分布式锁");
        assertThat(internalAnswerDraft.getDraftMarkdown())
                .doesNotContain("inventory_lock_compare", "redis_lock_probe", "。；", "Redis 分布式锁；");
    }

    /**
     * 验证带维度提示的对比题会按主体与维度重组草稿，而不是只平铺 finding 列表。
     */
    @Test
    void shouldBuildDimensionComparisonDraftForStructuredComparisonQuestion() {
        EvidenceLedger evidenceLedger = new EvidenceLedger();

        EvidenceCard inventoryCard = new EvidenceCard();
        inventoryCard.setEvidenceId("ev#1");
        inventoryCard.setTaskId("task-1");
        inventoryCard.setScope("库存并发控制 的关键结论是什么");
        inventoryCard.getEvidenceAnchors().add(articleAnchor("ev#1"));
        inventoryCard.getFactFindings().add(conflictFinding(
                "ev#1",
                "task.inventory",
                "库存并发控制采用 Redis 分布式锁串行化处理，以避免并发扣减产生冲突。"
        ));
        inventoryCard.getFactFindings().add(conflictFinding(
                "ev#2",
                "task.inventory",
                "库存扣减采用基于版本号校验的乐观锁，字段为 inventory_lock_version。"
        ));
        evidenceLedger.addCard(inventoryCard);

        EvidenceCard retryCard = new EvidenceCard();
        retryCard.setEvidenceId("ev#3");
        retryCard.setTaskId("task-2");
        retryCard.setScope("补偿重试策略 的关键结论是什么");
        retryCard.getEvidenceAnchors().add(articleAnchor("ev#3"));
        retryCard.getFactFindings().add(conflictFinding(
                "ev#3",
                "task.retry",
                "补偿失败后会进入 retry_queue 异步重试，由 RetryWorker 消费。"
        ));
        evidenceLedger.addCard(retryCard);

        InternalAnswerDraft internalAnswerDraft = new DeepResearchSynthesizer(null)
                .buildInternalAnswerDraft(
                        "请按目标、触发时机与实现方式，对比库存并发控制与补偿重试策略",
                        List.of(),
                        evidenceLedger
                );

        assertThat(internalAnswerDraft.getDraftMarkdown()).contains("## 按维度对比");
        assertThat(internalAnswerDraft.getDraftMarkdown()).contains("### 目标");
        assertThat(internalAnswerDraft.getDraftMarkdown()).contains("### 触发时机");
        assertThat(internalAnswerDraft.getDraftMarkdown()).contains("### 实现方式");
        assertThat(internalAnswerDraft.getDraftMarkdown()).contains("- 库存并发控制：");
        assertThat(internalAnswerDraft.getDraftMarkdown()).contains("- 补偿重试策略：");
        assertThat(internalAnswerDraft.getDraftMarkdown()).doesNotContain("task-1", "task-2");
    }

    /**
     * 验证最终出站答案会移除内部层摘要与文档元数据行，避免 HTTP 正文污染。
     */
    @Test
    void shouldSanitizeInternalMetadataFromProjectedAnswer() {
        EvidenceLedger evidenceLedger = new EvidenceLedger();
        EvidenceCard evidenceCard = new EvidenceCard();
        evidenceCard.setEvidenceId("ev#1");
        evidenceCard.setTaskId("task-1");
        evidenceCard.getEvidenceAnchors().add(articleAnchor("ev#1"));
        evidenceCard.getFactFindings().add(factFinding("ev#1", "5"));
        evidenceLedger.addCard(evidenceCard);
        LayerSummary layerSummary = new LayerSummary();
        layerSummary.setLayerIndex(0);
        layerSummary.setSummaryMarkdown("""
                metadata: {"sourcePaths":["docs/项目启动配置清单.md"],"articleKey":"startup"}
                sourcePaths: docs/项目启动配置清单.md
                """);

        DeepResearchSynthesisResult synthesisResult = new DeepResearchSynthesizer(new NoopCitationCheckService())
                .synthesize("PaymentService 默认重试次数是多少", List.of(layerSummary), evidenceLedger);

        assertThat(synthesisResult.getAnswerMarkdown()).contains("PaymentService 默认最多重试 5 次");
        assertThat(synthesisResult.getAnswerMarkdown()).contains("[[payment-routing]]");
        assertThat(synthesisResult.getAnswerMarkdown())
                .doesNotContain("## 分层摘要", "metadata:", "sourcePaths", "articleKey", "conceptId");
    }

    private EvidenceAnchor articleAnchor(String anchorId) {
        EvidenceAnchor evidenceAnchor = new EvidenceAnchor();
        evidenceAnchor.setAnchorId(anchorId);
        evidenceAnchor.setSourceType(EvidenceAnchorSourceType.ARTICLE);
        evidenceAnchor.setSourceId("payment-routing");
        evidenceAnchor.setQuoteText("PaymentService 默认最多重试 5 次。");
        evidenceAnchor.setRetrievalScore(0.95D);
        return evidenceAnchor;
    }

    private FactFinding factFinding(String anchorId, String valueText) {
        FactFinding factFinding = new FactFinding();
        factFinding.setFindingId(anchorId + "-finding-" + valueText);
        factFinding.setSubject("payment.retry");
        factFinding.setPredicate("maxAttempts");
        factFinding.setQualifier("current_config");
        factFinding.setFactKey(factFinding.expectedFactKey());
        factFinding.setValueText(valueText);
        factFinding.setValueType(FactValueType.NUMBER);
        factFinding.setUnit("times");
        factFinding.setClaimText("PaymentService 默认最多重试 " + valueText + " 次");
        factFinding.setConfidence(0.95D);
        factFinding.setSupportLevel(FindingSupportLevel.DIRECT);
        factFinding.setAnchorIds(List.of(anchorId));
        return factFinding;
    }

    private FactFinding conflictFinding(String anchorId, String subject, String claimText) {
        FactFinding factFinding = new FactFinding();
        factFinding.setFindingId(anchorId + "-finding");
        factFinding.setSubject(subject);
        factFinding.setPredicate("claim");
        factFinding.setQualifier("deep_research");
        factFinding.setFactKey(subject + ".claim.deep_research");
        factFinding.setValueText(claimText);
        factFinding.setValueType(FactValueType.STRING);
        factFinding.setClaimText(claimText);
        factFinding.setConfidence(0.95D);
        factFinding.setSupportLevel(FindingSupportLevel.DIRECT);
        factFinding.setAnchorIds(List.of(anchorId));
        return factFinding;
    }

    private static final class NoopCitationCheckService extends com.xbk.lattice.query.citation.CitationCheckService {

        private NoopCitationCheckService() {
            super(new com.xbk.lattice.query.citation.CitationExtractor(),
                    new com.xbk.lattice.query.citation.CitationValidator(null, null));
        }

        @Override
        public com.xbk.lattice.query.citation.CitationCheckReport check(
                String answerMarkdown,
                com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle answerProjectionBundle
        ) {
            return new com.xbk.lattice.query.citation.CitationCheckReport(
                    answerMarkdown,
                    List.of(),
                    List.of(),
                    0,
                    0,
                    0,
                    true,
                    1.0D,
                    0,
                    0,
                    0,
                    0
            );
        }

        @Override
        public boolean shouldRepair(
                com.xbk.lattice.query.citation.CitationCheckReport report,
                com.xbk.lattice.query.citation.CitationCheckOptions options,
                int repairAttemptCount
        ) {
            return false;
        }
    }
}
