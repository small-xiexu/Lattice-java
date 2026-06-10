package com.xbk.lattice.query.graph;

import lombok.Getter;
import lombok.Setter;

/**
 * 问答图状态。
 *
 * <p>承载 Query Graph 运行所需的轻量状态字段与工作集引用——由 Graph 框架通过 setter 注入。
 * 各 Ref 字段是工作集/缓存/审计对象的引用键，非大对象本体。
 *
 * @author xiexu
 */
@Getter
@Setter
public class QueryGraphState {

    // ── 运行上下文 ──
    /** 查询会话标识。 */
    private String queryId;
    /** 用户原始问题。 */
    private String question;
    /** 归一化后的问题文本。 */
    private String normalizedQuestion;
    /** 标准化缓存键（仅用于 query cache get/put，不影响 rewrite/intent/retrieval/audit）。 */
    private String canonicalCacheKey;
    /** 经 LLM 改写后的问题文本。 */
    private String rewrittenQuestion;
    /** 查询意图分类。 */
    private String queryIntent;
    /** 答案形态。 */
    private String answerShape;
    /** 观测追踪 traceId。 */
    private String traceId;
    /** 观测追踪 spanId。 */
    private String spanId;
    /** 根 traceId。 */
    private String rootTraceId;

    // ── 检索与召回 ──
    /** 改写审计引用（工作集键）。 */
    private String rewriteAuditRef;
    /** 检索策略引用。 */
    private String retrievalStrategyRef;
    /** 检索通道运行列表引用。 */
    private String retrievalChannelRunsRef;
    /** 检索审计引用。 */
    private String retrievalAuditRef;
    /** 是否命中缓存。 */
    private boolean cacheHit;
    /** 是否有 RRF 融合命中。 */
    private boolean hasFusedHits;
    /** 检索模式（parallel / sequential）。 */
    private String retrievalMode;
    /** 检索启动时间戳（epoch ms）。 */
    private long retrievalStartedAtEpochMs;

    // ── 各通道命中引用 ──
    private String retrievedHitGroupsRef;
    private String ftsHitsRef;
    private String articleChunkHitsRef;
    private String refkeyHitsRef;
    private String sourceHitsRef;
    private String sourceChunkHitsRef;
    private String factCardHitsRef;
    private String factCardVectorHitsRef;
    private String factCardTerminalUnitHitsRef;
    private String contributionHitsRef;
    private String graphHitsRef;
    private String articleVectorHitsRef;
    private String chunkVectorHitsRef;
    /** 融合后命中列表引用。 */
    private String fusedHitsRef;

    // ── 答案生成 ──
    /** 草稿答案引用。 */
    private String draftAnswerRef;
    /** 审查结果引用。 */
    private String reviewResultRef;
    /** 缓存响应引用。 */
    private String cachedResponseRef;
    /** 最终响应引用。 */
    private String finalResponseRef;
    /** claim 分段引用。 */
    private String claimSegmentsRef;
    /** 引用检查报告引用。 */
    private String citationCheckReportRef;
    /** 答案投影包引用。 */
    private String answerProjectionBundleRef;
    /** 答案审计引用。 */
    private String answerAuditRef;

    // ── LLM 绑定 ──
    /** LLM 作用域类型。 */
    private String llmScopeType;
    /** LLM 作用域标识。 */
    private String llmScopeId;
    /** LLM 绑定快照引用。 */
    private String llmBindingSnapshotRef;

    // ── 路由与状态 ──
    /** 答案路由。 */
    private String answerRoute;
    /** 审查路由。 */
    private String reviewRoute;
    /** 重写路由。 */
    private String rewriteRoute;
    /** 审查状态。 */
    private String reviewStatus;
    /** 答案结论。 */
    private String answerOutcome;
    /** 生成模式。 */
    private String generationMode;
    /** 模型执行状态。 */
    private String modelExecutionStatus;
    /** fallback 原因。 */
    private String fallbackReason;
    /** 是否允许缓存答案。 */
    private boolean answerCacheable;

    // ── 轮次控制 ──
    /** 重写尝试次数。 */
    private int rewriteAttemptCount;
    /** 最大重写轮次。 */
    private int maxRewriteRounds;
    /** 引用修复尝试次数。 */
    private int citationRepairAttemptCount;
}
