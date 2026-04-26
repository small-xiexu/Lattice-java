package com.xbk.lattice.query.deepresearch;

import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.ResearchTask;
import com.xbk.lattice.query.deepresearch.service.DeepResearchExecutionContext;
import com.xbk.lattice.query.deepresearch.service.DeepResearchResearcherService;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchorSourceType;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import com.xbk.lattice.query.service.AnswerGenerationService;
import com.xbk.lattice.query.service.KnowledgeSearchService;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryEvidenceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepResearchResearcherService 测试
 *
 * 职责：验证研究员输出优先落到 v2.6 FactFinding / EvidenceAnchor 结构
 *
 * @author xiexu
 */
class DeepResearchResearcherServiceTests {

    /**
     * 验证 researcher 会产出结构化 finding/anchor，并且 GRAPH 证据不会升级成最终 projection 候选。
     */
    @Test
    void shouldProduceStructuredFindingsAndKeepGraphEvidenceInternal() {
        DeepResearchResearcherService researcherService = new DeepResearchResearcherService(
                new FixedKnowledgeSearchService(),
                new FixedAnswerGenerationService()
        );
        ResearchTask researchTask = new ResearchTask();
        researchTask.setTaskId("task-payment-retry");
        researchTask.setQuestion("PaymentService 默认重试次数是多少");
        DeepResearchExecutionContext executionContext = new DeepResearchExecutionContext(
                2,
                System.currentTimeMillis() + 60_000L
        );

        EvidenceCard evidenceCard = researcherService.research(
                "dr-q1",
                researchTask,
                0,
                null,
                List.of(),
                executionContext
        );
        EvidenceLedger evidenceLedger = new EvidenceLedger();
        evidenceLedger.addCard(evidenceCard);

        assertThat(evidenceCard.getFactFindings()).hasSize(1);
        assertThat(evidenceCard.getEvidenceAnchors()).hasSize(1);
        assertThat(evidenceCard.getTaskHits()).hasSize(2);
        assertThat(evidenceCard.getTaskHits().get(0).getArticleKey()).isEqualTo("payment-routing");
        assertThat(evidenceCard.getEvidenceAnchors()).extracting(anchor -> anchor.getSourceType())
                .containsExactly(EvidenceAnchorSourceType.ARTICLE);
        assertThat(evidenceLedger.getProjectionCandidates()).hasSize(1);
        assertThat(evidenceLedger.getProjectionCandidates().get(0).getPreferredCitationFormat())
                .isEqualTo(ProjectionCitationFormat.ARTICLE);
    }

    /**
     * 验证结构化摘要为空时会保留 anchor-only partial result，避免检索证据丢失。
     */
    @Test
    void shouldKeepAnchorOnlyPartialResultWhenStructuredExtractionFails() {
        DeepResearchResearcherService researcherService = new DeepResearchResearcherService(
                new FixedKnowledgeSearchService(),
                new EmptyAnswerGenerationService()
        );
        ResearchTask researchTask = new ResearchTask();
        researchTask.setTaskId("task-payment-retry");
        researchTask.setQuestion("PaymentService 默认重试次数是多少");
        DeepResearchExecutionContext executionContext = new DeepResearchExecutionContext(
                2,
                System.currentTimeMillis() + 60_000L
        );

        EvidenceCard evidenceCard = researcherService.research(
                "dr-q2",
                researchTask,
                0,
                null,
                List.of(),
                executionContext
        );

        assertThat(evidenceCard.getFactFindings()).isEmpty();
        assertThat(evidenceCard.getEvidenceAnchors()).hasSize(2);
        assertThat(evidenceCard.getGaps()).contains("insufficient_grounding");
        assertThat(evidenceCard.getFollowUps()).contains("retry_structured_fact_extraction");
    }

