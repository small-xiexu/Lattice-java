package com.xbk.lattice.llm.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * LLM 模型配置
 *
 * 职责：表示后台维护的单条模型参数记录
 *
 * @author xiexu
 */
public class LlmModelProfile {

    public static final String MODEL_KIND_CHAT = "CHAT";

    public static final String MODEL_KIND_EMBEDDING = "EMBEDDING";

    private final Long id;

    private final String modelCode;

    private final Long connectionId;

    private final String modelName;

    private final String modelKind;

    private final Integer expectedDimensions;

    private final boolean supportsDimensionOverride;

    private final BigDecimal temperature;

    private final Integer maxTokens;

    private final Integer timeoutSeconds;

    private final BigDecimal inputPricePer1kTokens;

    private final BigDecimal outputPricePer1kTokens;

    private final String extraOptionsJson;

    private final boolean enabled;

    private final String remarks;

    private final String createdBy;

    private final String updatedBy;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    /**
     * 创建模型配置。
     *
     * @param id 主键
     * @param modelCode 模型编码
     * @param connectionId 连接 ID
     * @param modelName 模型名称
     * @param modelKind 模型类型
     * @param expectedDimensions 期望维度
     * @param supportsDimensionOverride 是否支持维度覆盖
     * @param temperature 温度参数
     * @param maxTokens 最大输出 token
     * @param timeoutSeconds 超时秒数
     * @param inputPricePer1kTokens 输入单价
     * @param outputPricePer1kTokens 输出单价
     * @param extraOptionsJson 扩展参数 JSON
     * @param enabled 是否启用
     * @param remarks 备注
     * @param createdBy 创建人
     * @param updatedBy 更新人
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public LlmModelProfile(
            Long id,
            String modelCode,
            Long connectionId,
            String modelName,
            String modelKind,
            Integer expectedDimensions,
            boolean supportsDimensionOverride,
            BigDecimal temperature,
            Integer maxTokens,
            Integer timeoutSeconds,
            BigDecimal inputPricePer1kTokens,
            BigDecimal outputPricePer1kTokens,
            String extraOptionsJson,
            boolean enabled,
            String remarks,
            String createdBy,
            String updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.modelCode = modelCode;
        this.connectionId = connectionId;
        this.modelName = modelName;
        this.modelKind = modelKind;
        this.expectedDimensions = expectedDimensions;
        this.supportsDimensionOverride = supportsDimensionOverride;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
        this.inputPricePer1kTokens = inputPricePer1kTokens;
        this.outputPricePer1kTokens = outputPricePer1kTokens;
        this.extraOptionsJson = extraOptionsJson;
        this.enabled = enabled;
        this.remarks = remarks;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
     * 返回模型编码。
     *
     * @return 模型编码
     */
    public String getModelCode() {
        return modelCode;
    }

    /**
     * 返回连接 ID。
     *
     * @return 连接 ID
     */
    public Long getConnectionId() {
        return connectionId;
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
     * 返回模型类型。
     *
     * @return 模型类型
     */
    public String getModelKind() {
        return modelKind;
    }

    /**
     * 返回期望维度。
     *
     * @return 期望维度
     */
    public Integer getExpectedDimensions() {
        return expectedDimensions;
    }

    /**
     * 返回是否支持维度覆盖。
     *
     * @return 是否支持维度覆盖
     */
    public boolean isSupportsDimensionOverride() {
        return supportsDimensionOverride;
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
     * 返回扩展参数 JSON。
     *
     * @return 扩展参数 JSON
     */
    public String getExtraOptionsJson() {
        return extraOptionsJson;
    }

    /**
     * 返回是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 返回备注。
     *
     * @return 备注
     */
    public String getRemarks() {
        return remarks;
    }

    /**
     * 返回创建人。
     *
     * @return 创建人
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * 返回更新人。
     *
     * @return 更新人
     */
    public String getUpdatedBy() {
        return updatedBy;
    }

    /**
     * 返回创建时间。
     *
     * @return 创建时间
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 返回更新时间。
     *
     * @return 更新时间
     */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
