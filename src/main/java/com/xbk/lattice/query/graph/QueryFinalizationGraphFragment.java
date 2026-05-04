package com.xbk.lattice.query.graph;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.query.citation.CitationCheckOptions;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationCheckService;
import com.xbk.lattice.query.citation.ClaimSegment;
import com.xbk.lattice.query.citation.QueryAnswerAuditPersistenceService;
import com.xbk.lattice.query.citation.QueryAnswerAuditSnapshot;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.service.AnswerGenerationService;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryCacheStore;
import com.xbk.lattice.query.service.QueryResponseCitationAssembler;

import java.util.List;
import java.util.Map;

/**
 * Query 最终化图片段
 *
 * 职责：集中承接 claim、citation、repair、persist 与最终响应组装节点
 *
 * @author xiexu
 */
public class QueryFinalizationGraphFragment {

    private static final CitationCheckOptions CITATION_CHECK_OPTIONS = CitationCheckOptions.defaults();

    private final QueryWorkingSetStore queryWorkingSetStore;

    private final CitationCheckService citationCheckService;

    private final QueryAnswerAuditPersistenceService queryAnswerAuditPersistenceService;

    private final QueryCacheStore queryCacheStore;

    private final QueryGraphStateMapper queryGraphStateMapper;

    private final QueryAnswerProjectionBuilder queryAnswerProjectionBuilder;

    private final AnswerGenerationService answerGenerationService;

    /**
     * 创建 Query 最终化图片段。
     *
     * @param queryWorkingSetStore Query 工作集
     * @param citationCheckService Citation 检查服务
     * @param queryAnswerAuditPersistenceService 答案审计持久化服务
     * @param queryCacheStore Query 缓存
     * @param queryGraphStateMapper Query 状态映射器
     * @param queryAnswerProjectionBuilder Query 答案投影构建器
     * @param answerGenerationService 答案生成服务
     */
    public QueryFinalizationGraphFragment(
            QueryWorkingSetStore queryWorkingSetStore,
            CitationCheckService citationCheckService,
            QueryAnswerAuditPersistenceService queryAnswerAuditPersistenceService,
            QueryCacheStore queryCacheStore,
            QueryGraphStateMapper queryGraphStateMapper,
            QueryAnswerProjectionBuilder queryAnswerProjectionBuilder,
            AnswerGenerationService answerGenerationService
    ) {
        this.queryWorkingSetStore = queryWorkingSetStore;
        this.citationCheckService = citationCheckService;
        this.queryAnswerAuditPersistenceService = queryAnswerAuditPersistenceService;
        this.queryCacheStore = queryCacheStore;
        this.queryGraphStateMapper = queryGraphStateMapper;
        this.queryAnswerProjectionBuilder = queryAnswerProjectionBuilder;
        this.answerGenerationService = answerGenerationService;
    }

    /**
     * 切分最终答案 claim。
     *
     * @param overAllState Graph 状态
     * @return 状态增量
     */
    public Map<String, Object> claimSegment(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        String answer = queryWorkingSetStore.loadAnswer(state.getDraftAnswerRef());
        AnswerProjectionBundle answerProjectionBundle = resolveAnswerProjectionBundle(state, answer);
        List<ClaimSegment> claimSegments = citationCheckService == null
                ? List.of()
                : citationCheckService.check(answer, answerProjectionBundle).getClaimSegments();
        state.setClaimSegmentsRef(queryWorkingSetStore.saveClaimSegments(state.getQueryId(), claimSegments));
        return queryGraphStateMapper.toDeltaMap(state);
    }

    /**
     * 执行 Citation 检查。
     *
     * @param overAllState Graph 状态
     * @return 状态增量
     */
    public Map<String, Object> citationCheck(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        if (citationCheckService == null) {
            state.setClaimSegmentsRef(queryWorkingSetStore.saveClaimSegments(state.getQueryId(), List.of()));
            state.setCitationCheckReportRef(null);
            return queryGraphStateMapper.toDeltaMap(state);
        }
        String answer = queryWorkingSetStore.loadAnswer(state.getDraftAnswerRef());
        AnswerProjectionBundle answerProjectionBundle = resolveAnswerProjectionBundle(state, answer);
        CitationCheckReport report = citationCheckService.check(answer, answerProjectionBundle);
        report = fallbackWhenCitationQualityIsInsufficient(state, report);
        state.setClaimSegmentsRef(queryWorkingSetStore.saveClaimSegments(state.getQueryId(), report.getClaimSegments()));
        state.setCitationCheckReportRef(queryWorkingSetStore.saveCitationCheckReport(state.getQueryId(), report));
        return queryGraphStateMapper.toDeltaMap(state);
    }

