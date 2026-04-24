package com.xbk.lattice.api.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询请求
 *
 * 职责：承载最小查询接口的请求参数
 *
 * @author xiexu
 */
public class QueryRequest {

    @NotBlank(message = "question 不能为空")
    private String question;

    private Boolean forceDeep;

    private Boolean forceSimple;

    private Integer maxLlmCalls;

    private Integer overallTimeoutMs;

    /**
     * 获取查询问题。
     *
     * @return 查询问题
     */
    public String getQuestion() {
        return question;
    }

    /**
     * 设置查询问题。
     *
     * @param question 查询问题
     */
    public void setQuestion(String question) {
        this.question = question;
    }

    /**
     * 返回是否强制走 Deep Research。
     *
     * @return 是否强制走 Deep Research
     */
    public Boolean getForceDeep() {
        return forceDeep;
    }

    /**
     * 设置是否强制走 Deep Research。
     *
     * @param forceDeep 是否强制走 Deep Research
     */
    public void setForceDeep(Boolean forceDeep) {
        this.forceDeep = forceDeep;
    }

    /**
     * 返回是否强制走简单问答。
     *
     * @return 是否强制走简单问答
     */
    public Boolean getForceSimple() {
        return forceSimple;
    }

    /**
     * 设置是否强制走简单问答。
     *
     * @param forceSimple 是否强制走简单问答
     */
    public void setForceSimple(Boolean forceSimple) {
        this.forceSimple = forceSimple;
    }

    /**
     * 返回 Deep Research 的最大 LLM 调用次数。
     *
     * @return 最大 LLM 调用次数
     */
    public Integer getMaxLlmCalls() {
        return maxLlmCalls;
    }

    /**
     * 设置 Deep Research 的最大 LLM 调用次数。
     *
     * @param maxLlmCalls 最大 LLM 调用次数
     */
    public void setMaxLlmCalls(Integer maxLlmCalls) {
        this.maxLlmCalls = maxLlmCalls;
    }

    /**
     * 返回整体超时时间（毫秒）。
     *
     * @return 整体超时时间（毫秒）
     */
    public Integer getOverallTimeoutMs() {
        return overallTimeoutMs;
    }

    /**
     * 设置整体超时时间（毫秒）。
     *
     * @param overallTimeoutMs 整体超时时间（毫秒）
     */
    public void setOverallTimeoutMs(Integer overallTimeoutMs) {
        this.overallTimeoutMs = overallTimeoutMs;
    }
}
