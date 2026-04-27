package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.QueryVectorConfigJdbcRepository;
import com.xbk.lattice.infra.persistence.QueryVectorConfigRecord;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.llm.domain.LlmModelProfile;
import com.xbk.lattice.llm.domain.LlmProviderConnection;
import com.xbk.lattice.llm.infra.LlmModelProfileJdbcRepository;
import com.xbk.lattice.llm.infra.LlmProviderConnectionJdbcRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Optional;

/**
 * Query 向量配置服务
 *
 * 职责：管理后台保存的向量配置，并把有效配置同步到运行时 QuerySearchProperties
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class QueryVectorConfigService {

    private static final String DEFAULT_SCOPE = "default";

    private final QuerySearchProperties querySearchProperties;

    private final QueryVectorConfigJdbcRepository queryVectorConfigJdbcRepository;

    private final LlmModelProfileJdbcRepository llmModelProfileJdbcRepository;

    private final LlmProviderConnectionJdbcRepository llmProviderConnectionJdbcRepository;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final QueryCacheStore queryCacheStore;

    private LlmGateway llmGateway;

    /**
     * 创建 Query 向量配置服务。
     *
     * @param querySearchProperties 查询检索配置
     * @param queryVectorConfigJdbcRepository 向量配置仓储
     */
    public QueryVectorConfigService(
            QuerySearchProperties querySearchProperties,
            QueryVectorConfigJdbcRepository queryVectorConfigJdbcRepository,
            LlmModelProfileJdbcRepository llmModelProfileJdbcRepository,
            LlmProviderConnectionJdbcRepository llmProviderConnectionJdbcRepository,
            ApplicationEventPublisher applicationEventPublisher,
            QueryCacheStore queryCacheStore
    ) {
        this.querySearchProperties = querySearchProperties;
        this.queryVectorConfigJdbcRepository = queryVectorConfigJdbcRepository;
        this.llmModelProfileJdbcRepository = llmModelProfileJdbcRepository;
        this.llmProviderConnectionJdbcRepository = llmProviderConnectionJdbcRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.queryCacheStore = queryCacheStore;
    }

    /**
     * 注入 LLM 网关。
     *
     * @param llmGateway LLM 网关
     */
    @Autowired(required = false)
    void setLlmGateway(LlmGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    /**
     * 在应用启动时加载持久化的向量配置覆盖项。
     */
    @PostConstruct
    public synchronized void initialize() {
        Optional<QueryVectorConfigRecord> persistedConfig = queryVectorConfigJdbcRepository.findDefault();
        if (persistedConfig.isPresent()) {
            apply(persistedConfig.orElseThrow());
        }
    }

    /**
     * 返回当前有效的向量配置状态。
     *
     * @return 向量配置状态
     */
    public synchronized QueryVectorConfigState getCurrentState() {
        Optional<QueryVectorConfigRecord> persistedConfig = queryVectorConfigJdbcRepository.findDefault();
        if (persistedConfig.isPresent()) {
            QueryVectorConfigRecord record = persistedConfig.orElseThrow();
            return toState(record, "database", false, "");
        }
        return fromProperties(false, "");
    }

    /**
     * 保存向量配置并立即应用到运行时。
     *
     * @param vectorEnabled 是否启用向量检索
     * @param embeddingModelProfileId embedding 模型配置主键
     * @param operator 操作人
     * @return 保存后的向量配置状态
     */
    @Transactional(rollbackFor = Exception.class)
    public synchronized QueryVectorConfigState save(
            boolean vectorEnabled,
            Long embeddingModelProfileId,
            String operator
    ) {
        String normalizedOperator = resolveOperator(operator);
        QueryVectorConfigState beforeState = getCurrentState();
        validateEmbeddingProfile(embeddingModelProfileId);
        Optional<QueryVectorConfigRecord> existing = queryVectorConfigJdbcRepository.findDefault();
        QueryVectorConfigRecord saved = queryVectorConfigJdbcRepository.save(new QueryVectorConfigRecord(
                DEFAULT_SCOPE,
                vectorEnabled,
                embeddingModelProfileId,
                existing.map(QueryVectorConfigRecord::getCreatedBy).orElse(normalizedOperator),
                normalizedOperator,
                existing.map(QueryVectorConfigRecord::getCreatedAt).orElse(null),
                existing.map(QueryVectorConfigRecord::getUpdatedAt).orElse(null)
        ));
        apply(saved);
        if (shouldEvictQueryCache(beforeState, saved)) {
            queryCacheStore.evictAll();
            if (llmGateway != null) {
                llmGateway.evictPromptCache();
            }
        }
        boolean rebuildRecommended = shouldRecommendRebuild(beforeState, saved);
        String rebuildReason = buildRebuildReason(beforeState, saved, rebuildRecommended);
        if (!Objects.equals(beforeState.getEmbeddingModelProfileId(), saved.getEmbeddingModelProfileId())) {
            applicationEventPublisher.publishEvent(new EmbeddingProfileChangedEvent(
                    beforeState.getEmbeddingModelProfileId(),
                    saved.getEmbeddingModelProfileId()
            ));
        }
        return toState(saved, "database", rebuildRecommended, rebuildReason);
    }

    /**
     * 应用向量配置到运行时属性。
     *
     * @param record 向量配置记录
     */
    private void apply(QueryVectorConfigRecord record) {
        querySearchProperties.getVector().setEnabled(record.isVectorEnabled());
        querySearchProperties.getVector().setEmbeddingModelProfileId(record.getEmbeddingModelProfileId());
    }

    /**
     * 基于运行时属性构造默认状态。
     *
     * @param rebuildRecommended 是否建议重建
     * @param rebuildReason 建议原因
     * @return 向量配置状态
     */
    private QueryVectorConfigState fromProperties(boolean rebuildRecommended, String rebuildReason) {
        Long embeddingModelProfileId = querySearchProperties.getVector().getEmbeddingModelProfileId();
        EmbeddingProfileSummary profileSummary = loadProfileSummary(embeddingModelProfileId)
                .orElseGet(this::buildPropertyBackedSummary);
        return new QueryVectorConfigState(
                querySearchProperties.getVector().isEnabled(),
                embeddingModelProfileId,
                profileSummary == null ? "" : profileSummary.getProviderType(),
                profileSummary == null ? "" : profileSummary.getModelName(),
                profileSummary == null ? null : profileSummary.getExpectedDimensions(),
                "properties",
                rebuildRecommended,
                rebuildReason,
                "",
                "",
                null,
                null
        );
    }

    /**
     * 基于 properties 构造 legacy embedding 摘要。
     *
     * @return legacy embedding 摘要
     */
    private EmbeddingProfileSummary buildPropertyBackedSummary() {
        String modelName = querySearchProperties.getVector().getEmbeddingModel();
        if (!StringUtils.hasText(modelName)) {
            return null;
        }
        Integer expectedDimensions = querySearchProperties.getVector().getExpectedDimensions() <= 0
                ? null
                : Integer.valueOf(querySearchProperties.getVector().getExpectedDimensions());
        return new EmbeddingProfileSummary(
                null,
                "legacy",
                modelName.trim(),
                expectedDimensions
        );
    }

    /**
     * 把记录映射为展示状态。
     *
     * @param record 向量配置记录
     * @param configSource 配置来源
     * @param rebuildRecommended 是否建议重建
     * @param rebuildReason 建议原因
     * @return 向量配置状态
     */
    private QueryVectorConfigState toState(
            QueryVectorConfigRecord record,
            String configSource,
            boolean rebuildRecommended,
            String rebuildReason
    ) {
        EmbeddingProfileSummary profileSummary = loadProfileSummary(record.getEmbeddingModelProfileId()).orElse(null);
        return new QueryVectorConfigState(
                record.isVectorEnabled(),
                record.getEmbeddingModelProfileId(),
                profileSummary == null ? "" : profileSummary.getProviderType(),
                profileSummary == null ? "" : profileSummary.getModelName(),
                profileSummary == null ? null : profileSummary.getExpectedDimensions(),
                configSource,
                rebuildRecommended,
                rebuildReason,
                record.getCreatedBy(),
                record.getUpdatedBy(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }

    /**
     * 判断是否建议执行一次向量索引重建。
     *
     * @param beforeState 变更前状态
     * @param savedRecord 保存后的记录
     * @return 是否建议重建
     */
    private boolean shouldRecommendRebuild(QueryVectorConfigState beforeState, QueryVectorConfigRecord savedRecord) {
        if (!savedRecord.isVectorEnabled()) {
            return false;
        }
        if (!beforeState.isVectorEnabled()) {
            return true;
        }
        return !Objects.equals(beforeState.getEmbeddingModelProfileId(), savedRecord.getEmbeddingModelProfileId());
    }

    /**
     * 构造建议重建的提示信息。
     *
     * @param beforeState 变更前状态
     * @param savedRecord 保存后的记录
     * @param rebuildRecommended 是否建议重建
     * @return 建议原因
     */
    private String buildRebuildReason(
            QueryVectorConfigState beforeState,
            QueryVectorConfigRecord savedRecord,
            boolean rebuildRecommended
    ) {
        if (!rebuildRecommended) {
            return "";
        }
        if (!beforeState.isVectorEnabled() && savedRecord.isVectorEnabled()) {
            return "已启用向量检索，建议执行一次“重建向量索引”为历史文章补齐 article_vector_index。";
        }
        return "embedding profile 已变更，建议执行一次“重建向量索引”以刷新现有 article_vector_index。";
    }

    /**
     * 判断是否需要清空查询缓存。
     *
     * @param beforeState 变更前状态
     * @param savedRecord 保存后的记录
     * @return 是否需要清空
     */
    private boolean shouldEvictQueryCache(QueryVectorConfigState beforeState, QueryVectorConfigRecord savedRecord) {
        if (beforeState.isVectorEnabled() != savedRecord.isVectorEnabled()) {
            return true;
        }
        return !Objects.equals(beforeState.getEmbeddingModelProfileId(), savedRecord.getEmbeddingModelProfileId());
    }

    /**
     * 规范化操作人。
     *
     * @param operator 操作人
     * @return 规范化后的操作人
     */
    private String resolveOperator(String operator) {
        if (!StringUtils.hasText(operator)) {
            return "admin";
        }
        return operator.trim();
    }

    /**
     * 校验 embedding profile 是否存在且可用于向量场景。
     *
     * @param embeddingModelProfileId 模型配置主键
     */
    private void validateEmbeddingProfile(Long embeddingModelProfileId) {
        if (embeddingModelProfileId == null) {
            throw new IllegalArgumentException("embeddingModelProfileId不能为空");
        }
        Optional<LlmModelProfile> modelProfile = llmModelProfileJdbcRepository.findEnabledById(embeddingModelProfileId);
        if (modelProfile.isEmpty()) {
            throw new IllegalArgumentException("embeddingModelProfileId不存在或未启用");
        }
        if (!LlmModelProfile.MODEL_KIND_EMBEDDING.equalsIgnoreCase(modelProfile.orElseThrow().getModelKind())) {
            throw new IllegalArgumentException("embeddingModelProfileId必须引用 EMBEDDING 模型");
        }
        Optional<LlmProviderConnection> providerConnection = llmProviderConnectionJdbcRepository.findEnabledById(
                modelProfile.orElseThrow().getConnectionId()
        );
        if (providerConnection.isEmpty()) {
            throw new IllegalArgumentException("embeddingModelProfileId关联的 provider connection 不存在或未启用");
        }
    }

    /**
     * 加载 embedding profile 摘要。
     *
     * @param embeddingModelProfileId 模型配置主键
     * @return profile 摘要
     */
    public Optional<EmbeddingProfileSummary> loadProfileSummary(Long embeddingModelProfileId) {
        if (embeddingModelProfileId == null) {
            return Optional.empty();
        }
        Optional<LlmModelProfile> modelProfile = llmModelProfileJdbcRepository.findById(embeddingModelProfileId);
        if (modelProfile.isEmpty()) {
            return Optional.empty();
        }
        Optional<LlmProviderConnection> providerConnection = llmProviderConnectionJdbcRepository.findById(
                modelProfile.orElseThrow().getConnectionId()
        );
        String providerType = providerConnection.map(LlmProviderConnection::getProviderType).orElse("");
        return Optional.of(new EmbeddingProfileSummary(
                modelProfile.orElseThrow().getId(),
                providerType,
                modelProfile.orElseThrow().getModelName(),
                modelProfile.orElseThrow().getExpectedDimensions()
        ));
    }
}
