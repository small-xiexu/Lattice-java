package com.xbk.lattice.query.graph;

import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationCheckService;
import com.xbk.lattice.query.citation.CitationExtractor;
import com.xbk.lattice.query.citation.CitationValidator;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.service.AnswerGenerationService;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryEvidenceType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryFinalizationGraphFragment 测试
 *
 * 职责：验证 citation 质量不足时的 terminal fallback 决策，
 * 覆盖 shouldFallbackToDeterministicAnswer 各 generationMode 分支
 * 以及 citationCheck → fallbackWhenCitationQualityIsInsufficient 的真实行为
 *
 * @author xiexu
 */
class QueryFinalizationGraphFragmentTests {

    private static final AnswerGenerationService answerGenerationService = new AnswerGenerationService();

    // ---- shouldFallbackToDeterministicAnswer 单元测试 ----

    /**
     * 当答案由 LLM 合成，citation repair 已耗尽且 report 为 noCitation 时，
     * 不应触发 terminal fallback——保留 repair 后的答案。
     */
    @Test
    void shouldNotFallbackWhenLlmSynthesizedAndNoCitationAfterRepair() {
        QueryGraphState state = new QueryGraphState();
        state.setGenerationMode(GenerationMode.LLM.name());
        state.setCitationRepairAttemptCount(1);

        CitationCheckReport report = new CitationCheckReport(
                "repaired answer body", List.of(), List.of(),
                0, 0, 0, true, 0.0D, 0, 0, 0, 0
        );

        QueryFinalizationGraphFragment fragment = new QueryFinalizationGraphFragment(
                null, null, null, null, null, null, answerGenerationService
        );

        assertThat(fragment.shouldFallbackToDeterministicAnswer(state, report)).isFalse();
    }

    /**
     * 当答案由规则拼装（RULE_BASED），citation repair 已耗尽且 noCitation 时，
     * 也不应触发 terminal fallback。
     */
    @Test
    void shouldNotFallbackWhenRuleBasedAndNoCitationAfterRepair() {
        QueryGraphState state = new QueryGraphState();
        state.setGenerationMode(GenerationMode.RULE_BASED.name());
        state.setCitationRepairAttemptCount(1);

        CitationCheckReport report = new CitationCheckReport(
                "rule-based answer", List.of(), List.of(),
                0, 0, 0, true, 0.0D, 0, 0, 0, 0
        );

        QueryFinalizationGraphFragment fragment = new QueryFinalizationGraphFragment(
                null, null, null, null, null, null, answerGenerationService
        );

        assertThat(fragment.shouldFallbackToDeterministicAnswer(state, report)).isFalse();
    }

    /**
     * 当 generationMode 为 null（未设置），repair 已耗尽且 noCitation 时，
     * 仍应触发 terminal fallback——这是安全网场景。
     */
    @Test
    void shouldStillFallbackWhenGenerationModeNullAndNoCitationAfterRepair() {
        QueryGraphState state = new QueryGraphState();
        state.setGenerationMode(null);
        state.setCitationRepairAttemptCount(1);

        CitationCheckReport report = new CitationCheckReport(
                "answer without mode", List.of(), List.of(),
                0, 0, 0, true, 0.0D, 0, 0, 0, 0
        );

        QueryFinalizationGraphFragment fragment = new QueryFinalizationGraphFragment(
                null, null, null, null, null, null, answerGenerationService
        );

        assertThat(fragment.shouldFallbackToDeterministicAnswer(state, report)).isTrue();
    }

    /**
     * 当 generationMode 为 FALLBACK（已走兜底），repair 已耗尽且 noCitation 时，
     * 仍应触发 terminal fallback——安全网，避免二次兜底时答案质量不可控。
     */
    @Test
    void shouldStillFallbackWhenAlreadyFallbackModeAndNoCitationAfterRepair() {
        QueryGraphState state = new QueryGraphState();
        state.setGenerationMode(GenerationMode.FALLBACK.name());
        state.setCitationRepairAttemptCount(1);

        CitationCheckReport report = new CitationCheckReport(
                "fallback answer", List.of(), List.of(),
                0, 0, 0, true, 0.0D, 0, 0, 0, 0
        );

        QueryFinalizationGraphFragment fragment = new QueryFinalizationGraphFragment(
                null, null, null, null, null, null, answerGenerationService
        );

        assertThat(fragment.shouldFallbackToDeterministicAnswer(state, report)).isTrue();
    }

