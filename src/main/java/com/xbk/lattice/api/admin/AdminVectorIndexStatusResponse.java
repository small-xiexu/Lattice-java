package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧向量索引状态响应。
 *
 * <p>承载四级向量可用性检查（开关→类型→表→可索引）、维度一致性诊断、
 * ANN 索引状态与当前索引统计信息，由 {@code AdminVectorIndexController} 组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminVectorIndexStatusResponse {

    /**
     * 向量检索是否启用。
     *
     * <p>四级可用性检查的第一级。{@code false} 时后续三级检查结果仅供参考，
     * 向量检索通道在运行期不会被使用。</p>
     */
    private final boolean vectorEnabled;

    /**
     * 数据库 vector 类型是否可用（如 pgvector 扩展是否安装）。
     *
     * <p>四级检查第二级。{@code false} 表示数据库不支持向量类型，无法创建向量列和索引。</p>
     */
    private final boolean vectorTypeAvailable;

    /**
     * 向量索引表是否存在且可访问。
     *
     * <p>四级检查第三级。{@code false} 时即使前两级通过也无法写入或查询向量索引。</p>
     */
    private final boolean vectorIndexTableAvailable;

    /**
     * 当前是否可执行索引操作。
     *
     * <p>四级检查第四级，综合前三项结果与运行时锁状态。
     * {@code false} 时重建、增量索引等写操作将被拒绝。</p>
     */
    private final boolean indexingAvailable;

    /**
     * 当前配置的 embedding 模型配置主键。
     *
     * <p>为 {@code null} 表示未配置 embedding 模型。</p>
     */
    private final Long embeddingModelProfileId;

    /**
     * 当前配置的 provider 类型（如 {@code openai} / {@code local}）。
     *
     * <p>仅用于管理侧展示。</p>
     */
    private final String configuredProviderType;

    /**
     * 当前配置的 embedding 模型名。
     *
     * <p>与 {@code indexedModelNames} 不一致时说明历史索引使用了不同模型，
     * 可能存在维度不匹配的旧向量残留。</p>
     */
    private final String configuredModelName;

    /**
     * 配置期望的向量维度。
     */
    private final int configuredExpectedDimensions;

    /**
     * profile 记录的实际维度。
     *
     * <p>为 {@code null} 表示 profile 未就绪，无法读取实际维度。</p>
     */
    private final Integer profileDimensions;

    /**
     * 向量列的数据库类型（如 {@code vector(1536)}）。
     *
     * <p>仅用于管理侧确认实际 DDL 中的向量列定义。</p>
     */
    private final String embeddingColumnType;

    /**
     * 数据库 schema 中的向量维度。
     *
     * <p>为 {@code null} 表示无法从数据库 schema 读取维度信息。</p>
     */
    private final Integer schemaDimensions;

    /**
     * 配置维度与 schema 维度是否精确匹配。
     *
     * <p>为 {@code null} 表示无法判断（如 profile 未就绪）。
     * 为 {@code false} 时向量检索可能因维度不匹配而异常，
     * 管理侧应提示用户检查配置或触发重建。</p>
     */
    private final Boolean dimensionsMatch;

    /**
     * 配置、profile、schema 三维度是否一致。
     *
     * <p>比 {@code dimensionsMatch} 更宽泛的综合一致性判断——
     * 允许 profile 与 schema 存在可接受的偏差。
     * {@code false} 时管理侧应展示告警。</p>
     */
    private final boolean dimensionsConsistent;

    /**
     * ANN 近似最近邻索引是否就绪。
     *
     * <p>{@code false} 时向量相似度计算退化为全表扫描，检索延迟显著上升。
     * 可能原因：索引未创建、正在重建中、或数据库不支持 ANN。</p>
     */
    private final boolean annIndexReady;

    /**
     * ANN 索引类型（如 {@code ivfflat} / {@code hnsw}）。
     *
     * <p>仅用于管理侧确认索引算法选择。</p>
     */
    private final String annIndexType;

    /**
     * 文章总数。
     */
    private final int articleCount;

    /**
     * 已向量索引的文章数。
     *
     * <p>与 {@code articleCount} 对比可知索引覆盖率。
     * 覆盖率低于 100% 时部分文章无法通过向量通道检索。</p>
     */
    private final int indexedArticleCount;

    /**
     * 当前索引中出现过的所有模型名。
     *
     * <p>多模型共存说明历史上线过不同 embedding 模型。
     * 切换模型后旧向量不会自动清理——旧维度向量仍在索引中但可能无法用于当前模型。
     * 管理侧可据此判断是否需要全量重建以清理旧模型残留。</p>
     */
    private final List<String> indexedModelNames;

    /**
     * 索引最近更新时间（ISO-8601 字符串）。
     */
    private final String latestUpdatedAt;

    /**
     * 创建管理侧向量索引状态响应。
     *
     * @param vectorEnabled 是否启用向量索引
     * @param vectorTypeAvailable vector 类型是否可用
     * @param vectorIndexTableAvailable 向量索引表是否可用
     * @param indexingAvailable 当前是否可执行向量索引
     * @param embeddingModelProfileId 当前配置的 embedding profile 主键
     * @param configuredProviderType 当前配置的 provider 类型
     * @param configuredModelName 当前配置的 embedding 模型名
     * @param configuredExpectedDimensions 当前配置的期望维度
     * @param profileDimensions 当前 profile 维度
     * @param embeddingColumnType 向量列数据库类型
     * @param schemaDimensions 数据库维度
     * @param dimensionsMatch 配置维度是否与数据库一致
     * @param dimensionsConsistent 维度是否一致
     * @param annIndexReady ANN 索引是否就绪
     * @param annIndexType ANN 索引类型
     * @param articleCount 当前文章总数
     * @param indexedArticleCount 当前向量索引总数
     * @param indexedModelNames 当前索引内出现过的模型名
     * @param latestUpdatedAt 最近更新时间
     */
    public AdminVectorIndexStatusResponse(
            boolean vectorEnabled,
            boolean vectorTypeAvailable,
            boolean vectorIndexTableAvailable,
            boolean indexingAvailable,
            Long embeddingModelProfileId,
            String configuredProviderType,
            String configuredModelName,
            int configuredExpectedDimensions,
            Integer profileDimensions,
            String embeddingColumnType,
            Integer schemaDimensions,
            Boolean dimensionsMatch,
            boolean dimensionsConsistent,
            boolean annIndexReady,
            String annIndexType,
            int articleCount,
            int indexedArticleCount,
            List<String> indexedModelNames,
            String latestUpdatedAt
    ) {
        this.vectorEnabled = vectorEnabled;
        this.vectorTypeAvailable = vectorTypeAvailable;
        this.vectorIndexTableAvailable = vectorIndexTableAvailable;
        this.indexingAvailable = indexingAvailable;
        this.embeddingModelProfileId = embeddingModelProfileId;
        this.configuredProviderType = configuredProviderType;
        this.configuredModelName = configuredModelName;
        this.configuredExpectedDimensions = configuredExpectedDimensions;
        this.profileDimensions = profileDimensions;
        this.embeddingColumnType = embeddingColumnType;
        this.schemaDimensions = schemaDimensions;
        this.dimensionsMatch = dimensionsMatch;
        this.dimensionsConsistent = dimensionsConsistent;
        this.annIndexReady = annIndexReady;
        this.annIndexType = annIndexType;
        this.articleCount = articleCount;
        this.indexedArticleCount = indexedArticleCount;
        this.indexedModelNames = indexedModelNames;
        this.latestUpdatedAt = latestUpdatedAt;
    }
}
