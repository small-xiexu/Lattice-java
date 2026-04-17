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

    public static final String CACHE_HIT = "cacheHit";

    public static final String HAS_FUSED_HITS = "hasFusedHits";

    public static final String RETRIEVED_HIT_GROUPS_REF = "retrievedHitGroupsRef";

    public static final String FUSED_HITS_REF = "fusedHitsRef";

    public static final String DRAFT_ANSWER_REF = "draftAnswerRef";

    public static final String REVIEW_RESULT_REF = "reviewResultRef";

    public static final String CACHED_RESPONSE_REF = "cachedResponseRef";

    public static final String FINAL_RESPONSE_REF = "finalResponseRef";

    public static final String REVIEW_STATUS = "reviewStatus";

    public static final String REWRITE_ATTEMPT_COUNT = "rewriteAttemptCount";

    public static final String MAX_REWRITE_ROUNDS = "maxRewriteRounds";

    private QueryGraphStateKeys() {
    }
}