    /**
     * 验证 researcher 能直接消费 v2.6 JSON 结构化输出。
     */
    @Test
    void shouldParseStructuredJsonEvidenceFromResearcherOutput() {
        JsonAnswerGenerationService answerGenerationService = new JsonAnswerGenerationService(validEvidenceJson());
        DeepResearchResearcherService researcherService = new DeepResearchResearcherService(
                new FixedKnowledgeSearchService(),
                answerGenerationService
        );
        ResearchTask researchTask = new ResearchTask();
        researchTask.setTaskId("task-payment-retry");
        researchTask.setQuestion("PaymentService 默认重试次数是多少");
        DeepResearchExecutionContext executionContext = new DeepResearchExecutionContext(
                2,
                System.currentTimeMillis() + 60_000L
        );

        EvidenceCard evidenceCard = researcherService.research(
                "dr-q3",
                researchTask,
                0,
                null,
                List.of(),
                executionContext
        );

        assertThat(evidenceCard.getEvidenceAnchors()).hasSize(1);
        assertThat(evidenceCard.getFactFindings()).hasSize(1);
        assertThat(evidenceCard.getFactFindings().get(0).getFactKey())
                .isEqualTo("payment.retry.maxAttempts.current_config");
        assertThat(answerGenerationService.callCount).isEqualTo(1);
    }

    /**
     * 验证结构化 JSON 解析失败时只执行一次 schema repair 重试。
     */
    @Test
    void shouldRetryOnceWhenStructuredJsonSchemaIsInvalid() {
        RetryAnswerGenerationService answerGenerationService = new RetryAnswerGenerationService();
        DeepResearchResearcherService researcherService = new DeepResearchResearcherService(
                new FixedKnowledgeSearchService(),
                answerGenerationService
        );
        ResearchTask researchTask = new ResearchTask();
        researchTask.setTaskId("task-payment-retry");
        researchTask.setQuestion("PaymentService 默认重试次数是多少");
        DeepResearchExecutionContext executionContext = new DeepResearchExecutionContext(
                3,
                System.currentTimeMillis() + 60_000L
        );

        EvidenceCard evidenceCard = researcherService.research(
                "dr-q4",
                researchTask,
                0,
                null,
                List.of(),
                executionContext
        );

        assertThat(evidenceCard.getFactFindings()).hasSize(1);
        assertThat(evidenceCard.getGaps()).isEmpty();
        assertThat(evidenceCard.getFollowUps()).contains("schema_repair_attempted");
        assertThat(answerGenerationService.callCount).isEqualTo(2);
        assertThat(executionContext.llmCallCount()).isEqualTo(2);
    }

    /**
     * 验证真实 fused 检索低分也会映射成可投影 confidence，避免 Deep Research 误判为无证据。
     */
    @Test
    void shouldNormalizeLowPositiveRetrievalScoresIntoProjectableConfidence() {
        DeepResearchResearcherService researcherService = new DeepResearchResearcherService(
                new LowScoreKnowledgeSearchService(),
                new FixedAnswerGenerationService()
        );
        ResearchTask researchTask = new ResearchTask();
        researchTask.setTaskId("task-payment-retry");
        researchTask.setQuestion("PaymentService 默认重试次数是多少");
        DeepResearchExecutionContext executionContext = new DeepResearchExecutionContext(
                2,
                System.currentTimeMillis() + 60_000L
        );

        EvidenceCard evidenceCard = researcherService.research(
                "dr-q5",
                researchTask,
                0,
                null,
                List.of(),
                executionContext
        );
        EvidenceLedger evidenceLedger = new EvidenceLedger();
        evidenceLedger.addCard(evidenceCard);

        assertThat(evidenceCard.getFactFindings()).isNotEmpty();
        assertThat(evidenceCard.getFactFindings().get(0).getConfidence()).isGreaterThan(0.55D);
        assertThat(evidenceCard.getEvidenceAnchors().get(0).getRetrievalScore()).isGreaterThan(0.55D);
        assertThat(evidenceCard.getEvidenceAnchors().get(0).getSourceId()).isEqualTo("payment-routing");
        assertThat(evidenceLedger.getProjectionCandidates()).hasSize(1);
        assertThat(evidenceLedger.getProjectionCandidates().get(0).getPreferredCitationFormat())
                .isEqualTo(ProjectionCitationFormat.ARTICLE);
    }

    /**
     * 验证 researcher 会剥掉上游 answer 中已有的 citation literal，避免 Deep Research 最终答案重复引用。
     */
    @Test
    void shouldStripExistingCitationLiteralFromClaimText() {
        DeepResearchResearcherService researcherService = new DeepResearchResearcherService(
                new FixedKnowledgeSearchService(),
                new CitationDecoratedAnswerGenerationService()
        );
        ResearchTask researchTask = new ResearchTask();
        researchTask.setTaskId("task-payment-retry");
        researchTask.setQuestion("PaymentService 默认重试次数是多少");
        DeepResearchExecutionContext executionContext = new DeepResearchExecutionContext(
                2,
                System.currentTimeMillis() + 60_000L
        );

        EvidenceCard evidenceCard = researcherService.research(
                "dr-q6",
                researchTask,
                0,
                null,
                List.of(),
                executionContext
        );

        assertThat(evidenceCard.getFactFindings()).isNotEmpty();
        assertThat(evidenceCard.getFactFindings().get(0).getClaimText())
                .doesNotContain("[[payment-routing]]", "[→ src/main/java/payment/PaymentService.java]");
    }

