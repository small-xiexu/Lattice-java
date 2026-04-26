package com.xbk.lattice.query.deepresearch;

import com.xbk.lattice.api.query.QueryRequest;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.infra.persistence.DeepResearchRunJdbcRepository;
import com.xbk.lattice.query.citation.Citation;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationSourceType;
import com.xbk.lattice.query.citation.CitationValidationResult;
import com.xbk.lattice.query.citation.CitationValidationStatus;
import com.xbk.lattice.query.citation.ClaimSegment;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchAuditSnapshot;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchSynthesisResult;
import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.LayerSummary;
import com.xbk.lattice.query.deepresearch.domain.LayeredResearchPlan;
import com.xbk.lattice.query.deepresearch.domain.ResearchLayer;
import com.xbk.lattice.query.deepresearch.domain.ResearchTask;
import com.xbk.lattice.query.deepresearch.graph.DeepResearchGraphDefinitionFactory;
import com.xbk.lattice.query.deepresearch.graph.DeepResearchStateMapper;
import com.xbk.lattice.query.deepresearch.service.DeepResearchAuditPersistenceService;
import com.xbk.lattice.query.deepresearch.service.DeepResearchExecutionContext;
import com.xbk.lattice.query.deepresearch.service.DeepResearchExecutionRegistry;
import com.xbk.lattice.query.deepresearch.service.DeepResearchPlanner;
import com.xbk.lattice.query.deepresearch.service.DeepResearchResearcherService;
import com.xbk.lattice.query.deepresearch.service.DeepResearchRouter;
import com.xbk.lattice.query.deepresearch.service.DeepResearchSynthesizer;
import com.xbk.lattice.query.deepresearch.store.DeepResearchWorkingSetStore;
import com.xbk.lattice.query.deepresearch.store.InMemoryDeepResearchWorkingSetStore;
import com.xbk.lattice.query.evidence.domain.AnswerProjection;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchorSourceType;
import com.xbk.lattice.query.evidence.domain.FactFinding;
import com.xbk.lattice.query.evidence.domain.FactValueType;
import com.xbk.lattice.query.evidence.domain.FindingSupportLevel;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import com.xbk.lattice.query.evidence.domain.ProjectionStatus;
import com.xbk.lattice.query.graph.InMemoryQueryWorkingSetStore;
import com.xbk.lattice.query.graph.QueryWorkingSetStore;
import com.xbk.lattice.query.service.DeepResearchOrchestrator;
import com.xbk.lattice.query.service.KnowledgeSearchService;
import com.xbk.lattice.query.service.QueryArticleHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepResearchOrchestrator 测试
 *
 * 职责：验证 Deep Research 路由、图执行、审计收口与对外摘要
 *
 * @author xiexu
 */
class DeepResearchOrchestratorTests {

    /**
     * 验证 Deep Research 会执行分层图、持久化审计并返回最终摘要。
     */
    @Test
    void shouldExecuteDeepResearchGraphAndPersistAudits() {
        DeepResearchWorkingSetStore deepResearchWorkingSetStore = new InMemoryDeepResearchWorkingSetStore();
        QueryWorkingSetStore queryWorkingSetStore = new InMemoryQueryWorkingSetStore();
        DeepResearchExecutionRegistry deepResearchExecutionRegistry = new DeepResearchExecutionRegistry();
        RecordingDeepResearchAuditPersistenceService deepResearchAuditPersistenceService =
                new RecordingDeepResearchAuditPersistenceService();
        DeepResearchOrchestrator deepResearchOrchestrator = buildOrchestrator(
                new FixedSynthesizer(),
                deepResearchWorkingSetStore,
                queryWorkingSetStore,
                deepResearchExecutionRegistry,
                deepResearchAuditPersistenceService
        );
        QueryRequest queryRequest = new QueryRequest();
        queryRequest.setQuestion("RoutePlanner 和 PaymentService 有什么区别");
        queryRequest.setForceDeep(true);
        queryRequest.setMaxLlmCalls(4);
        queryRequest.setOverallTimeoutMs(10_000);

        QueryResponse queryResponse = deepResearchOrchestrator.execute(queryRequest, "dr-query-001");

        assertThat(queryResponse.getAnswer()).contains("深度研究结论");
        assertThat(queryResponse.getDeepResearch()).isNotNull();
        assertThat(queryResponse.getDeepResearch().isRouted()).isTrue();
        assertThat(queryResponse.getDeepResearch().getLayerCount()).isEqualTo(2);
        assertThat(queryResponse.getDeepResearch().getTaskCount()).isEqualTo(3);
        assertThat(queryResponse.getDeepResearch().getEvidenceCardCount()).isEqualTo(3);
        assertThat(queryResponse.getCitationCheck()).isNotNull();
        assertThat(deepResearchAuditPersistenceService.called).isTrue();
        assertThat(deepResearchAuditPersistenceService.lastEvidenceCardCount).isEqualTo(3);
        assertThat(queryResponse.getSources()).isNotEmpty();
        assertThat(queryResponse.getSources().get(0).getDerivation()).isEqualTo("PROJECTION");
        assertThat(queryResponse.getArticles()).isNotEmpty();
        assertThat(queryResponse.getArticles().get(0).getDerivation()).isEqualTo("PROJECTION");
    }

