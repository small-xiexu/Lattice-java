package com.xbk.lattice.api.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询请求。
 *
 * <p>承载查询接口的请求参数，由 Spring MVC 从 JSON 请求体反序列化绑定。
 * 所有字段可选（除 question 有 @NotBlank 校验），未传的字段使用默认行为。
 *
 * @author xiexu
 */
public class QueryRequest {

    /**
     * 用户输入的自然语言问题。
     *
     * <p>必填（@NotBlank），会经过 Query Rewrite 标准化后作为各检索通道的输入。</p>
     */
    @NotBlank(message = "question 不能为空")
    private String question;

    /**
     * 是否强制走 Deep Research 深度研究路径。
     *
     * <p>由调用方显式传入。为 true 时跳过路由判断直接进入多层研究编排；
     * 为 null 或 false 时由系统根据问题复杂度自动决定是否走 Deep Research。</p>
     */
    private Boolean forceDeep;

    /**
     * 是否强制走简单问答路径。
     *
     * <p>为 true 时跳过 Deep Research 路由和复杂编排，直接走单层问答。
     * 与 forceDeep 互斥——两者同时为 true 时行为未定义，调用方不应同时设置。</p>
     */
    private Boolean forceSimple;

    /**
     * Deep Research 的最大 LLM 调用次数上限。
     *
     * <p>限制深度研究过程中 LLM 的总调用次数，用于控制成本和延迟。
     * 为 null 时使用系统默认上限。</p>
     */
    private Integer maxLlmCalls;

    /**
     * 整体查询超时时间（毫秒）。
     *
     * <p>从接收请求到返回响应的总时限。超过此时限后查询会被中断并返回超时错误。
     * 为 null 时使用系统默认超时。</p>
     */
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