    /**
     * 当 citation repair 尚未达到最大轮数时，即使 noCitation 也不应 fallback。
     */
    @Test
    void shouldNotFallbackWhenRepairRoundsNotExhausted() {
        QueryGraphState state = new QueryGraphState();
        state.setGenerationMode(null);
        state.setCitationRepairAttemptCount(0);

        CitationCheckReport report = new CitationCheckReport(
                "answer before repair", List.of(), List.of(),
                0, 0, 0, true, 0.0D, 0, 0, 0, 0
        );

        QueryFinalizationGraphFragment fragment = new QueryFinalizationGraphFragment(
                null, null, null, null, null, null, answerGenerationService
        );

        assertThat(fragment.shouldFallbackToDeterministicAnswer(state, report)).isFalse();
    }

    /**
     * 当 generationMode 为 LLM，repair 已耗尽，但 verified/skipped/coverage 均为 0
     * 且 noCitation=false 时，应通过 hasUsableCitationEvidence 判为 fallback。
     * 但 generationMode=LLM 的保护条件先命中，不会进入 hasUsableCitationEvidence 判定。
     */
    @Test
    void shouldNotFallbackWhenLlmSynthesizedAndNoUsableCitationEvidence() {
        QueryGraphState state = new QueryGraphState();
        state.setGenerationMode(GenerationMode.LLM.name());
        state.setCitationRepairAttemptCount(1);

        CitationCheckReport report = new CitationCheckReport(
                "repaired answer with stripped citations", List.of(), List.of(),
                0, 0, 0, false, 0.0D, 0, 0, 0, 0
        );

        QueryFinalizationGraphFragment fragment = new QueryFinalizationGraphFragment(
                null, null, null, null, null, null, answerGenerationService
        );

        assertThat(fragment.shouldFallbackToDeterministicAnswer(state, report)).isFalse();
    }

    // ---- citationCheck → fallbackWhenCitationQualityIsInsufficient 集成测试 ----

    /**
     * 验证 citationCheck 在 generationMode=LLM 时不会触发 fallback 替换答案。
     * 答案"结论：retry=3"无实际 citation 标记，
     * citation check 返回 noCitation=true 的报告，
     * 但因为 generationMode=LLM，fallbackWhenCitationQualityIsInsufficient 不会触发。
     */
    @Test
    void citationCheckShouldNotReplaceLlmAnswerWhenNoCitationAfterRepair() {
        InMemoryQueryWorkingSetStore workingSet = new InMemoryQueryWorkingSetStore();
        String queryId = "test-query-id";

        QueryGraphState state = new QueryGraphState();
        state.setQueryId(queryId);
        state.setGenerationMode(GenerationMode.LLM.name());
        state.setCitationRepairAttemptCount(1);
        state.setHasFusedHits(true);
        state.setAnswerOutcome(AnswerOutcome.PARTIAL_ANSWER.name());

        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "payment-timeout",
                "Payment Timeout",
                "retry=3",
                "{\"description\":\"Handles payment timeout recovery\"}",
                List.of("payment/analyze.json"),
                10.0D
        );
        List<QueryArticleHit> fusedHits = List.of(articleHit);
        state.setFusedHitsRef(workingSet.saveFusedHits(queryId, fusedHits));

        String answer = "结论：retry=3";
        state.setDraftAnswerRef(workingSet.saveAnswer(queryId, answer));

        AnswerProjectionBundle projectionBundle = new AnswerProjectionBundle(answer, List.of());
        state.setAnswerProjectionBundleRef(workingSet.saveAnswerProjectionBundle(queryId, projectionBundle));

        CitationCheckService citationCheckService = new CitationCheckService(
                new CitationExtractor(),
                new CitationValidator(null, null, null, null),
                null
        );

        QueryGraphStateMapper stateMapper = new QueryGraphStateMapper();
        QueryFinalizationGraphFragment fragment = new QueryFinalizationGraphFragment(
                workingSet,
                citationCheckService,
                null,
                null,
                stateMapper,
                null,
                answerGenerationService
        );

        Map<String, Object> result = fragment.citationCheck(
                new com.alibaba.cloud.ai.graph.OverAllState(stateMapper.toDeltaMap(state))
        );

        QueryGraphState updatedState = stateMapper.fromMap(result);
        String updatedAnswer = workingSet.loadAnswer(updatedState.getDraftAnswerRef());
        assertThat(updatedAnswer).isEqualTo("结论：retry=3");
        assertThat(updatedState.getFallbackReason()).isNull();
    }
}
