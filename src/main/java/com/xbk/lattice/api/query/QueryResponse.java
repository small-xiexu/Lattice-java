package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;

import java.util.List;

/**
 * 查询响应
 *
 * 职责：承载最小查询闭环的返回结果
 *
 * @author xiexu
 */
public class QueryResponse {

    private final String answer;

    private final List<QuerySourceResponse> sources;

    private final List<QueryArticleResponse> articles;

    private final String queryId;

    private final String reviewStatus;

    private final AnswerOutcome answerOutcome;

    private final GenerationMode generationMode;

    private final ModelExecutionStatus modelExecutionStatus;

    private final CitationCheckSummary citationCheck;

    private final DeepResearchSummary deepResearch;

    private final String fallbackReason;

    private final List<QueryCitationMarkerResponse> citationMarkers;

    /**
     * 创建查询响应。
     *
     * @param answer 答案
     * @param sources 来源列表
     * @param articles 命中文章列表
     */
    public QueryResponse(String answer, List<QuerySourceResponse> sources, List<QueryArticleResponse> articles) {
        this(answer, sources, articles, null, null, null, null, null, null, null, "");
    }

    /**
     * 创建查询响应。
     *
     * @param answer 答案
     * @param sources 来源列表
     * @param articles 命中文章列表
     * @param queryId 待确认查询标识
     * @param reviewStatus 审查状态
     */
    public QueryResponse(
            @JsonProperty("answer") String answer,
            @JsonProperty("sources") List<QuerySourceResponse> sources,
            @JsonProperty("articles") List<QueryArticleResponse> articles,
            @JsonProperty("queryId") String queryId,
            @JsonProperty("reviewStatus") String reviewStatus
    ) {
        this(answer, sources, articles, queryId, reviewStatus, null, null, null, null, null, "");
    }

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
     */
    public QueryResponse(
            @JsonProperty("answer") String answer,
            @JsonProperty("sources") List<QuerySourceResponse> sources,
            @JsonProperty("articles") List<QueryArticleResponse> articles,
            @JsonProperty("queryId") String queryId,
            @JsonProperty("reviewStatus") String reviewStatus,
            @JsonProperty("answerOutcome") AnswerOutcome answerOutcome,
            @JsonProperty("generationMode") GenerationMode generationMode,
            @JsonProperty("modelExecutionStatus") ModelExecutionStatus modelExecutionStatus
    ) {
        this(answer, sources, articles, queryId, reviewStatus, answerOutcome, generationMode, modelExecutionStatus, null, null, "");
    }

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
     */
    public QueryResponse(
            String answer,
            List<QuerySourceResponse> sources,
            List<QueryArticleResponse> articles,
            String queryId,
            String reviewStatus,
            AnswerOutcome answerOutcome,
            GenerationMode generationMode,
            ModelExecutionStatus modelExecutionStatus,
            CitationCheckSummary citationCheck,
            DeepResearchSummary deepResearch
    ) {
        this(
                answer,
                sources,
                articles,
                queryId,
                reviewStatus,
                answerOutcome,
                generationMode,
                modelExecutionStatus,
                citationCheck,
                deepResearch,
                ""
        );
    }

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
     */
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
            @JsonProperty("fallbackReason") String fallbackReason
    ) {
        this(
                answer,
                sources,
                articles,
                queryId,
                reviewStatus,
                answerOutcome,
                generationMode,
                modelExecutionStatus,
                citationCheck,
                deepResearch,
                fallbackReason,
                List.of()
        );
    }

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
     */
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
            @JsonProperty("citationMarkers") List<QueryCitationMarkerResponse> citationMarkers
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
    }

    /**
     * 获取答案。
     *
     * @return 答案
     */
    public String getAnswer() {
        return answer;
    }

    /**
     * 获取来源列表。
     *
     * @return 来源列表
     */
    public List<QuerySourceResponse> getSources() {
        return sources;
    }

    /**
     * 获取命中文章列表。
     *
     * @return 命中文章列表
     */
    public List<QueryArticleResponse> getArticles() {
        return articles;
    }

    /**
     * 获取待确认查询标识。
     *
     * @return 待确认查询标识
     */
    public String getQueryId() {
        return queryId;
    }

    /**
     * 获取审查状态。
     *
     * @return 审查状态
     */
    public String getReviewStatus() {
        return reviewStatus;
    }

    /**
     * 获取答案语义。
     *
     * @return 答案语义
     */
    public AnswerOutcome getAnswerOutcome() {
        return answerOutcome;
    }

    /**
     * 获取生成模式。
     *
     * @return 生成模式
     */
    public GenerationMode getGenerationMode() {
        return generationMode;
    }

    /**
     * 获取模型执行状态。
     *
     * @return 模型执行状态
     */
    public ModelExecutionStatus getModelExecutionStatus() {
        return modelExecutionStatus;
    }

    /**
     * 获取引用核验摘要。
     *
     * @return 引用核验摘要
     */
    public CitationCheckSummary getCitationCheck() {
        return citationCheck;
    }

    /**
     * 获取 Deep Research 摘要。
     *
     * @return Deep Research 摘要
     */
    public DeepResearchSummary getDeepResearch() {
        return deepResearch;
    }

    /**
     * 获取 fallback 原因。
     *
     * @return fallback 原因
     */
    public String getFallbackReason() {
        return fallbackReason;
    }

    /**
     * 获取答案引用点列表。
     *
     * @return 答案引用点列表
     */
    public List<QueryCitationMarkerResponse> getCitationMarkers() {
        return citationMarkers;
    }
}
