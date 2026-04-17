package com.xbk.lattice.llm.service;

import java.math.BigDecimal;

/**
 * LLM 路由解析结果
 *
 * 职责：承载一次实际调用所需的稳定路由、连接和定价信息
 *
 * @author xiexu
 */
public class LlmRouteResolution {

    private final String scopeType;

    private final String scopeId;

    private final String scene;

    private final String agentRole;

    private final Long bindingId;

    private final Long snapshotId;

    private final Integer snapshotVersion;

    private final String routeLabel;

    private final String providerType;

    private final String baseUrl;

    private final String apiKey;

    private final String modelName;

    private final BigDecimal temperature;

    private final Integer maxTokens;

    private final Integer timeoutSeconds;

    private final String extraOptionsJson;

    private final BigDecimal inputPricePer1kTokens;

    private final BigDecimal outputPricePer1kTokens;

    private final boolean snapshotBacked;

    /**
     * 创建 LLM 路由解析结果。
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param bindingId 绑定 ID
     * @param snapshotId 快照 ID
     * @param snapshotVersion 快照版本
     * @param routeLabel 路由标签
     * @param providerType Provider 类型
     * @param baseUrl 基础地址
     * @param apiKey 解密后的 API Key
     * @param modelName 模型名称
     * @param temperature 温度参数
     * @param maxTokens 最大输出 token
     * @param timeoutSeconds 超时秒数
     * @param extraOptionsJson 扩展参数 JSON
     * @param inputPricePer1kTokens 输入单价
     * @param outputPricePer1kTokens 输出单价
     * @param snapshotBacked 是否由快照支撑
     */
    public LlmRouteResolution(
            String scopeType,
            String scopeId,
            String scene,
            String agentRole,
            Long bindingId,
            Long snapshotId,
            Integer snapshotVersion,
            String routeLabel,
            String providerType,
            String baseUrl,
            String apiKey,
            String modelName,
            BigDecimal temperature,
            Integer maxTokens,
            Integer timeoutSeconds,
            String extraOptionsJson,
            BigDecimal inputPricePer1kTokens,
            BigDecimal outputPricePer1kTokens,
            boolean snapshotBacked
    ) {
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.scene = scene;
        this.agentRole = agentRole;
        this.bindingId = bindingId;
        this.snapshotId = snapshotId;
        this.snapshotVersion = snapshotVersion;
        this.routeLabel = routeLabel;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
        this.extraOptionsJson = extraOptionsJson;
        this.inputPricePer1kTokens = inputPricePer1kTokens;
        this.outputPricePer1kTokens = outputPricePer1kTokens;
        this.snapshotBacked = snapshotBacked;
    }

    /**
     * 返回作用域类型。
     *
     * @return 作用域类型
     */
    public String getScopeType() {
        return scopeType;
    }

    /**
     * 返回作用域标识。
     *
     * @return 作用域标识
     */
    public String getScopeId() {
        return scopeId;
    }

    /**
     * 返回场景。
     *
     * @return 场景
     */
    public String getScene() {
        return scene;
    }

    /**
     * 返回 Agent 角色。
     *
     * @return Agent 角色
     */
    public String getAgentRole() {
        return agentRole;
    }

    /**
     * 返回绑定 ID。
     *
     * @return 绑定 ID
     */
    public Long getBindingId() {
        return bindingId;
    }

    /**
     * 返回快照 ID。
     *
     * @return 快照 ID
     */
    public Long getSnapshotId() {
        return snapshotId;
    }

    /**
     * 返回快照版本。
     *
     * @return 快照版本
     */
    public Integer getSnapshotVersion() {
        return snapshotVersion;
    }

    /**
     * 返回路由标签。
     *
     * @return 路由标签
     */
    public String getRouteLabel() {
        return routeLabel;
    }

    /**
     * 返回 Provider 类型。
     *
     * @return Provider 类型
     */
    public String getProviderType() {
        return providerType;
    }

    /**
     * 返回基础地址。
     *
     * @return 基础地址
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 返回 API Key。
     *
     * @return API Key
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 返回模型名称。
     *
     * @return 模型名称
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * 返回温度参数。
     *
     * @return 温度参数
     */
    public BigDecimal getTemperature() {
        return temperature;
    }

    /**
     * 返回最大输出 token。
     *
     * @return 最大输出 token
     */
    public Integer getMaxTokens() {
        return maxTokens;
    }

    /**
     * 返回超时秒数。
     *
     * @return 超时秒数
     */
    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * 返回扩展参数 JSON。
     *
     * @return 扩展参数 JSON
     */
    public String getExtraOptionsJson() {
        return extraOptionsJson;
    }

    /**
     * 返回输入单价。
     *
     * @return 输入单价
     */
    public BigDecimal getInputPricePer1kTokens() {
        return inputPricePer1kTokens;
    }

    /**
     * 返回输出单价。
     *
     * @return 输出单价
     */
    public BigDecimal getOutputPricePer1kTokens() {
        return outputPricePer1kTokens;
    }

    /**
     * 返回是否由快照支撑。
     *
     * @return 是否由快照支撑
     */
    public boolean isSnapshotBacked() {
        return snapshotBacked;
    }

    /**
     * 返回缓存维度键。
     *
     * @return 缓存维度键
     */
    public String cacheDimensionKey() {
        StringBuilder builder = new StringBuilder();
        builder.append(routeLabel == null ? "unlabeled" : routeLabel);
        builder.append(":");
        builder.append(bindingId == null ? "no-binding" : bindingId);
        builder.append(":");
        builder.append(snapshotVersion == null ? "0" : snapshotVersion);
        return builder.toString();
    }
}
