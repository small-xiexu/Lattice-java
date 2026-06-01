package com.xbk.lattice.llm.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * LLM 模型配置。
 *
 * <p>表示后台维护的单条模型参数记录——含模型标识、类别、维度、超参、定价和审计信息。
 * {@code MODEL_KIND_CHAT/EMBEDDING} 常量用于区分模型类别和校验 expectedDimensions。
 *
 * @author xiexu
 */
@Getter
public class LlmModelProfile {

    public static final String MODEL_KIND_CHAT = "CHAT";
    public static final String MODEL_KIND_EMBEDDING = "EMBEDDING";

    /** 模型配置主键。 */
    private final Long id;
    /** 模型编码（系统内唯一标识）。 */
    private final String modelCode;
    /** 关联连接配置主键。 */
    private final Long connectionId;
    /** Provider 模型名（如 gpt-4 / claude-sonnet-4-6）。 */
    private final String modelName;
    /** 模型类别（CHAT / EMBEDDING）。决定 expectedDimensions 校验规则。 */
    private final String modelKind;
    /** 期望向量维度。仅 EMBEDDING 模型使用。与实际模型不匹配时检索异常。 */
    private final Integer expectedDimensions;
    /** 是否支持维度覆写。仅部分 embedding 模型支持。 */
    private final boolean supportsDimensionOverride;
    /** 温度参数（控制生成随机性）。 */
    private final BigDecimal temperature;
    /** 最大输出 token 数。 */
    private final Integer maxTokens;
    /** 请求超时秒数。 */
    private final Integer timeoutSeconds;
    /** 输入价格（每千 token）。 */
    private final BigDecimal inputPricePer1kTokens;
    /** 输出价格（每千 token）。 */
    private final BigDecimal outputPricePer1kTokens;
    /** Provider 扩展配置 JSON。可能含 provider 特有参数。 */
    private final String extraOptionsJson;
    /** 是否启用。 */
    private final boolean enabled;
    /** 备注。 */
    private final String remarks;
    /** 创建人。 */
    private final String createdBy;
    /** 最后更新人。 */
    private final String updatedBy;
    /** 创建时间。 */
    private final OffsetDateTime createdAt;
    /** 最后更新时间。 */
    private final OffsetDateTime updatedAt;

    public LlmModelProfile(
            Long id, String modelCode, Long connectionId, String modelName, String modelKind,
            Integer expectedDimensions, boolean supportsDimensionOverride, BigDecimal temperature,
            Integer maxTokens, Integer timeoutSeconds, BigDecimal inputPricePer1kTokens,
            BigDecimal outputPricePer1kTokens, String extraOptionsJson, boolean enabled,
            String remarks, String createdBy, String updatedBy,
            OffsetDateTime createdAt, OffsetDateTime updatedAt
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
}