    /**
     * 验证带有硬 token 的 claim 会过滤掉不相关 hit，避免把冲突文档误挂到补偿重试结论上。
     */
    @Test
    void shouldFilterIrrelevantHitsForClaimWithHardFactTokens() {
        DeepResearchResearcherService researcherService = new DeepResearchResearcherService(
                new MixedKnowledgeSearchService(),
                new RetryQueueAnswerGenerationService()
        );
        ResearchTask researchTask = new ResearchTask();
        researchTask.setTaskId("task-compensation-retry");
        researchTask.setQuestion("补偿重试策略是什么");
        DeepResearchExecutionContext executionContext = new DeepResearchExecutionContext(
                2,
                System.currentTimeMillis() + 60_000L
        );

        EvidenceCard evidenceCard = researcherService.research(
                "dr-q7",
                researchTask,
                0,
                null,
                List.of(),
                executionContext
        );

        assertThat(evidenceCard.getEvidenceAnchors()).extracting(anchor -> anchor.getSourceId())
                .doesNotContain("conflict-lock");
        assertThat(evidenceCard.getFactFindings()).hasSize(2);
    }

    /**
     * 验证 researcher 会在任务级先剔除只在正文顺带提及 RoutePlanner 的笔记，避免污染路径结论。
     */
    @Test
    void shouldFilterTaskLevelIrrelevantRoutePlannerHits() {
        DeepResearchResearcherService researcherService = new DeepResearchResearcherService(
                new RoutePlannerKnowledgeSearchService(),
                new RoutePlannerAnswerGenerationService()
        );
        ResearchTask researchTask = new ResearchTask();
        researchTask.setTaskId("task-route-planner-path");
        researchTask.setQuestion("RoutePlanner 暴露了什么路径");
        DeepResearchExecutionContext executionContext = new DeepResearchExecutionContext(
                2,
                System.currentTimeMillis() + 60_000L
        );

        EvidenceCard evidenceCard = researcherService.research(
                "dr-q7b",
                researchTask,
                0,
                null,
                List.of(),
                executionContext
        );

        assertThat(evidenceCard.getTaskHits()).hasSize(1);
        assertThat(evidenceCard.getTaskHits()).extracting(taskHit -> taskHit.getArticleKey())
                .containsExactly("routeplanner");
        assertThat(evidenceCard.getSelectedArticleKeys()).containsExactly("routeplanner");
        assertThat(evidenceCard.getEvidenceAnchors()).extracting(anchor -> anchor.getSourceId())
                .containsExactly("routeplanner");
    }

    /**
     * 验证冲突总结会按 hit 提取更窄的 claim，避免同一条 finding 同时包含两类冲突实现细节。
     */
    @Test
    void shouldPreferFocusedClaimSnippetPerRelevantHit() {
        DeepResearchResearcherService researcherService = new DeepResearchResearcherService(
                new ConflictKnowledgeSearchService(),
                new ConflictSummaryAnswerGenerationService()
        );
        ResearchTask researchTask = new ResearchTask();
        researchTask.setTaskId("task-inventory-lock");
        researchTask.setQuestion("库存并发到底用什么锁");
        DeepResearchExecutionContext executionContext = new DeepResearchExecutionContext(
                2,
                System.currentTimeMillis() + 60_000L
        );

        EvidenceCard evidenceCard = researcherService.research(
                "dr-q8",
                researchTask,
                0,
                null,
                List.of(),
                executionContext
        );

        assertThat(evidenceCard.getFactFindings()).extracting(finding -> finding.getClaimText())
                .anySatisfy(claimText -> assertThat(claimText).contains("Redis 分布式锁"))
                .anySatisfy(claimText -> assertThat(claimText).contains("inventory_lock_version"));
        assertThat(evidenceCard.getFactFindings()).extracting(finding -> finding.getClaimText())
                .allSatisfy(claimText -> assertThat(
                        claimText.contains("Redis 分布式锁") && claimText.contains("inventory_lock_version")
                ).isFalse());
        assertThat(evidenceCard.getFactFindings()).extracting(finding -> finding.getClaimText())
                .allSatisfy(claimText -> assertThat(claimText).doesNotContain("现有证据里存在", "不能给出单一"));
        assertThat(evidenceCard.getFactFindings()).extracting(finding -> finding.getClaimText())
                .allSatisfy(claimText -> assertThat(claimText).doesNotContain("--- title:", "referential_keywords", "compiled_at"));
    }