    /**
     * 按 Citation 报告修复答案。
     *
     * @param overAllState Graph 状态
     * @return 状态增量
     */
    public Map<String, Object> citationRepair(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        String answer = queryWorkingSetStore.loadAnswer(state.getDraftAnswerRef());
        CitationCheckReport report = queryWorkingSetStore.loadCitationCheckReport(state.getCitationCheckReportRef());
        if (citationCheckService != null && citationCheckService.shouldRepair(
                report,
                CITATION_CHECK_OPTIONS,
                state.getCitationRepairAttemptCount()
        )) {
            String repairedAnswer = citationCheckService.repair(answer, report);
            AnswerProjectionBundle answerProjectionBundle = queryWorkingSetStore.loadAnswerProjectionBundle(
                    state.getAnswerProjectionBundleRef()
            );
            AnswerProjectionBundle repairedProjectionBundle = citationCheckService.repairProjectionBundle(
                    answerProjectionBundle,
                    report,
                    repairedAnswer
            );
            state.setDraftAnswerRef(queryWorkingSetStore.saveAnswer(state.getQueryId(), repairedAnswer));
            if (repairedProjectionBundle != null) {
                state.setAnswerProjectionBundleRef(queryWorkingSetStore.saveAnswerProjectionBundle(
                        state.getQueryId(),
                        repairedProjectionBundle
                ));
            }
            state.setCitationRepairAttemptCount(state.getCitationRepairAttemptCount() + 1);
        }
        return queryGraphStateMapper.toDeltaMap(state);
    }

    /**
     * 持久化答案审计并构造最终响应。
     *
     * @param overAllState Graph 状态
     * @return 状态增量
     */
    public Map<String, Object> persistResponse(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        CitationCheckReport report = queryWorkingSetStore.loadCitationCheckReport(state.getCitationCheckReportRef());
        QueryAnswerAuditSnapshot answerAuditSnapshot = persistAnswerAudit(state, report);
        if (answerAuditSnapshot != null) {
            state.setAnswerAuditRef(queryWorkingSetStore.saveAnswerAudit(state.getQueryId(), answerAuditSnapshot));
        }
        QueryResponse queryResponse = buildSuccessResponse(state);
        String responseRef = queryWorkingSetStore.saveResponse(state.getQueryId(), queryResponse);
        if (shouldCacheResponse(queryResponse, state.isAnswerCacheable(), report)) {
            queryCacheStore.put(state.getNormalizedQuestion(), withoutQueryId(queryResponse));
            state.setCachedResponseRef(responseRef);
        }
        state.setFinalResponseRef(responseRef);
        return queryGraphStateMapper.toDeltaMap(state);
    }

    /**
     * 生成最终响应引用。
     *
     * @param overAllState Graph 状态
     * @return 状态增量
     */
    public Map<String, Object> finalizeResponse(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        QueryGraphState state = queryGraphStateMapper.fromMap(overAllState.data());
        if (state.getFinalResponseRef() != null) {
            return queryGraphStateMapper.toDeltaMap(state);
        }
        if (state.isCacheHit()) {
            state.setFinalResponseRef(state.getCachedResponseRef());
            return queryGraphStateMapper.toDeltaMap(state);
        }
        QueryResponse queryResponse;
        if (!state.isHasFusedHits()) {
            queryResponse = new QueryResponse(
                    "未找到相关知识",
                    List.of(),
                    List.of(),
                    state.getQueryId(),
                    null,
                    AnswerOutcome.NO_RELEVANT_KNOWLEDGE,
                    GenerationMode.RULE_BASED,
                    ModelExecutionStatus.SKIPPED
            );
        }
        else {
            queryResponse = buildSuccessResponse(state);
        }
        state.setFinalResponseRef(queryWorkingSetStore.saveResponse(state.getQueryId(), queryResponse));
        return queryGraphStateMapper.toDeltaMap(state);
    }

