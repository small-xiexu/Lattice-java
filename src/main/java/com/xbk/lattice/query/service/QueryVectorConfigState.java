package com.xbk.lattice.query.service;

import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * Query 向量配置状态。
 *
 * <p>承载后台展示所需的有效向量配置、profile 摘要、来源与重建建议——
 * 由服务层从持久化配置与运行态检测组装，用于管理侧向量状态展示。
 *
 * @author xiexu
 */
@Getter
public class QueryVectorConfigState {

    /**
     * 向量检索是否启用。
     *
     * <p>{@code false} 时向量检索通道在运行期不会被使用，召回退回纯 lexical/图谱模式。</p>
     */
    private final boolean vectorEnabled;

    /**
     * embedding 模型配置主键。
     *
     * <p>为 {@code null} 表示未配置。切换模型→维度变化→现有索引全部失效→{@code rebuildRecommended=true}。</p>
     */
    private final Long embeddingModelProfileId;

    /** 当前 provider 类型。 */
    private final String providerType;

    /**
     * 当前 embedding 模型名。
     *
     * <p>与实际索引中的模型名不一致时 {@code rebuildRecommended=true}。</p>
     */
    private final String modelName;

    /**
     * profile 记录的实际维度。
     *
     * <p>为 {@code null} 表示 profile 未就绪。与 {@code schemaDimensions} 不一致时
     * {@code rebuildRecommended=true}。</p>
     */
    private final Integer profileDimensions;

    /** 配置来源（如 {@code manual} / {@code auto}）。 */
    private final String configSource;

    /**
     * 是否建议重建向量索引。
     *
     * <p>模型切换或维度不匹配时为 {@code true}。仅建议，不强制——忽略将继续使用不匹配模型。</p>
     */
    private final boolean rebuildRecommended;

    /** 建议重建的原因说明。{@code rebuildRecommended=false} 时可为空。 */
    private final String rebuildReason;

    /** 创建人。 */
    private final String createdBy;

    /** 最后更新人。 */
    private final String updatedBy;

    /** 创建时间。 */
    private final OffsetDateTime createdAt;

    /** 最后更新时间。 */
    private final OffsetDateTime updatedAt;

    /**
     * 创建 Query 向量配置状态。
     */
    public QueryVectorConfigState(
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
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
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