    /**
     * 验证冲突模式下会剥离 front matter，而不是把 YAML 头写进 finding。
     */
    @Test
    void shouldStripFrontMatterFromConflictClaimSnippet() {
        DeepResearchResearcherService researcherService = new DeepResearchResearcherService(
                new ConflictKnowledgeSearchService(),
                new ConflictSummaryAnswerGenerationService()
        );
        ResearchTask researchTask = new ResearchTask();
        researchTask.setTaskId("task-inventory-lock");
        researchTask.setQuestion("库存并发到底用什么锁");
        DeepResearchExecutionContext executionContext = new DeepResearchExecutionContext(
                2,
                System.currentTimeMillis() + 60_000L
        );

        EvidenceCard evidenceCard = researcherService.research(
                "dr-q9",
                researchTask,
                0,
                null,
                List.of(),
                executionContext
        );

        assertThat(evidenceCard.getFactFindings()).extracting(finding -> finding.getClaimText())
                .allSatisfy(claimText -> assertThat(claimText).doesNotContain("--- title:", "summary:", "referential_keywords"));
    }

    /**
     * 验证 claim 同时包含数字和 snake_case 时，优先用 snake_case 命中正文句子，而不是被通用数字误带偏。
     */
    @Test
    void shouldPreferSnakeCaseTokenOverNumericLiteralWhenFocusingClaim() {
        DeepResearchResearcherService researcherService = new DeepResearchResearcherService(
                new ConflictKnowledgeSearchService(),
                new NumericAndSnakeCaseAnswerGenerationService()
        );
        ResearchTask researchTask = new ResearchTask();
        researchTask.setTaskId("task-inventory-lock");
        researchTask.setQuestion("库存并发到底用什么锁");
        DeepResearchExecutionContext executionContext = new DeepResearchExecutionContext(
                2,
                System.currentTimeMillis() + 60_000L
        );

        EvidenceCard evidenceCard = researcherService.research(
                "dr-q10",
                researchTask,
                0,
                null,
                List.of(),
                executionContext
        );

        assertThat(evidenceCard.getFactFindings()).extracting(finding -> finding.getClaimText())
                .anySatisfy(claimText -> assertThat(claimText).contains("inventory_lock_version"));
    }

    private static class FixedKnowledgeSearchService extends KnowledgeSearchService {

