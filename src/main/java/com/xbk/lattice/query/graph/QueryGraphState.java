package com.xbk.lattice.query.graph;

import lombok.Data;

/**
 * 问答图状态
 *
 * 职责：承载 Query Graph 运行所需的轻量状态字段与工作集引用
 *
 * @author xiexu
 */
@Data
public class QueryGraphState {

    private String queryId;

    private String question;

    private String normalizedQuestion;

    private String llmScopeType;

    private String llmScopeId;

    private String traceId;

    private String spanId;

    private String rootTraceId;

    private boolean cacheHit;

    private boolean hasFusedHits;

    private String retrievedHitGroupsRef;

    private String ftsHitsRef;

    private String refkeyHitsRef;

    private String sourceHitsRef;

    private String contributionHitsRef;

    private String articleVectorHitsRef;

    private String chunkVectorHitsRef;

    private String fusedHitsRef;

    private String retrievalMode;

    private long retrievalStartedAtEpochMs;

    private String draftAnswerRef;

    private String reviewResultRef;

    private String cachedResponseRef;

    private String finalResponseRef;

    private String llmBindingSnapshotRef;

    private String answerRoute;

    private String reviewRoute;

    private String rewriteRoute;

    private String reviewStatus;

    private int rewriteAttemptCount;

    private int maxRewriteRounds;
}
