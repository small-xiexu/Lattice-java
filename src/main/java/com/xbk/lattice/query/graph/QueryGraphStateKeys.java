package com.xbk.lattice.query.graph;

/**
 * 问答图状态键
 *
 * 职责：集中管理 QueryGraphState 对应的 Graph Map 键名
 *
 * @author xiexu
 */
public final class QueryGraphStateKeys {

    public static final String QUERY_ID = "queryId";

    public static final String LEGACY_REQUEST_ID = "requestId";

    public static final String QUESTION = "question";

    public static final String NORMALIZED_QUESTION = "normalizedQuestion";

    public static final String REWRITTEN_QUESTION = "rewrittenQuestion";

    public static final String QUERY_INTENT = "queryIntent";

    public static final String ANSWER_SHAPE = "answerShape";

    public static final String REWRITE_AUDIT_REF = "rewriteAuditRef";

    public static final String RETRIEVAL_STRATEGY_REF = "retrievalStrategyRef";

    public static final String RETRIEVAL_CHANNEL_RUNS_REF = "retrievalChannelRunsRef";

    public static final String RETRIEVAL_AUDIT_REF = "retrievalAuditRef";

    public static final String LLM_SCOPE_TYPE = "llmScopeType";

    public static final String LLM_SCOPE_ID = "llmScopeId";

    public static final String TRACE_ID = "traceId";

    public static final String SPAN_ID = "spanId";

    public static final String ROOT_TRACE_ID = "rootTraceId";

    public static final String CACHE_HIT = "cacheHit";

    public static final String HAS_FUSED_HITS = "hasFusedHits";

    public static final String RETRIEVED_HIT_GROUPS_REF = "retrievedHitGroupsRef";

    public static final String FTS_HITS_REF = "ftsHitsRef";

    public static final String ARTICLE_CHUNK_HITS_REF = "articleChunkHitsRef";

    public static final String REFKEY_HITS_REF = "refkeyHitsRef";

    public static final String SOURCE_HITS_REF = "sourceHitsRef";

    public static final String SOURCE_CHUNK_HITS_REF = "sourceChunkHitsRef";

    public static final String FACT_CARD_HITS_REF = "factCardHitsRef";

    public static final String FACT_CARD_VECTOR_HITS_REF = "factCardVectorHitsRef";

    public static final String CONTRIBUTION_HITS_REF = "contributionHitsRef";

    public static final String GRAPH_HITS_REF = "graphHitsRef";

    public static final String ARTICLE_VECTOR_HITS_REF = "articleVectorHitsRef";

    public static final String CHUNK_VECTOR_HITS_REF = "chunkVectorHitsRef";

    public static final String FUSED_HITS_REF = "fusedHitsRef";

    public static final String RETRIEVAL_MODE = "retrievalMode";

    public static final String RETRIEVAL_STARTED_AT_EPOCH_MS = "retrievalStartedAtEpochMs";

    public static final String DRAFT_ANSWER_REF = "draftAnswerRef";

    public static final String REVIEW_RESULT_REF = "reviewResultRef";

    public static final String CACHED_RESPONSE_REF = "cachedResponseRef";

    public static final String FINAL_RESPONSE_REF = "finalResponseRef";

    public static final String CLAIM_SEGMENTS_REF = "claimSegmentsRef";

    public static final String CITATION_CHECK_REPORT_REF = "citationCheckReportRef";

    public static final String ANSWER_PROJECTION_BUNDLE_REF = "answerProjectionBundleRef";

    public static final String ANSWER_AUDIT_REF = "answerAuditRef";

    public static final String LLM_BINDING_SNAPSHOT_REF = "llmBindingSnapshotRef";

    public static final String ANSWER_ROUTE = "answerRoute";

    public static final String REVIEW_ROUTE = "reviewRoute";

    public static final String REWRITE_ROUTE = "rewriteRoute";

    public static final String REVIEW_STATUS = "reviewStatus";

    public static final String ANSWER_OUTCOME = "answerOutcome";

    public static final String GENERATION_MODE = "generationMode";

    public static final String MODEL_EXECUTION_STATUS = "modelExecutionStatus";

    public static final String FALLBACK_REASON = "fallbackReason";

    public static final String ANSWER_CACHEABLE = "answerCacheable";

    public static final String REWRITE_ATTEMPT_COUNT = "rewriteAttemptCount";

    public static final String MAX_REWRITE_ROUNDS = "maxRewriteRounds";

    public static final String CITATION_REPAIR_ATTEMPT_COUNT = "citationRepairAttemptCount";

    private QueryGraphStateKeys() {
    }
}
