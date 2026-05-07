package com.xbk.lattice.query.deepresearch;

import com.xbk.lattice.query.citation.Citation;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationSourceType;
import com.xbk.lattice.query.citation.CitationValidationResult;
import com.xbk.lattice.query.citation.CitationValidationStatus;
import com.xbk.lattice.query.citation.ClaimSegment;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchAuditSnapshot;
import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.LayeredResearchPlan;
import com.xbk.lattice.query.deepresearch.domain.ResearchLayer;
import com.xbk.lattice.query.deepresearch.domain.ResearchTask;
import com.xbk.lattice.query.deepresearch.domain.ResearchTaskHit;
import com.xbk.lattice.query.deepresearch.service.DeepResearchAuditPersistenceService;
import com.xbk.lattice.query.evidence.domain.AnswerProjection;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchorSourceType;
import com.xbk.lattice.query.evidence.domain.FactFinding;
import com.xbk.lattice.query.evidence.domain.FactValueType;
import com.xbk.lattice.query.evidence.domain.FindingSupportLevel;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import com.xbk.lattice.query.evidence.domain.ProjectionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DeepResearchAuditPersistenceService 测试
 *
 * 职责：验证 v2.6 Deep Research 持久化顺序可形成单 run 闭环
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class DeepResearchAuditPersistenceServiceTests {

    @Autowired
    private DeepResearchAuditPersistenceService deepResearchAuditPersistenceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 清理 Deep Research 审计表，避免测试间共享同一个 schema 的历史数据。
     */
    @BeforeEach
    void cleanAuditTables() {
        jdbcTemplate.execute("truncate table deep_research_runs cascade");
    }

    /**
     * 验证 Deep Research 持久化会按 v2.6 顺序写入 run、audit、claim、citation 与 projection。
     */
    @Test
    void shouldPersistDeepResearchRunAuditAndProjectionInSingleRunScope() {
        LayeredResearchPlan plan = buildPlan();
        EvidenceLedger evidenceLedger = buildEvidenceLedger();
        CitationCheckReport report = buildCitationCheckReport();
        AnswerProjectionBundle answerProjectionBundle = buildAnswerProjectionBundle();

        DeepResearchAuditSnapshot snapshot = deepResearchAuditPersistenceService.persist(
                "dr-persist-q1",
                "PaymentService 默认路由是什么",
                "complexity_gate",
                plan,
                evidenceLedger,
                "# 深度研究结论\n\n- PaymentService 默认路由是标准链路 [[payment-routing]]",
                report,
                answerProjectionBundle,
                2,
                false,
                false
        );

        assertThat(snapshot.getRunId()).isNotNull();
        assertThat(snapshot.getEvidenceCardCount()).isEqualTo(1);

        Long auditId = jdbcTemplate.queryForObject(
                "select final_answer_audit_id from deep_research_runs where run_id = ?",
                Long.class,
                snapshot.getRunId()
        );

        assertThat(auditId).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select deep_research_run_id from query_answer_audits where audit_id = ?",
                Long.class,
                auditId
        )).isEqualTo(snapshot.getRunId());
        assertThat(count("deep_research_tasks")).isEqualTo(1);
        assertThat(count("deep_research_task_hits")).isEqualTo(1);
        assertThat(count("deep_research_findings")).isEqualTo(1);
        assertThat(count("deep_research_evidence_anchors")).isEqualTo(1);
        assertThat(count("deep_research_evidence_anchor_validations")).isEqualTo(1);
        assertThat(count("query_answer_claims")).isEqualTo(1);
        assertThat(count("query_answer_citations")).isEqualTo(1);
        assertThat(count("deep_research_answer_projections")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select validated_by from query_answer_citations where audit_id = ?",
                String.class,
                auditId
        )).isEqualTo("RULE");
        assertThat(jdbcTemplate.queryForObject(
                "select validated_by from deep_research_evidence_anchor_validations where run_id = ?",
                String.class,
                snapshot.getRunId()
        )).isEqualTo("STRUCTURE_RULE");
        assertThat(jdbcTemplate.queryForObject(
                "select citation_literal from deep_research_answer_projections where run_id = ?",
                String.class,
                snapshot.getRunId()
        )).isEqualTo("[[payment-routing]]");
    }

    /**
     * 验证同一答案审计内不能写入重复 ACTIVE citation literal。
     */
    @Test
    void shouldRejectDuplicateActiveProjectionLiteralInSameAuditScope() {
        LayeredResearchPlan plan = buildPlan();
        EvidenceLedger evidenceLedger = buildEvidenceLedgerWithSecondAnchor();
        CitationCheckReport report = buildCitationCheckReport();
        AnswerProjectionBundle duplicateProjectionBundle = buildDuplicateActiveLiteralProjectionBundle();

        assertThatThrownBy(() -> deepResearchAuditPersistenceService.persist(
                "dr-persist-q2",
                "PaymentService 默认路由是什么",
                "complexity_gate",
                plan,
                evidenceLedger,
                "# 深度研究结论\n\n- PaymentService 默认路由是标准链路 [[payment-routing]]",
                report,
                duplicateProjectionBundle,
                2,
                false,
                false
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * 验证 projection repair 历史会按 append-only 状态落库。
     */
    @Test
    void shouldPersistProjectionRepairHistory() {
        LayeredResearchPlan plan = buildPlan();
        EvidenceLedger evidenceLedger = buildEvidenceLedger();
        CitationCheckReport report = buildUnsupportedNoCitationReport();
        AnswerProjectionBundle projectionHistoryBundle = buildRemovedProjectionHistoryBundle();

        DeepResearchAuditSnapshot snapshot = deepResearchAuditPersistenceService.persist(
                "dr-persist-q3",
                "PaymentService 默认路由是什么",
                "complexity_gate",
                plan,
                evidenceLedger,
                "PaymentService 默认路由是标准链路（当前证据不足）",
                report,
                projectionHistoryBundle,
                2,
                true,
                false
        );

        assertThat(jdbcTemplate.queryForList(
                "select status from deep_research_answer_projections where run_id = ? order by projection_ordinal",
                String.class,
                snapshot.getRunId()
        )).containsExactly("REPLACED", "REMOVED");
        assertThat(jdbcTemplate.queryForObject(
                "select repaired_from_projection_ordinal from deep_research_answer_projections where run_id = ? and status = 'REMOVED'",
                Integer.class,
                snapshot.getRunId()
        )).isEqualTo(1);
    }

    /**
     * 验证同一 task 内重复 finding 会在持久化前合并，避免撞唯一约束。
     */
    @Test
    void shouldMergeDuplicateTaskFindingsBeforePersist() {
        LayeredResearchPlan plan = buildPlan();
        EvidenceLedger evidenceLedger = buildEvidenceLedgerWithDuplicateFinding();
        CitationCheckReport report = buildCitationCheckReport();
        AnswerProjectionBundle answerProjectionBundle = buildAnswerProjectionBundle();

        DeepResearchAuditSnapshot snapshot = deepResearchAuditPersistenceService.persist(
                "dr-persist-q4",
                "库存并发到底是乐观锁还是 Redis 锁",
                "force_deep",
                plan,
                evidenceLedger,
                "# 深度研究结论\n\n- 当前证据存在冲突 [[payment-routing]]",
                report,
                answerProjectionBundle,
                1,
                true,
                true
        );

        assertThat(snapshot.getRunId()).isNotNull();
        assertThat(count("deep_research_findings")).isEqualTo(1);
        String anchorIdsJson = jdbcTemplate.queryForObject(
                "select anchor_ids_json::text from deep_research_findings where run_id = ?",
                String.class,
                snapshot.getRunId()
        );
        assertThat(anchorIdsJson).contains("ev#1");
        assertThat(anchorIdsJson).contains("ev#2");
    }

    /**
     * 构造最小研究计划。
     *
     * @return 研究计划
     */
    private LayeredResearchPlan buildPlan() {
        ResearchTask researchTask = new ResearchTask();
        researchTask.setTaskId("task-1");
        researchTask.setQuestion("PaymentService 默认路由是什么");
        researchTask.setExpectedOutput("确认默认路由配置");
        researchTask.setRetrievalFocus("focused");

        ResearchLayer researchLayer = new ResearchLayer();
        researchLayer.setLayerIndex(0);
        researchLayer.setTasks(List.of(researchTask));

        LayeredResearchPlan plan = new LayeredResearchPlan();
        plan.setRootQuestion("PaymentService 默认路由是什么");
        plan.setLayers(List.of(researchLayer));
        return plan;
    }

    /**
     * 构造最小证据账本。
     *
     * @return 证据账本
     */
    private EvidenceLedger buildEvidenceLedger() {
        EvidenceAnchor evidenceAnchor = new EvidenceAnchor();
        evidenceAnchor.setAnchorId("ev#1");
        evidenceAnchor.setSourceType(EvidenceAnchorSourceType.ARTICLE);
        evidenceAnchor.setSourceId("payment-routing");
        evidenceAnchor.setQuoteText("RoutePlanner delegates to StandardRoutePolicy");
        evidenceAnchor.setRetrievalScore(0.9D);

        FactFinding factFinding = new FactFinding();
        factFinding.setFindingId("ev#1-finding");
        factFinding.setSubject("payment.service");
        factFinding.setPredicate("defaultRoute");
        factFinding.setQualifier("current_config");
        factFinding.setFactKey(factFinding.expectedFactKey());
        factFinding.setValueText("标准链路");
        factFinding.setValueType(FactValueType.STRING);
        factFinding.setClaimText("PaymentService 默认路由是标准链路");
        factFinding.setConfidence(0.9D);
        factFinding.setSupportLevel(FindingSupportLevel.DIRECT);
        factFinding.setAnchorIds(List.of("ev#1"));

        EvidenceCard evidenceCard = new EvidenceCard();
        evidenceCard.setEvidenceId("ev#1");
        evidenceCard.setLayerIndex(0);
        evidenceCard.setTaskId("task-1");
        evidenceCard.setScope("PaymentService 默认路由是什么");
        evidenceCard.setEvidenceAnchors(List.of(evidenceAnchor));
        evidenceCard.setFactFindings(List.of(factFinding));
        evidenceCard.setTaskHits(List.of(taskHit()));
        evidenceCard.setSelectedArticleKeys(List.of("payment-routing"));

        EvidenceLedger evidenceLedger = new EvidenceLedger();
        evidenceLedger.addCard(evidenceCard);
        return evidenceLedger;
    }

    /**
     * 构造携带第二个 article anchor 的证据账本。
     *
     * @return 证据账本
     */
    private EvidenceLedger buildEvidenceLedgerWithSecondAnchor() {
        EvidenceLedger evidenceLedger = buildEvidenceLedger();
        EvidenceCard evidenceCard = evidenceLedger.getCards().get(0);
        List<EvidenceAnchor> evidenceAnchors = new ArrayList<EvidenceAnchor>(evidenceCard.getEvidenceAnchors());

        EvidenceAnchor secondAnchor = new EvidenceAnchor();
        secondAnchor.setAnchorId("ev#2");
        secondAnchor.setSourceType(EvidenceAnchorSourceType.ARTICLE);
        secondAnchor.setSourceId("payment-routing");
        secondAnchor.setQuoteText("PaymentService route policy is documented by payment-routing.");
        secondAnchor.setRetrievalScore(0.86D);

        evidenceAnchors.add(secondAnchor);
        evidenceCard.setEvidenceAnchors(evidenceAnchors);
        evidenceLedger.addAnchor(secondAnchor);
        return evidenceLedger;
    }

    /**
     * 构造同一 task 内包含重复 finding 的证据账本。
     *
     * @return 证据账本
     */
    private EvidenceLedger buildEvidenceLedgerWithDuplicateFinding() {
        EvidenceLedger evidenceLedger = buildEvidenceLedger();
        EvidenceCard evidenceCard = evidenceLedger.getCards().get(0);
        List<EvidenceAnchor> evidenceAnchors = new ArrayList<EvidenceAnchor>(evidenceCard.getEvidenceAnchors());
        List<FactFinding> factFindings = new ArrayList<FactFinding>(evidenceCard.getFactFindings());

        EvidenceAnchor secondAnchor = new EvidenceAnchor();
        secondAnchor.setAnchorId("ev#2");
        secondAnchor.setSourceType(EvidenceAnchorSourceType.ARTICLE);
        secondAnchor.setSourceId("payment-routing");
        secondAnchor.setQuoteText("PaymentService route policy is documented by payment-routing.");
        secondAnchor.setRetrievalScore(0.86D);

        FactFinding duplicateFinding = new FactFinding();
        duplicateFinding.setFindingId("ev#2-finding");
        duplicateFinding.setSubject("task_1");
        duplicateFinding.setPredicate("claim");
        duplicateFinding.setQualifier("deep_research");
        duplicateFinding.setFactKey(duplicateFinding.expectedFactKey());
        duplicateFinding.setValueText("目前证据里**存在冲突，不能直接下单一结论**：");
        duplicateFinding.setValueType(FactValueType.STRING);
        duplicateFinding.setClaimText("目前证据里**存在冲突，不能直接下单一结论**：");
        duplicateFinding.setConfidence(0.8D);
        duplicateFinding.setSupportLevel(FindingSupportLevel.DIRECT);
        duplicateFinding.setAnchorIds(List.of("ev#2"));

        evidenceAnchors.add(secondAnchor);
        factFindings.clear();
        FactFinding originalFinding = new FactFinding();
        originalFinding.setFindingId("ev#1-finding");
        originalFinding.setSubject("task_1");
        originalFinding.setPredicate("claim");
        originalFinding.setQualifier("deep_research");
        originalFinding.setFactKey(originalFinding.expectedFactKey());
        originalFinding.setValueText("目前证据里**存在冲突，不能直接下单一结论**：");
        originalFinding.setValueType(FactValueType.STRING);
        originalFinding.setClaimText("目前证据里**存在冲突，不能直接下单一结论**：");
        originalFinding.setConfidence(0.9D);
        originalFinding.setSupportLevel(FindingSupportLevel.DIRECT);
        originalFinding.setAnchorIds(List.of("ev#1"));
        factFindings.add(originalFinding);
        factFindings.add(duplicateFinding);

        evidenceCard.setEvidenceAnchors(evidenceAnchors);
        evidenceCard.setFactFindings(factFindings);
        evidenceLedger.addAnchor(secondAnchor);
        return evidenceLedger;
    }

    /**
     * 构造最小任务命中。
     *
     * @return 任务命中
     */
    private ResearchTaskHit taskHit() {
        ResearchTaskHit taskHit = new ResearchTaskHit();
        taskHit.setHitOrdinal(1);
        taskHit.setChannel("knowledge_search");
        taskHit.setEvidenceType("ARTICLE");
        taskHit.setSourceId("1");
        taskHit.setArticleKey("payment-routing");
        taskHit.setConceptId("payment-routing");
        taskHit.setTitle("Payment Routing");
        taskHit.setPath("docs/payment-routing.md");
        taskHit.setOriginalScore(Double.valueOf(0.9D));
        taskHit.setFusedScore(Double.valueOf(0.9D));
        taskHit.setContentExcerpt("RoutePlanner delegates to StandardRoutePolicy");
        return taskHit;
    }

    /**
     * 构造最小 Citation 检查报告。
     *
     * @return Citation 检查报告
     */
    private CitationCheckReport buildCitationCheckReport() {
        Citation citation = new Citation(
                0,
                "[[payment-routing]]",
                CitationSourceType.ARTICLE,
                "payment-routing",
                "PaymentService 默认路由是标准链路",
                "PaymentService 默认路由是标准链路 [[payment-routing]]"
        );
        ClaimSegment claimSegment = new ClaimSegment(
                0,
                "PaymentService 默认路由是标准链路",
                "PaymentService 默认路由是标准链路 [[payment-routing]]",
                List.of(citation)
        );
        CitationValidationResult validationResult = new CitationValidationResult(
                "payment-routing",
                CitationSourceType.ARTICLE,
                CitationValidationStatus.VERIFIED,
                1.0D,
                "rule_overlap_verified",
                "StandardRoutePolicy",
                0
        );
        return new CitationCheckReport(
                "# 深度研究结论\n\n- PaymentService 默认路由是标准链路 [[payment-routing]]",
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
    }

    /**
     * 构造无引用的 unsupported Citation 检查报告。
     *
     * @return Citation 检查报告
     */
    private CitationCheckReport buildUnsupportedNoCitationReport() {
        ClaimSegment claimSegment = new ClaimSegment(
                0,
                "PaymentService 默认路由是标准链路",
                "PaymentService 默认路由是标准链路（当前证据不足）",
                List.of()
        );
        return new CitationCheckReport(
                "PaymentService 默认路由是标准链路（当前证据不足）",
                List.of(claimSegment),
                List.of(),
                0,
                0,
                0,
                true,
                0.0D,
                1,
                0,
                0,
                0
        );
    }

    /**
     * 构造最小答案投影包。
     *
     * @return 答案投影包
     */
    private AnswerProjectionBundle buildAnswerProjectionBundle() {
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
        return new AnswerProjectionBundle(
                "# 深度研究结论\n\n- PaymentService 默认路由是标准链路 [[payment-routing]]",
                List.of(answerProjection)
        );
    }

    /**
     * 构造重复 ACTIVE literal 的答案投影包。
     *
     * @return 答案投影包
     */
    private AnswerProjectionBundle buildDuplicateActiveLiteralProjectionBundle() {
        AnswerProjection firstProjection = new AnswerProjection(
                1,
                "ev#1",
                ProjectionCitationFormat.ARTICLE,
                "[[payment-routing]]",
                "payment-routing",
                ProjectionStatus.ACTIVE,
                0,
                null
        );
        AnswerProjection secondProjection = new AnswerProjection(
                2,
                "ev#2",
                ProjectionCitationFormat.ARTICLE,
                "[[payment-routing]]",
                "payment-routing",
                ProjectionStatus.ACTIVE,
                0,
                null
        );
        return new AnswerProjectionBundle(
                "# 深度研究结论\n\n- PaymentService 默认路由是标准链路 [[payment-routing]]",
                List.of(firstProjection, secondProjection)
        );
    }

    /**
     * 构造已移除 projection 的 repair 历史包。
     *
     * @return 答案投影包
     */
    private AnswerProjectionBundle buildRemovedProjectionHistoryBundle() {
        AnswerProjection replacedProjection = new AnswerProjection(
                1,
                "ev#1",
                ProjectionCitationFormat.ARTICLE,
                "[[payment-routing]]",
                "payment-routing",
                ProjectionStatus.REPLACED,
                0,
                null
        );
        AnswerProjection removedProjection = new AnswerProjection(
                2,
                "ev#1",
                ProjectionCitationFormat.ARTICLE,
                "[[payment-routing]]",
                "payment-routing",
                ProjectionStatus.REMOVED,
                1,
                Integer.valueOf(1)
        );
        return new AnswerProjectionBundle(
                "PaymentService 默认路由是标准链路（当前证据不足）",
                List.of(replacedProjection, removedProjection)
        );
    }

    /**
     * 统计表记录数。
     *
     * @param tableName 表名
     * @return 记录数
     */
    private int count(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count.intValue();
    }
}