    private QueryResponse buildSuccessResponse(QueryGraphState state) {
        List<QueryArticleHit> fusedHits = queryWorkingSetStore.loadFusedHits(state.getFusedHitsRef());
        String answer = queryWorkingSetStore.loadAnswer(state.getDraftAnswerRef());
        CitationCheckReport report = queryWorkingSetStore.loadCitationCheckReport(state.getCitationCheckReportRef());
        AnswerProjectionBundle answerProjectionBundle = queryWorkingSetStore.loadAnswerProjectionBundle(
                state.getAnswerProjectionBundleRef()
        );
        return new QueryResponse(
                answer,
                QueryResponseCitationAssembler.toSourceResponses(answerProjectionBundle, fusedHits, true),
                QueryResponseCitationAssembler.toArticleResponses(answerProjectionBundle, fusedHits, true),
                state.getQueryId(),
                state.getReviewStatus(),
                readAnswerOutcome(state.getAnswerOutcome()),
                readGenerationMode(state.getGenerationMode()),
                readModelExecutionStatus(state.getModelExecutionStatus()),
                report == null ? null : report.toSummary(),
                null,
                state.getFallbackReason(),
                QueryResponseCitationAssembler.toCitationMarkerResponses(report, answerProjectionBundle, fusedHits)
        );
    }

    private AnswerProjectionBundle resolveAnswerProjectionBundle(QueryGraphState state, String answer) {
        AnswerProjectionBundle answerProjectionBundle = queryWorkingSetStore.loadAnswerProjectionBundle(
                state.getAnswerProjectionBundleRef()
        );
        if (answerProjectionBundle != null) {
            return answerProjectionBundle;
        }
        List<QueryArticleHit> fusedHits = queryWorkingSetStore.loadFusedHits(state.getFusedHitsRef());
        AnswerProjectionBundle builtProjectionBundle = queryAnswerProjectionBuilder == null
                ? new AnswerProjectionBundle(answer, List.of())
                : queryAnswerProjectionBuilder.build(answer, fusedHits);
        state.setAnswerProjectionBundleRef(queryWorkingSetStore.saveAnswerProjectionBundle(
                state.getQueryId(),
                builtProjectionBundle
        ));
        return builtProjectionBundle;
    }

    /**
     * 当最终答案已没有任何 citation 时，强制回落到确定性 fallback，并重新生成 projection/citation 报告。
     *
     * @param state 当前图状态
     * @param report 原始 citation 报告
     * @return 回落后的 citation 报告
     */
    private CitationCheckReport fallbackWhenCitationQualityIsInsufficient(
            QueryGraphState state,
            CitationCheckReport report
    ) {
        if (!shouldFallbackToDeterministicAnswer(state, report)) {
            return report;
        }
        List<QueryArticleHit> fusedHits = queryWorkingSetStore.loadFusedHits(state.getFusedHitsRef());
        if (fusedHits == null || fusedHits.isEmpty()) {
            return report;
        }
        QueryAnswerPayload fallbackPayload = answerGenerationService.fallbackPayload(
                state.getQuestion(),
                fusedHits,
                fallbackOutcome(state)
        );
        state.setDraftAnswerRef(queryWorkingSetStore.saveAnswer(
                state.getQueryId(),
                fallbackPayload.getAnswerMarkdown()
        ));
        state.setAnswerOutcome(fallbackPayload.getAnswerOutcome().name());
        state.setGenerationMode(fallbackPayload.getGenerationMode().name());
        state.setModelExecutionStatus(fallbackPayload.getModelExecutionStatus().name());
        state.setFallbackReason("CITATION_QUALITY_INSUFFICIENT");
        state.setAnswerCacheable(fallbackPayload.isAnswerCacheable());
        AnswerProjectionBundle fallbackProjectionBundle = queryAnswerProjectionBuilder == null
                ? new AnswerProjectionBundle(fallbackPayload.getAnswerMarkdown(), List.of())
                : queryAnswerProjectionBuilder.build(fallbackPayload.getAnswerMarkdown(), fusedHits);
        state.setAnswerProjectionBundleRef(queryWorkingSetStore.saveAnswerProjectionBundle(
                state.getQueryId(),
                fallbackProjectionBundle
        ));
        return citationCheckService.check(fallbackPayload.getAnswerMarkdown(), fallbackProjectionBundle);
    }

    /**
     * 判断当前 citation 质量是否应直接降级为 deterministic fallback。
     *
     * @param state 当前图状态
     * @param report 当前 citation 报告
     * @return 需要降级返回 true
     */
    private boolean shouldFallbackToDeterministicAnswer(QueryGraphState state, CitationCheckReport report) {
        if (report == null || answerGenerationService == null) {
            return false;
        }
        if (state.getCitationRepairAttemptCount() < CITATION_CHECK_OPTIONS.getMaxRepairRounds()) {
            return false;
        }
        if (report.isNoCitation()) {
            return true;
        }
        return !hasUsableCitationEvidence(report);
    }

