package com.xbk.lattice.llm.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 运行时 LLM 快照
 *
 * 职责：表示某次执行作用域冻结后的稳定 LLM 路由快照
 *
 * @author xiexu
 */
public class ExecutionLlmSnapshot {

    private final Long id;

    private final String scopeType;

    private final String scopeId;

    private final String scene;

    private final String agentRole;

    private final Long bindingId;

    private final Long modelProfileId;

    private final Long connectionId;

    private final String routeLabel;

    private final String providerType;

    private final String baseUrl;

    private final String modelName;

    private final BigDecimal temperature;

    private final Integer maxTokens;

    private final Integer timeoutSeconds;

    private final String extraOptionsJson;

    private final BigDecimal inputPricePer1kTokens;

    private final BigDecimal outputPricePer1kTokens;

    private final Integer snapshotVersion;

    private final OffsetDateTime createdAt;

    /**
     * 创建运行时 LLM 快照。
     *
     * @param id 主键
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param bindingId 绑定 ID
     * @param modelProfileId 模型配置 ID
     * @param connectionId 连接配置 ID
     * @param routeLabel 路由标签
     * @param providerType Provider 类型
     * @param baseUrl 基础地址
     * @param modelName 模型名称
     * @param temperature 温度参数
     * @param maxTokens 最大输出 token
     * @param timeoutSeconds 超时秒数
     * @param extraOptionsJson 扩展参数 JSON
     * @param inputPricePer1kTokens 输入单价
     * @param outputPricePer1kTokens 输出单价
     * @param snapshotVersion 快照版本
     * @param createdAt 创建时间
     */
    public ExecutionLlmSnapshot(
            Long id,
            String scopeType,
            String scopeId,
            String scene,
            String agentRole,
            Long bindingId,
            Long modelProfileId,
            Long connectionId,
            String routeLabel,
            String providerType,
            String baseUrl,
            String modelName,
            BigDecimal temperature,
            Integer maxTokens,
            Integer timeoutSeconds,
            String extraOptionsJson,
            BigDecimal inputPricePer1kTokens,
            BigDecimal outputPricePer1kTokens,
            Integer snapshotVersion,
            OffsetDateTime createdAt
    ) {
        this.id = id;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.scene = scene;
        this.agentRole = agentRole;
        this.bindingId = bindingId;
        this.modelProfileId = modelProfileId;
        this.connectionId = connectionId;
        this.routeLabel = routeLabel;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
        this.extraOptionsJson = extraOptionsJson;
        this.inputPricePer1kTokens = inputPricePer1kTokens;
        this.outputPricePer1kTokens = outputPricePer1kTokens;
        this.snapshotVersion = snapshotVersion;
        this.createdAt = createdAt;
    }

    /**
     * 返回主键。
     *
     * @return 主键
     */
    public Long getId() {
        return id;
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
     * 返回模型配置 ID。
     *
     * @return 模型配置 ID
     */
    public Long getModelProfileId() {
        return modelProfileId;
    }

    /**
     * 返回连接配置 ID。
     *
     * @return 连接配置 ID
     */
    public Long getConnectionId() {
        return connectionId;
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
     * 返回快照版本。
     *
     * @return 快照版本
     */
    public Integer getSnapshotVersion() {
        return snapshotVersion;
    }

    /**
     * 返回创建时间。
     *
     * @return 创建时间
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
