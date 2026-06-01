package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 管理侧向量配置响应。
 *
 * <p>返回当前生效的向量配置、profile 摘要、配置来源与索引重建建议，
 * 由 {@code AdminVectorIndexController} 从持久化配置与运行态状态组装。
 *
 * @author xiexu
 */
@Getter
public class AdminVectorConfigResponse {

    /**
     * 当前向量检索是否启用。
     *
     * <p>{@code false} 时前端应展示禁用态并提示影响范围——召回将退化到非向量模式。</p>
     */
    private final boolean vectorEnabled;

    /**
     * 当前生效的 embedding 模型配置主键。
     *
     * <p>为 {@code null} 表示未配置 embedding 模型。与索引内实际模型名不一致时
     * {@code rebuildRecommended} 为 {@code true}。</p>
     */
    private final Long embeddingModelProfileId;

    /**
     * 当前 embedding provider 类型（如 {@code openai} / {@code local}）。
     *
     * <p>仅用于管理侧展示，不参与检索路径决策。</p>
     */
    private final String providerType;

    /**
     * 当前 embedding 模型名称。
     *
     * <p>索引内模型名与此不一致时触发 {@code rebuildRecommended=true}。</p>
     */
    private final String modelName;

    /**
     * profile 配置的向量维度。
     *
     * <p>与 {@code schemaDimensions} 不一致时触发 {@code rebuildRecommended=true}。
     * 为 {@code null} 表示 profile 未就绪。</p>
     */
    private final Integer profileDimensions;

    /**
     * 配置来源标识（如 {@code manual} / {@code auto}）。
     *
     * <p>用于管理侧追溯配置变更路径，不参与检索行为。</p>
     */
    private final String configSource;

    /**
     * 是否建议重建向量索引。
     *
     * <p>维度不匹配或模型切换后为 {@code true}。管理侧可据此展示重建引导，
     * 但不强制——忽略此建议继续使用不匹配模型将导致检索质量下降。</p>
     */
    private final boolean rebuildRecommended;

    /**
     * 建议重建的原因说明。
     *
     * <p>{@code rebuildRecommended=false} 时可为空字符串或 {@code null}。</p>
     */
    private final String rebuildReason;

    /**
     * 配置创建人。
     *
     * <p>用于审计追踪，不参与检索行为。</p>
     */
    private final String createdBy;

    /**
     * 配置最后更新人。
     *
     * <p>用于审计追踪，不参与检索行为。</p>
     */
    private final String updatedBy;

    /**
     * 配置创建时间（ISO-8601 字符串）。
     */
    private final String createdAt;

    /**
     * 配置最后更新时间（ISO-8601 字符串）。
     */
    private final String updatedAt;

    /**
     * 创建管理侧向量配置响应。
     *
     * @param vectorEnabled 是否启用向量检索
     * @param embeddingModelProfileId embedding 模型配置主键
     * @param providerType provider 类型
     * @param modelName 模型名称
     * @param profileDimensions profile 维度
     * @param configSource 配置来源
     * @param rebuildRecommended 是否建议重建向量索引
     * @param rebuildReason 建议原因
     * @param createdBy 创建人
     * @param updatedBy 更新人
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public AdminVectorConfigResponse(
            boolean vectorEnabled,
            Long embeddingModelProfileId,
            String providerType,
            String modelName,
            Integer profileDimensions,
            String configSource,
            boolean rebuildRecommended,
            String rebuildReason,
            String createdBy,
            String updatedBy,
            String createdAt,
            String updatedAt
    ) {
        this.vectorEnabled = vectorEnabled;
        this.embeddingModelProfileId = embeddingModelProfileId;
        this.providerType = providerType;
        this.modelName = modelName;
        this.profileDimensions = profileDimensions;
        this.configSource = configSource;
        this.rebuildRecommended = rebuildRecommended;
        this.rebuildReason = rebuildReason;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
