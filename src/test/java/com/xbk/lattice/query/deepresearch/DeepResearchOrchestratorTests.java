package com.xbk.lattice.query.deepresearch;

import com.xbk.lattice.api.query.QueryRequest;
import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.infra.persistence.DeepResearchEvidenceCardJdbcRepository;
import com.xbk.lattice.infra.persistence.DeepResearchRunJdbcRepository;
import com.xbk.lattice.query.citation.Citation;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationSourceType;
import com.xbk.lattice.query.citation.CitationValidationResult;
import com.xbk.lattice.query.citation.CitationValidationStatus;
import com.xbk.lattice.query.citation.ClaimSegment;
import com.xbk.lattice.query.citation.QueryAnswerAuditPersistenceService;
import com.xbk.lattice.query.citation.QueryAnswerAuditSnapshot;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchAuditSnapshot;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchSynthesisResult;
import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceFinding;
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
        DeepResearchRouter deepResearchRouter = new DeepResearchRouter();
        DeepResearchPlanner deepResearchPlanner = new DeepResearchPlanner();
        DeepResearchWorkingSetStore deepResearchWorkingSetStore = new InMemoryDeepResearchWorkingSetStore();
        QueryWorkingSetStore queryWorkingSetStore = new InMemoryQueryWorkingSetStore();
        DeepResearchExecutionRegistry deepResearchExecutionRegistry = new DeepResearchExecutionRegistry();
        DeepResearchGraphDefinitionFactory graphDefinitionFactory = new DeepResearchGraphDefinitionFactory(
                new DeepResearchStateMapper(),
                deepResearchWorkingSetStore,
                queryWorkingSetStore,
                deepResearchExecutionRegistry,
                new FixedResearcherService(),
                new FixedSynthesizer()
        );
        RecordingQueryAnswerAuditPersistenceService queryAnswerAuditPersistenceService =
                new RecordingQueryAnswerAuditPersistenceService();
        RecordingDeepResearchAuditPersistenceService deepResearchAuditPersistenceService =
                new RecordingDeepResearchAuditPersistenceService();
        DeepResearchOrchestrator deepResearchOrchestrator = new DeepResearchOrchestrator(
                deepResearchRouter,
                deepResearchPlanner,
                graphDefinitionFactory,
                new DeepResearchStateMapper(),
                deepResearchWorkingSetStore,
                deepResearchExecutionRegistry,
                queryWorkingSetStore,
                queryAnswerAuditPersistenceService,
                deepResearchAuditPersistenceService,
                new FixedKnowledgeSearchService()
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
        assertThat(queryAnswerAuditPersistenceService.called).isTrue();
        assertThat(deepResearchAuditPersistenceService.called).isTrue();
        assertThat(deepResearchAuditPersistenceService.lastEvidenceCardCount).isEqualTo(3);
        assertThat(queryResponse.getSources()).isNotEmpty();
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
            EvidenceFinding evidenceFinding = new EvidenceFinding();
            evidenceFinding.setClaim(task.getQuestion() + " 的结论");
            evidenceFinding.setQuote("RoutePlanner -> PaymentService.plan");
            evidenceFinding.setSourceType("ARTICLE");
            evidenceFinding.setSourceId("payment-routing");
            evidenceFinding.setConfidence(0.9D);

            EvidenceCard evidenceCard = new EvidenceCard();
            evidenceCard.setEvidenceId(executionContext.nextEvidenceId());
            evidenceCard.setLayerIndex(layerIndex);
            evidenceCard.setTaskId(task.getTaskId());
            evidenceCard.setScope(task.getQuestion());
            evidenceCard.getFindings().add(evidenceFinding);
            evidenceCard.getSelectedArticleKeys().add("payment-routing");
            for (EvidenceCard preferredCard : preferredCards) {
                evidenceCard.getRelatedLeads().add(preferredCard.getEvidenceId());
            }
            return evidenceCard;
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
            result.setCitationCheckReport(new CitationCheckReport(
                    result.getAnswerMarkdown(),
                    List.of(claimSegment),
                    List.of(validationResult),
                    1,
                    0,
                    0,
                    false,
                    1.0D,
                    0
            ));
            result.setPartialAnswer(false);
            result.setHasConflicts(false);
            result.setEvidenceCardCount(evidenceLedger.cardCount());
            return result;
        }
    }

    private static class RecordingQueryAnswerAuditPersistenceService extends QueryAnswerAuditPersistenceService {

        private boolean called;

        private RecordingQueryAnswerAuditPersistenceService() {
            super(null, null, null);
        }

        @Override
        public QueryAnswerAuditSnapshot persist(
                String queryId,
                int answerVersion,
                String question,
                String answerMarkdown,
                com.xbk.lattice.query.domain.AnswerOutcome answerOutcome,
                com.xbk.lattice.query.domain.GenerationMode generationMode,
                String reviewStatus,
                boolean cacheable,
                String routeType,
                CitationCheckReport report
        ) {
            called = true;
            return new QueryAnswerAuditSnapshot(88L, answerVersion, report);
        }
    }

    private static class RecordingDeepResearchAuditPersistenceService extends DeepResearchAuditPersistenceService {

        private boolean called;

        private int lastEvidenceCardCount;

        private RecordingDeepResearchAuditPersistenceService() {
            super((DeepResearchRunJdbcRepository) null, (DeepResearchEvidenceCardJdbcRepository) null);
        }

        @Override
        public DeepResearchAuditSnapshot persist(
                String queryId,
                String question,
                String routeReason,
                LayeredResearchPlan plan,
                EvidenceLedger evidenceLedger,
                int llmCallCount,
                double citationCoverage,
                boolean partialAnswer,
                boolean hasConflicts,
                Long finalAnswerAuditId
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
