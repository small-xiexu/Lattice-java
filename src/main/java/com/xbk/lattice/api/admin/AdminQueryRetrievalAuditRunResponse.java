package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧 Query 检索审计 run 响应。
 *
 * <p>承载一次检索 run 的完整摘要——含 query 处理链、检索模式、命中统计与通道详情，
 * 由 {@code AdminQueryRetrievalAuditController} 组装返回。
 * 构造器含 {@code List.copyOf} 防御性拷贝（{@code channelRuns}），不可变。
 * 禁止引入 {@code @Data}：{@code question} 为用户查询内容，{@code channelRunSummaryJson} 可能很大。
 *
 * @author xiexu
 */
@Getter
public class AdminQueryRetrievalAuditRunResponse {

    /** run 主键。为 {@code null} 表示无记录。 */
    private final Long runId;

    /** 查询标识。 */
    private final String queryId;

    /**
     * 用户原始问题文本。
     *
     * <p>可能含 PII，禁止参与 {@code toString()}。</p>
     */
    private final String question;

    /** query 归一化后的文本。 */
    private final String normalizedQuestion;

    /**
     * 实际发送给检索引擎的文本。
     *
     * <p>可能经过 LLM 改写或扩展，与原始 {@code question} 不同。</p>
     */
    private final String retrievalQuestion;

    /** 版本标签——标识检索使用的代码/配置版本。 */
    private final String versionTag;

    /** 策略标签——标识检索策略组合。 */
    private final String strategyTag;

    /** 问题类型分类标签（如 {@code factual} / {@code reasoning} / {@code comparison}）。 */
    private final String questionTypeTag;

    /** 答案形态（如 {@code text} / {@code table} / {@code mixed}）。 */
    private final String answerShape;

    /** 检索模式（如 {@code parallel} / {@code sequential}）。 */
    private final String retrievalMode;

    /**
     * 是否对 query 执行了 LLM 改写。
     *
     * <p>{@code true} 时 {@code retrievalQuestion} 与 {@code question} 不同，
     * {@code rewriteAuditRef} 可追溯到具体改写记录。</p>
     */
    private final boolean rewriteApplied;

    /** 改写审计引用——可追溯到具体改写记录。 */
    private final String rewriteAuditRef;

    /** 检索策略引用——可追溯策略配置版本。 */
    private final String retrievalStrategyRef;

    /**
     * RRF 融合后的最终命中数。
     *
     * <p>0 表示检索未产生有效结果（{@code coverageStatus=empty}）。</p>
     */
    private final int fusedHitCount;

    /** 实际参与检索的通道数。 */
    private final int channelCount;

    /** Fact Card 通道命中数。 */
    private final int factCardHitCount;

    /** Source Chunk 通道命中数。 */
    private final int sourceChunkHitCount;

    /**
     * 检索覆盖状态。
     *
     * <p>可选值：{@code sufficient} / {@code partial} / {@code empty}。
     * 驱动诊断展示——{@code empty} 时检索完全失败，需排查通道配置或数据完整性。</p>
     */
    private final String coverageStatus;

    /**
     * 通道运行摘要原始 JSON。
     *
     * <p>可能较大，禁止参与 {@code toString()}。</p>
     */
    private final String channelRunSummaryJson;

    /**
     * 各通道运行详情列表。
     *
     * <p>不可变（构造器中通过 {@code List.copyOf} 防御性拷贝）。</p>
     */
    private final List<AdminQueryRetrievalChannelRunResponse> channelRuns;

    /** run 创建时间（ISO-8601 字符串）。 */
    private final String createdAt;

    /**
     * 创建管理侧 Query 检索审计 run 响应。
     *
     * @param runId 主键
     * @param queryId 查询标识
     * @param question 原始问题
     * @param normalizedQuestion 归一化问题
     * @param retrievalQuestion 实际检索问题
     * @param versionTag 版本标签
     * @param strategyTag 策略标签
     * @param questionTypeTag 问题类型标签
     * @param answerShape 答案形态
     * @param retrievalMode 检索模式
     * @param rewriteApplied 是否改写
     * @param rewriteAuditRef 改写审计引用
     * @param retrievalStrategyRef 检索策略引用
     * @param fusedHitCount 融合命中数
     * @param channelCount 通道数
     * @param factCardHitCount Fact Card 命中数
     * @param sourceChunkHitCount Source Chunk 命中数
     * @param coverageStatus 覆盖状态
     * @param channelRunSummaryJson 通道运行摘要 JSON
     * @param channelRuns 通道运行摘要（构造器中做防御性拷贝）
     * @param createdAt 创建时间
     */
    public AdminQueryRetrievalAuditRunResponse(
            Long runId,
            String queryId,
            String question,
            String normalizedQuestion,
            String retrievalQuestion,
            String versionTag,
            String strategyTag,
            String questionTypeTag,
            String answerShape,
            String retrievalMode,
            boolean rewriteApplied,
            String rewriteAuditRef,
            String retrievalStrategyRef,
            int fusedHitCount,
            int channelCount,
            int factCardHitCount,
            int sourceChunkHitCount,
            String coverageStatus,
            String channelRunSummaryJson,
            List<AdminQueryRetrievalChannelRunResponse> channelRuns,
            String createdAt
    ) {
        this.runId = runId;
        this.queryId = queryId;
        this.question = question;
        this.normalizedQuestion = normalizedQuestion;
        this.retrievalQuestion = retrievalQuestion;
        this.versionTag = versionTag;
        this.strategyTag = strategyTag;
        this.questionTypeTag = questionTypeTag;
        this.answerShape = answerShape;
        this.retrievalMode = retrievalMode;
        this.rewriteApplied = rewriteApplied;
        this.rewriteAuditRef = rewriteAuditRef;
        this.retrievalStrategyRef = retrievalStrategyRef;
        this.fusedHitCount = fusedHitCount;
        this.channelCount = channelCount;
        this.factCardHitCount = factCardHitCount;
        this.sourceChunkHitCount = sourceChunkHitCount;
        this.coverageStatus = coverageStatus;
        this.channelRunSummaryJson = channelRunSummaryJson;
        this.channelRuns = channelRuns == null ? List.of() : List.copyOf(channelRuns);
        this.createdAt = createdAt;
    }
}