        private FixedKnowledgeSearchService() {
            super(null, null, null, null, null);
        }

        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            return List.of(
                    new QueryArticleHit(
                            QueryEvidenceType.ARTICLE,
                            1L,
                            "payment-routing",
                            "payment-routing",
                            "Payment Routing",
                            "PaymentService 默认最多重试 5 次。",
                            "{}",
                            List.of("src/main/java/payment/PaymentService.java"),
                            0.95D
                    ),
                    new QueryArticleHit(
                            QueryEvidenceType.GRAPH,
                            null,
                            null,
                            "payment.retry.maxAttempts.current_config",
                            "payment.retry.maxAttempts",
                            "payment.retry.maxAttempts.current_config=5",
                            "{}",
                            List.of(),
                            0.80D
                    )
            );
        }
    }

    private static class FixedAnswerGenerationService extends AnswerGenerationService {

        @Override
        public QueryAnswerPayload generatePayload(
                String scopeId,
                String scene,
                String agentRole,
                String question,
                List<QueryArticleHit> queryArticleHits
        ) {
            return QueryAnswerPayload.ruleBased(
                    "PaymentService 默认最多重试 5 次",
                    AnswerOutcome.PARTIAL_ANSWER
            );
        }
    }

    private static class LowScoreKnowledgeSearchService extends KnowledgeSearchService {

        private LowScoreKnowledgeSearchService() {
            super(null, null, null, null, null);
        }

        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            return List.of(
                    new QueryArticleHit(
                            QueryEvidenceType.ARTICLE,
                            1L,
                            "payment-routing",
                            "payment-routing",
                            "Payment Routing",
                            "PaymentService 默认最多重试 5 次。",
                            "{}",
                            List.of("src/main/java/payment/PaymentService.java"),
                            0.045D
                    ),
                    new QueryArticleHit(
                            QueryEvidenceType.GRAPH,
                            null,
                            null,
                            "payment.retry.maxAttempts.current_config",
                            "payment.retry.maxAttempts",
                            "payment.retry.maxAttempts.current_config=5",
                            "{}",
                            List.of(),
                            0.030D
                    )
            );
        }
    }

    private static class CitationDecoratedAnswerGenerationService extends AnswerGenerationService {

        @Override
        public QueryAnswerPayload generatePayload(
                String scopeId,
                String scene,
                String agentRole,
                String question,
                List<QueryArticleHit> queryArticleHits
        ) {
            return QueryAnswerPayload.ruleBased(
                    "PaymentService 默认最多重试 5 次 [[payment-routing]] [→ src/main/java/payment/PaymentService.java]",
                    AnswerOutcome.PARTIAL_ANSWER
            );
        }
    }

    private static class RetryQueueAnswerGenerationService extends AnswerGenerationService {

        @Override
        public QueryAnswerPayload generatePayload(
                String scopeId,
                String scene,
                String agentRole,
                String question,
                List<QueryArticleHit> queryArticleHits
        ) {
            return QueryAnswerPayload.ruleBased(
                    "当补偿失败后会进入 retry_queue 进行异步重试，并由 RetryWorker 负责消费处理",
                    AnswerOutcome.PARTIAL_ANSWER
            );
        }
    }

    private static class MixedKnowledgeSearchService extends KnowledgeSearchService {

        private MixedKnowledgeSearchService() {
            super(null, null, null, null, null);
        }

        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            return List.of(
                    new QueryArticleHit(
                            QueryEvidenceType.ARTICLE,
                            1L,
                            "conflict-lock",
                            "conflict-lock",
                            "Conflict Lock",
                            "库存并发控制采用 Redis 分布式锁串行化处理。",
                            "{}",
                            List.of("conflict-lock.md"),
                            0.04D
                    ),
                    new QueryArticleHit(
                            QueryEvidenceType.ARTICLE,
                            1L,
                            "readme",
                            "readme",
                            "Readme",
                            "补偿失败后会进入 retry_queue 异步重试，由 RetryWorker 消费。",
                            "{}",
                            List.of("README.md"),
                            0.04D
                    ),
                    new QueryArticleHit(
                            QueryEvidenceType.ARTICLE,
                            1L,
                            "research-notes",
                            "research-notes",
                            "Research Notes",
                            "RetryWorker 会从 retry_queue 消费补偿任务。",
                            "{}",
                            List.of("research-notes.md"),
                            0.04D
                    )
            );
        }
    }

    private static class ConflictSummaryAnswerGenerationService extends AnswerGenerationService {

        @Override
        public QueryAnswerPayload generatePayload(
                String scopeId,
                String scene,
                String agentRole,
                String question,
                List<QueryArticleHit> queryArticleHits
        ) {
            return QueryAnswerPayload.ruleBased(
                    "关于库存并发控制存在冲突：一份资料指出库存并发控制采用 Redis 分布式锁做串行化处理；另一份资料则指出库存扣减采用基于版本号校验的乐观锁，字段为 inventory_lock_version。",
                    AnswerOutcome.PARTIAL_ANSWER
            );
        }
    }

    private static class RoutePlannerAnswerGenerationService extends AnswerGenerationService {

        @Override
        public QueryAnswerPayload generatePayload(
                String scopeId,
                String scene,
                String agentRole,
                String question,
                List<QueryArticleHit> queryArticleHits
        ) {
            return QueryAnswerPayload.ruleBased(
                    "RoutePlanner 负责暴露 `/payments` 路径。",
                    AnswerOutcome.PARTIAL_ANSWER
            );
        }
    }

    private static class NumericAndSnakeCaseAnswerGenerationService extends AnswerGenerationService {

        @Override
        public QueryAnswerPayload generatePayload(
                String scopeId,
                String scene,
                String agentRole,
                String question,
                List<QueryArticleHit> queryArticleHits
        ) {
            return QueryAnswerPayload.ruleBased(
                    "库存扣减采用基于版本号校验的乐观锁，默认重试 5 次，字段为 inventory_lock_version。",
                    AnswerOutcome.PARTIAL_ANSWER
            );
        }
    }

    private static class RoutePlannerKnowledgeSearchService extends KnowledgeSearchService {

        private RoutePlannerKnowledgeSearchService() {
            super(null, null, null, null, null);
        }

        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            return List.of(
                    new QueryArticleHit(
                            QueryEvidenceType.ARTICLE,
                            1L,
                            "routeplanner",
                            "routeplanner",
                            "RoutePlanner",
                            "RoutePlanner 负责暴露 `/payments` 路径。",
                            "{}",
                            List.of("src/main/java/payment/RoutePlanner.java"),
                            0.90D
                    ),
                    new QueryArticleHit(
                            QueryEvidenceType.ARTICLE,
                            2L,
                            "research-notes",
                            "research-notes",
                            "Research Notes",
                            "Research Notes 顺带提到 RoutePlanner 会把请求委托给 PaymentService。",
                            "{}",
                            List.of("research-notes.md"),
                            0.60D
                    )
            );
        }
    }

    private static class ConflictKnowledgeSearchService extends KnowledgeSearchService {

        private ConflictKnowledgeSearchService() {
            super(null, null, null, null, null);
        }

        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            return List.of(
                    new QueryArticleHit(
                            QueryEvidenceType.ARTICLE,
                            1L,
                            "conflict-lock",
                            "conflict-lock",
                            "Conflict Lock",
                            "库存并发控制采用 Redis 分布式锁串行化处理。",
                            "{}",
                            List.of("conflict-lock.md"),
                            0.04D
                    ),
                    new QueryArticleHit(
                            QueryEvidenceType.ARTICLE,
                            1L,
                            "readme",
                            "readme",
                            "Readme",
                            "库存扣减采用基于版本号校验的乐观锁，字段为 inventory_lock_version。",
                            "{}",
                            List.of("README.md"),
                            0.04D
                    )
            );
        }
    }


    private static class EmptyAnswerGenerationService extends AnswerGenerationService {

        @Override
        public QueryAnswerPayload generatePayload(
                String scopeId,
                String scene,
                String agentRole,
                String question,
                List<QueryArticleHit> queryArticleHits
        ) {
            return QueryAnswerPayload.ruleBased("", AnswerOutcome.PARTIAL_ANSWER);
        }
    }

    private static class JsonAnswerGenerationService extends AnswerGenerationService {

        private final String answerMarkdown;

        private int callCount;

        private JsonAnswerGenerationService(String answerMarkdown) {
            this.answerMarkdown = answerMarkdown;
        }

        @Override
        public QueryAnswerPayload generatePayload(
                String scopeId,
                String scene,
                String agentRole,
                String question,
                List<QueryArticleHit> queryArticleHits
        ) {
            callCount++;
            return QueryAnswerPayload.ruleBased(answerMarkdown, AnswerOutcome.PARTIAL_ANSWER);
        }
    }

    private static class RetryAnswerGenerationService extends AnswerGenerationService {

        private int callCount;

        @Override
        public QueryAnswerPayload generatePayload(
                String scopeId,
                String scene,
                String agentRole,
                String question,
                List<QueryArticleHit> queryArticleHits
        ) {
            callCount++;
            if (callCount == 1) {
                return QueryAnswerPayload.ruleBased("{\"factFindings\":[", AnswerOutcome.PARTIAL_ANSWER);
            }
            return QueryAnswerPayload.ruleBased(validEvidenceJson(), AnswerOutcome.PARTIAL_ANSWER);
        }
    }

    private static String validEvidenceJson() {
        return """
                {
                  "evidenceAnchors": [
                    {
                      "anchorId": "ev#json",
                      "sourceType": "ARTICLE",
                      "sourceId": "payment-routing",
                      "quoteText": "PaymentService 默认最多重试 5 次。",
                      "retrievalScore": 0.95
                    }
                  ],
                  "factFindings": [
                    {
                      "findingId": "ev#json-finding",
                      "subject": "payment.retry",
                      "predicate": "maxAttempts",
                      "qualifier": "current_config",
                      "factKey": "payment.retry.maxAttempts.current_config",
                      "valueText": "5",
                      "valueType": "NUMBER",
                      "unit": "times",
                      "claimText": "PaymentService 默认最多重试 5 次",
                      "confidence": 0.95,
                      "supportLevel": "DIRECT",
                      "anchorIds": ["ev#json"]
                    }
                  ]
                }
                """;
    }
}