    /**
     * 验证 projection 缺失时不会把内部 ev#N 暴露给用户。
     */
    @Test
    void shouldHideInternalEvidenceIdsWhenProjectionBundleIsMissing() {
        DeepResearchWorkingSetStore deepResearchWorkingSetStore = new InMemoryDeepResearchWorkingSetStore();
        QueryWorkingSetStore queryWorkingSetStore = new InMemoryQueryWorkingSetStore();
        DeepResearchExecutionRegistry deepResearchExecutionRegistry = new DeepResearchExecutionRegistry();
        RecordingDeepResearchAuditPersistenceService deepResearchAuditPersistenceService =
                new RecordingDeepResearchAuditPersistenceService();
        DeepResearchOrchestrator deepResearchOrchestrator = buildOrchestrator(
                new NoProjectionSynthesizer(),
                deepResearchWorkingSetStore,
                queryWorkingSetStore,
                deepResearchExecutionRegistry,
                deepResearchAuditPersistenceService
        );
        QueryRequest queryRequest = new QueryRequest();
        queryRequest.setQuestion("RoutePlanner 和 PaymentService 有什么区别");
        queryRequest.setForceDeep(true);
        queryRequest.setMaxLlmCalls(4);
        queryRequest.setOverallTimeoutMs(10_000);

        QueryResponse queryResponse = deepResearchOrchestrator.execute(queryRequest, "dr-query-002");

        assertThat(queryResponse.getAnswer()).contains("无法生成可核验引用版答案");
        assertThat(queryResponse.getAnswer()).doesNotContain("ev#1");
        assertThat(queryResponse.getDeepResearch().isPartialAnswer()).isTrue();
        assertThat(queryResponse.getSources()).isEmpty();
        assertThat(queryResponse.getArticles()).isEmpty();
        assertThat(deepResearchAuditPersistenceService.called).isTrue();
    }

    /**
     * 构造使用指定综合器的 Deep Research 编排器。
     *
     * @param deepResearchSynthesizer 综合器
     * @param deepResearchWorkingSetStore Deep Research 工作集
     * @param queryWorkingSetStore Query 工作集
     * @param deepResearchExecutionRegistry 执行上下文注册表
     * @param deepResearchAuditPersistenceService 审计持久化服务
     * @return 编排器
     */
    private DeepResearchOrchestrator buildOrchestrator(
            DeepResearchSynthesizer deepResearchSynthesizer,
            DeepResearchWorkingSetStore deepResearchWorkingSetStore,
            QueryWorkingSetStore queryWorkingSetStore,
            DeepResearchExecutionRegistry deepResearchExecutionRegistry,
            DeepResearchAuditPersistenceService deepResearchAuditPersistenceService
    ) {
        DeepResearchStateMapper deepResearchStateMapper = new DeepResearchStateMapper();
        DeepResearchGraphDefinitionFactory graphDefinitionFactory = new DeepResearchGraphDefinitionFactory(
                deepResearchStateMapper,
                deepResearchWorkingSetStore,
                queryWorkingSetStore,
                deepResearchExecutionRegistry,
                new FixedResearcherService(),
                deepResearchSynthesizer
        );
        return new DeepResearchOrchestrator(
                new DeepResearchRouter(),
                new DeepResearchPlanner(),
                graphDefinitionFactory,
                deepResearchStateMapper,
                deepResearchWorkingSetStore,
                deepResearchExecutionRegistry,
                queryWorkingSetStore,
                deepResearchAuditPersistenceService,
                new FixedKnowledgeSearchService(),
                null
        );
    }

    private static class FixedResearcherService extends DeepResearchResearcherService {

        private FixedResearcherService() {
            super(null, null);
        }

        @Override
        public EvidenceCard research(
                String queryId,
                ResearchTask task,
                int layerIndex,
                LayerSummary previousLayerSummary,
                List<EvidenceCard> preferredCards,
                DeepResearchExecutionContext executionContext
        ) {
            executionContext.tryAcquireLlmCall();
            String anchorId = executionContext.nextEvidenceId();
            EvidenceCard evidenceCard = new EvidenceCard();
            evidenceCard.setEvidenceId(anchorId);
            evidenceCard.setLayerIndex(layerIndex);
            evidenceCard.setTaskId(task.getTaskId());
            evidenceCard.setScope(task.getQuestion());
            evidenceCard.getEvidenceAnchors().add(articleAnchor(anchorId));
            evidenceCard.getFactFindings().add(factFinding(task, anchorId));
            evidenceCard.getSelectedArticleKeys().add("payment-routing");
            for (EvidenceCard preferredCard : preferredCards) {
                evidenceCard.getRelatedLeads().add(preferredCard.getEvidenceId());
            }
            return evidenceCard;
        }