    /**
     * 判断 citation 报告里是否仍有可保留的证据支撑。
     *
     * @param report citation 检查报告
     * @return 存在可用 citation 返回 true
     */
    private boolean hasUsableCitationEvidence(CitationCheckReport report) {
        if (report == null || report.isNoCitation()) {
            return false;
        }
        return report.getVerifiedCount() > 0 || report.getSkippedCount() > 0 || report.getCoverageRate() > 0.0D;
    }

    /**
     * 为 no-citation fallback 推导可保留的负向 outcome。
     *
     * @param state 当前图状态
     * @return fallback 后的答案语义
     */
    private AnswerOutcome fallbackOutcome(QueryGraphState state) {
        AnswerOutcome answerOutcome = readAnswerOutcome(state.getAnswerOutcome());
        if (answerOutcome == AnswerOutcome.INSUFFICIENT_EVIDENCE
                || answerOutcome == AnswerOutcome.NO_RELEVANT_KNOWLEDGE
                || answerOutcome == AnswerOutcome.PARTIAL_ANSWER) {
            return answerOutcome;
        }
        return AnswerOutcome.PARTIAL_ANSWER;
    }

    private boolean shouldCacheResponse(
            QueryResponse queryResponse,
            boolean answerCacheable,
            CitationCheckReport report
    ) {
        if (queryResponse == null) {
            return false;
        }
        if (!answerCacheable) {
            return false;
        }
        String answer = queryResponse.getAnswer();
        if (answer == null || answer.isBlank()) {
            return false;
        }
        if (queryResponse.getSources().isEmpty() && queryResponse.getArticles().isEmpty()) {
            return false;
        }
        if (!"PASSED".equals(queryResponse.getReviewStatus())) {
            return false;
        }
        if (report == null) {
            return false;
        }
        if (report.isNoCitation()) {
            return false;
        }
        if (report.getDemotedCount() > 0) {
            return false;
        }
        if (report.getUnsupportedClaimCount() > 0) {
            return false;
        }
        if (report.getProjectionMismatchCount() > 0) {
            return false;
        }
        if (report.getCoverageRate() < CITATION_CHECK_OPTIONS.getMinCitationCoverage()) {
            return false;
        }
        return isCacheableOutcome(queryResponse.getAnswerOutcome());
    }

    private QueryAnswerAuditSnapshot persistAnswerAudit(QueryGraphState state, CitationCheckReport report) {
        if (queryAnswerAuditPersistenceService == null) {
            return null;
        }
        String answer = queryWorkingSetStore.loadAnswer(state.getDraftAnswerRef());
        return queryAnswerAuditPersistenceService.persist(
                state.getQueryId(),
                state.getCitationRepairAttemptCount() + 1,
                state.getQuestion(),
                answer,
                readAnswerOutcome(state.getAnswerOutcome()),
                readGenerationMode(state.getGenerationMode()),
                state.getReviewStatus(),
                state.isAnswerCacheable(),
                "query",
                report
        );
    }

    private QueryResponse withoutQueryId(QueryResponse queryResponse) {
        return new QueryResponse(
                queryResponse.getAnswer(),
                queryResponse.getSources(),
                queryResponse.getArticles(),
                null,
                queryResponse.getReviewStatus(),
                queryResponse.getAnswerOutcome(),
                queryResponse.getGenerationMode(),
                queryResponse.getModelExecutionStatus(),
                queryResponse.getCitationCheck(),
                queryResponse.getDeepResearch(),
                queryResponse.getFallbackReason(),
                queryResponse.getCitationMarkers()
        );
    }

    private boolean isCacheableOutcome(AnswerOutcome answerOutcome) {
        return answerOutcome == AnswerOutcome.SUCCESS;
    }

    private AnswerOutcome readAnswerOutcome(String answerOutcome) {
        if (answerOutcome == null || answerOutcome.isBlank()) {
            return null;
        }
        return AnswerOutcome.valueOf(answerOutcome);
    }

    private GenerationMode readGenerationMode(String generationMode) {
        if (generationMode == null || generationMode.isBlank()) {
            return null;
        }
        return GenerationMode.valueOf(generationMode);
    }

    private ModelExecutionStatus readModelExecutionStatus(String modelExecutionStatus) {
        if (modelExecutionStatus == null || modelExecutionStatus.isBlank()) {
            return null;
        }
        return ModelExecutionStatus.valueOf(modelExecutionStatus);
    }

}
