package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * 查询响应。
 *
 * <p>承载一次查询闭环的完整返回结果，包括答案文本、来源溯源、
 * 答案语义/生成模式/模型状态等 API 可见元数据。
 *
 * @author xiexu
 */
@Getter
public class QueryResponse {

    /**
     * 最终返回给调用方的答案正文。
     *
     * <p>这个字段承载问答链路生成或降级拼装后的可展示文本，前端会直接把它作为主答案渲染。
     * 引用标记、结构化证据和 fallback 原因都围绕这段文本解释答案是如何形成的。</p>
     */
    private final String answer;

    /**
     * 答案关联的来源列表。
     *
     * <p>这些来源用于支撑答案中的引用和溯源展示。即使最终答案来自 fallback，
     * 调用方也会通过这个列表看到系统采用了哪些文件、文章或证据作为回答依据。</p>
     */
    private final List<QuerySourceResponse> sources;

    /**
     * 检索命中的已编译文章列表。
     *
     * <p>与 sources 互补——sources 偏向原始资料溯源，articles 偏向编译加工后的知识文章。
     * 每条记录包含文章摘要、内容片段和基础审计信息，调用方可据此了解检索覆盖了哪些已整理的知识。</p>
     */
    private final List<QueryArticleResponse> articles;

    /**
     * 本次查询的唯一业务标识。
     *
     * <p>调用方可以用它关联审计日志、反馈记录和检索追踪。
     * 当查询未进入 answer audit 流程时，该字段可能为空。</p>
     */
    private final String queryId;

    /**
     * 答案的整体审查结论。
     *
     * <p>这个字段帮助调用方判断当前答案是否已经过审查、审查是否通过。
     * 它和 {@link #citationCheck} 不同——citationCheck 只关注引用维度的核验，
     * reviewStatus 是对答案完整性、安全性和正确性的综合审查结果。</p>
     */
    private final String reviewStatus;

    /**
     * 答案的结果语义分类。
     *
     * <p>调用方通过这个字段快速判断答案质量——是成功给出了完整回答，
     * 还是仅能给出部分答案，或是因证据不足无法回答。配合 {@link #fallbackReason}
     * 可以完整理解答案的形成过程和局限性。</p>
     */
    private final AnswerOutcome answerOutcome;

    /**
     * 答案的生成方式。
     *
     * <p>告诉调用方这个答案是模型实时生成的、走确定性模板降级的、还是系统判定无法回答的。
     * 不同模式对应不同的答案可靠性预期，调用方可据此决定前端展示策略——
     * 例如对 fallback 答案是否需要额外提示用户。</p>
     */
    private final GenerationMode generationMode;

    /**
     * 模型调用的最终执行状态。
     *
     * <p>当 generationMode 为 LLM 时，这个字段反映模型调用是否成功、是否超时、是否被拒绝。
     * 调用方可以用它判断模型层面的健康度，区分"模型没调用"和"调用了但失败了"两种情形。
     * 当 generationMode 非 LLM 时，这个字段通常为空。</p>
     */
    private final ModelExecutionStatus modelExecutionStatus;

    /**
     * 引用核验的摘要信息。
     *
     * <p>记录了对答案中各项引用的核验结果——哪些引用能在来源中找到支撑、
     * 哪些疑似编造、整体引用覆盖率如何。调用方通过它可以评估答案的事实依据是否可靠，
     * 而不需要自己去逐条比对引用和来源。</p>
     */
    private final CitationCheckSummary citationCheck;

    /**
     * Deep Research 编排摘要。
     *
     * <p>仅当查询触发了多层深度研究流程时才有内容，包含研究计划、子问题分解、
     * 调用统计和汇总结论。普通问答场景下这个字段为空——调用方可以通过判空来区分
     * 本次查询是否经过了深度研究编排。</p>
     */
    private final DeepResearchSummary deepResearch;

    /**
     * 进入 fallback 模式的原因。
     *
     * <p>当系统判定无法用模型生成回答时，会通过这个字段说明具体触发原因——
     * 例如证据不足、模型不可用、安全策略拦截等。调用方在 answerOutcome 为
     * INSUFFICIENT_EVIDENCE 或 generationMode 为 FALLBACK 时，
     * 可以读取这个字段向用户解释为什么没有得到模型生成的答案。</p>
     */
    private final String fallbackReason;

    /**
     * 答案文本中的引用标记位置列表。
     *
     * <p>每个标记记录了引用序号在 answer 文本中的起止字符位置，以及它指向的来源。
     * 前端通过这个列表可以在答案文本中渲染可点击的引用角标，并把角标和 sources
     * 列表中的具体来源关联起来。如果答案没有引用，这个列表为空。</p>
     */
    private final List<QueryCitationMarkerResponse> citationMarkers;

    /**
     * 结构化事实证据汇总。
     *
     * <p>当查询涉及数据库表、接口路径、配置项等结构化信息时，这个字段承载编译和检索
     * 链路抽取的事实卡片——例如某个配置项的取值、某个接口的参数约束。
     * 调用方可以把它展示为答案之外的结构化补充信息。纯文本问答场景下这个字段为空。</p>
     */
    private final QueryStructuredEvidenceResponse structuredEvidence;

    /**
     * 创建查询响应。
     *
     * @param answer 答案
     * @param sources 来源列表
     * @param articles 命中文章列表
     * @param queryId 查询标识
     * @param reviewStatus 审查状态
     * @param answerOutcome 答案语义
     * @param generationMode 生成模式
     * @param modelExecutionStatus 模型执行状态
     * @param citationCheck 引用核验摘要
     * @param deepResearch 深度研究摘要
     * @param fallbackReason fallback 原因
     * @param citationMarkers 答案引用点列表
     * @param structuredEvidence 结构化证据
     */
    @Builder
    @JsonCreator
    public QueryResponse(
            @JsonProperty("answer") String answer,
            @JsonProperty("sources") List<QuerySourceResponse> sources,
            @JsonProperty("articles") List<QueryArticleResponse> articles,
            @JsonProperty("queryId") String queryId,
            @JsonProperty("reviewStatus") String reviewStatus,
            @JsonProperty("answerOutcome") AnswerOutcome answerOutcome,
            @JsonProperty("generationMode") GenerationMode generationMode,
            @JsonProperty("modelExecutionStatus") ModelExecutionStatus modelExecutionStatus,
            @JsonProperty("citationCheck") CitationCheckSummary citationCheck,
            @JsonProperty("deepResearch") DeepResearchSummary deepResearch,
            @JsonProperty("fallbackReason") String fallbackReason,
            @JsonProperty("citationMarkers") List<QueryCitationMarkerResponse> citationMarkers,
            @JsonProperty("structuredEvidence") QueryStructuredEvidenceResponse structuredEvidence
    ) {
        this.answer = answer;
        this.sources = sources;
        this.articles = articles;
        this.queryId = queryId;
        this.reviewStatus = reviewStatus;
        this.answerOutcome = answerOutcome;
        this.generationMode = generationMode;
        this.modelExecutionStatus = modelExecutionStatus;
        this.citationCheck = citationCheck;
        this.deepResearch = deepResearch;
        this.fallbackReason = fallbackReason;
        this.citationMarkers = citationMarkers == null ? List.of() : citationMarkers;
        this.structuredEvidence = structuredEvidence;
    }
}