        private EvidenceAnchor articleAnchor(String anchorId) {
            EvidenceAnchor evidenceAnchor = new EvidenceAnchor();
            evidenceAnchor.setAnchorId(anchorId);
            evidenceAnchor.setSourceType(EvidenceAnchorSourceType.ARTICLE);
            evidenceAnchor.setSourceId("payment-routing");
            evidenceAnchor.setQuoteText("RoutePlanner -> PaymentService.plan");
            evidenceAnchor.setRetrievalScore(0.9D);
            return evidenceAnchor;
        }

        private FactFinding factFinding(ResearchTask task, String anchorId) {
            FactFinding factFinding = new FactFinding();
            factFinding.setFindingId(anchorId + "-finding");
            factFinding.setSubject(task.getTaskId());
            factFinding.setPredicate("claim");
            factFinding.setQualifier("deep_research");
            factFinding.setFactKey(factFinding.expectedFactKey());
            factFinding.setValueText(task.getQuestion() + " 的结论");
            factFinding.setValueType(FactValueType.STRING);
            factFinding.setClaimText(task.getQuestion() + " 的结论");
            factFinding.setConfidence(0.9D);
            factFinding.setSupportLevel(FindingSupportLevel.DIRECT);
            factFinding.setAnchorIds(List.of(anchorId));
            return factFinding;
        }
    }

    private static class FixedSynthesizer extends DeepResearchSynthesizer {

        private FixedSynthesizer() {
            super(null);
        }

        @Override
        public DeepResearchSynthesisResult synthesize(
                String question,
                List<LayerSummary> layerSummaries,
                EvidenceLedger evidenceLedger
        ) {
            Citation citation = new Citation(
                    0,
                    "[[payment-routing]]",
                    CitationSourceType.ARTICLE,
                    "payment-routing",
                    "综合结论",
                    "综合结论 [[payment-routing]]"
            );
            ClaimSegment claimSegment = new ClaimSegment(
                    0,
                    "综合结论",
                    "综合结论 [[payment-routing]]",
                    List.of(citation)
            );
            CitationValidationResult validationResult = new CitationValidationResult(
                    "payment-routing",
                    CitationSourceType.ARTICLE,
                    CitationValidationStatus.VERIFIED,
                    0.9D,
                    "rule_overlap_verified",
                    "综合结论",
                    0
            );
            DeepResearchSynthesisResult result = new DeepResearchSynthesisResult();
            result.setAnswerMarkdown("# 深度研究结论\n\n- 综合结论 [[payment-routing]]");
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
            AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                    result.getAnswerMarkdown(),
                    List.of(answerProjection)
            );
            result.setAnswerProjectionBundle(answerProjectionBundle);
            result.setCitationCheckReport(new CitationCheckReport(
                    result.getAnswerMarkdown(),
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
            ));
            result.setPartialAnswer(false);
            result.setHasConflicts(false);
            result.setEvidenceCardCount(evidenceLedger.cardCount());
            return result;
        }
    }

    private static class NoProjectionSynthesizer extends DeepResearchSynthesizer {

        private NoProjectionSynthesizer() {
            super(null);
        }

        @Override
        public DeepResearchSynthesisResult synthesize(
                String question,
                List<LayerSummary> layerSummaries,
                EvidenceLedger evidenceLedger
        ) {
            DeepResearchSynthesisResult result = new DeepResearchSynthesisResult();
            result.setAnswerMarkdown("# 深度研究结论\n\n- 内部草稿仍引用 ev#1");
            result.setPartialAnswer(false);
            result.setHasConflicts(false);
            result.setEvidenceCardCount(evidenceLedger.cardCount());
            return result;
        }
    }

    private static class RecordingDeepResearchAuditPersistenceService extends DeepResearchAuditPersistenceService {

        private boolean called;

        private int lastEvidenceCardCount;

        private RecordingDeepResearchAuditPersistenceService() {
            super((DeepResearchRunJdbcRepository) null, null, null, null, null, null, null, null);
        }

        @Override
        public DeepResearchAuditSnapshot persist(
                String queryId,
                String question,
                String routeReason,
                LayeredResearchPlan plan,
                EvidenceLedger evidenceLedger,
                String answerMarkdown,
                CitationCheckReport citationCheckReport,
                AnswerProjectionBundle answerProjectionBundle,
                int llmCallCount,
                boolean partialAnswer,
                boolean hasConflicts
        ) {
            called = true;
            lastEvidenceCardCount = evidenceLedger == null ? 0 : evidenceLedger.cardCount();
            return new DeepResearchAuditSnapshot(99L, lastEvidenceCardCount);
        }
    }

    private static class FixedKnowledgeSearchService extends KnowledgeSearchService {

        private FixedKnowledgeSearchService() {
            super(null, null, null, null, null);
        }

        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            return List.of(new QueryArticleHit(
                    1L,
                    "payment-routing",
                    "payment-routing",
                    "Payment Routing",
                    "RoutePlanner -> PaymentService.plan",
                    "{\"description\":\"支付路由\"}",
                    List.of("src/main/java/payment/RoutePlanner.java"),
                    3.0D
            ));
        }
    }
}
