package com.xbk.lattice.llm.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 运行时 LLM 快照。
 *
 * <p>表示某次执行作用域冻结后的稳定 LLM 路由快照——记录该时刻的 provider、模型、参数和定价。
 * 用于审计追溯、成本估算、回放排查，避免运行期配置变化影响历史记录的可解释性。
 *
 * @author xiexu
 */
@Getter
public class ExecutionLlmSnapshot {

    /** 快照主键。 */
    private final Long id;
    /** 作用域类型（如 compile_job / query_session）。 */
    private final String scopeType;
    /** 作用域标识（如 jobId / queryId）。 */
    private final String scopeId;
    /** 场景标识。 */
    private final String scene;
    /** Agent 角色。 */
    private final String agentRole;
    /** 绑定的 Agent 模型绑定主键。 */
    private final Long bindingId;
    /** 绑定的模型配置主键。 */
    private final Long modelProfileId;
    /** 绑定的连接配置主键。 */
    private final Long connectionId;
    /** 路由标签。 */
    private final String routeLabel;
    /** Provider 类型。 */
    private final String providerType;
    /** API 端点 URL。 */
    private final String baseUrl;
    /** 模型名称。 */
    private final String modelName;
    /** 温度参数。 */
    private final BigDecimal temperature;
    /** 最大输出 token 数。 */
    private final Integer maxTokens;
    /** 请求超时秒数。 */
    private final Integer timeoutSeconds;
    /** 扩展参数 JSON（快照时刻的配置）。 */
    private final String extraOptionsJson;
    /** 输入价格（快照时刻的定价）。用于成本估算。 */
    private final BigDecimal inputPricePer1kTokens;
    /** 输出价格（快照时刻的定价）。用于成本估算。 */
    private final BigDecimal outputPricePer1kTokens;
    /** 快照版本号（递增）。 */
    private final Integer snapshotVersion;
    /** 快照创建时间。 */
    private final OffsetDateTime createdAt;

    public ExecutionLlmSnapshot(
            Long id, String scopeType, String scopeId, String scene, String agentRole,
            Long bindingId, Long modelProfileId, Long connectionId, String routeLabel,
            String providerType, String baseUrl, String modelName, BigDecimal temperature,
            Integer maxTokens, Integer timeoutSeconds, String extraOptionsJson,
            BigDecimal inputPricePer1kTokens, BigDecimal outputPricePer1kTokens,
            Integer snapshotVersion, OffsetDateTime createdAt
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
}
