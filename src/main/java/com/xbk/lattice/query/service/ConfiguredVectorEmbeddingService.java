package com.xbk.lattice.query.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 可配置向量 embedding 服务
 *
 * 职责：按当前 query 向量配置构造 embedding 请求，并把模型名与维度真正下发到运行时
 *
 * @author xiexu
 */
@Slf4j
@Service
public class ConfiguredVectorEmbeddingService {

    private final QuerySearchProperties querySearchProperties;

    private final EmbeddingRouteResolver embeddingRouteResolver;

    private final EmbeddingClientFactory embeddingClientFactory;

    private final EmbeddingModel legacyEmbeddingModel;

    /**
     * 创建可配置向量 embedding 服务。
     *
     * @param querySearchProperties 查询检索配置
     * @param embeddingRouteResolver embedding 路由解析器
     * @param embeddingClientFactory embedding 客户端工厂
     */
    @Autowired
    public ConfiguredVectorEmbeddingService(
            QuerySearchProperties querySearchProperties,
            EmbeddingRouteResolver embeddingRouteResolver,
            EmbeddingClientFactory embeddingClientFactory,
            ObjectProvider<EmbeddingModel> embeddingModelProvider
    ) {
        this(
                querySearchProperties,
                embeddingRouteResolver,
                embeddingClientFactory,
                embeddingModelProvider.getIfAvailable()
        );
    }

    /**
     * 创建可配置向量 embedding 服务。
     *
     * @param querySearchProperties 查询检索配置
     * @param embeddingModel embedding 模型
     */
    public ConfiguredVectorEmbeddingService(
            QuerySearchProperties querySearchProperties,
            EmbeddingModel embeddingModel
    ) {
        this(querySearchProperties, null, null, embeddingModel);
    }

    /**
     * 创建可配置向量 embedding 服务。
     *
     * @param querySearchProperties 查询检索配置
     * @param embeddingRouteResolver embedding 路由解析器
     * @param embeddingClientFactory embedding 客户端工厂
     * @param embeddingModel legacy embedding 模型
     */
    ConfiguredVectorEmbeddingService(
            QuerySearchProperties querySearchProperties,
            EmbeddingRouteResolver embeddingRouteResolver,
            EmbeddingClientFactory embeddingClientFactory,
            EmbeddingModel embeddingModel
    ) {
        this.querySearchProperties = querySearchProperties;
        this.embeddingRouteResolver = embeddingRouteResolver;
        this.embeddingClientFactory = embeddingClientFactory;
        this.legacyEmbeddingModel = embeddingModel;
    }

    /**
     * 返回当前 embedding 能力是否可用。
     *
     * @return 是否可用
     */
    public boolean isAvailable() {
        return legacyEmbeddingModel != null || querySearchProperties.getVector().getEmbeddingModelProfileId() != null;
    }

    /**
     * 返回当前配置的 embedding 模型名称。
     *
     * @return 模型名称
     */
    public String getConfiguredModelName() {
        Long profileId = querySearchProperties.getVector().getEmbeddingModelProfileId();
        if (profileId != null && embeddingRouteResolver != null) {
            return embeddingRouteResolver.resolve(profileId).getModelName();
        }
        if (legacyEmbeddingModel == null) {
            return "";
        }
        String configuredModelName = querySearchProperties.getVector().getEmbeddingModel();
        if (configuredModelName == null) {
            return "";
        }
        return configuredModelName.trim();
    }

    /**
     * 返回当前配置的期望维度。
     *
     * @return 期望维度
     */
    public int getConfiguredExpectedDimensions() {
        Long profileId = querySearchProperties.getVector().getEmbeddingModelProfileId();
        if (profileId != null && embeddingRouteResolver != null) {
            Integer expectedDimensions = embeddingRouteResolver.resolve(profileId).getExpectedDimensions();
            return expectedDimensions == null ? 0 : expectedDimensions.intValue();
        }
        if (legacyEmbeddingModel == null) {
            return 0;
        }
        return querySearchProperties.getVector().getExpectedDimensions();
    }

    /**
     * 生成文本 embedding。
     *
     * @param text 原始文本
     * @return embedding 向量
     */
    public float[] embed(String text) {
        long startedAt = System.currentTimeMillis();
        Long profileId = querySearchProperties.getVector().getEmbeddingModelProfileId();
        if (profileId != null) {
            if (embeddingRouteResolver == null || embeddingClientFactory == null) {
                throw new IllegalStateException("Embedding profile route dependencies are not configured");
            }
            EmbeddingRouteResolution routeResolution = embeddingRouteResolver.resolve(profileId);
            EmbeddingModel embeddingModel = embeddingClientFactory.getOrCreate(routeResolution);
            EmbeddingRequest request = buildEmbeddingRequest(text, routeResolution);
            EmbeddingResponse response = embeddingModel.call(request);
            if (response == null || response.getResult() == null) {
                log.info(
                        "[VECTOR][EMBED] profileId={}, modelName={}, providerType={}, dimensions={}, latencyMs={}, success=false",
                        routeResolution.getProfileId(),
                        routeResolution.getModelName(),
                        routeResolution.getProviderType(),
                        routeResolution.getExpectedDimensions(),
                        System.currentTimeMillis() - startedAt
                );
                return null;
            }
            log.info(
                    "[VECTOR][EMBED] profileId={}, modelName={}, providerType={}, dimensions={}, latencyMs={}, success=true",
                    routeResolution.getProfileId(),
                    routeResolution.getModelName(),
                    routeResolution.getProviderType(),
                    routeResolution.getExpectedDimensions(),
                    System.currentTimeMillis() - startedAt
            );
            return response.getResult().getOutput();
        }
        if (legacyEmbeddingModel == null) {
            throw new IllegalStateException("Embedding profile is not configured");
        }
        return executeLegacyEmbedding(text, startedAt);
    }

    /**
     * 返回当前配置的 profile 主键。
     *
     * @return profile 主键
     */
    public Long getConfiguredProfileId() {
        return querySearchProperties.getVector().getEmbeddingModelProfileId();
    }

    private float[] executeLegacyEmbedding(String text, long startedAt) {
        EmbeddingRequest request = new EmbeddingRequest(List.of(text), buildLegacyEmbeddingOptions());
        EmbeddingResponse response = legacyEmbeddingModel.call(request);
        if (response == null || response.getResult() == null) {
            log.info(
                    "[VECTOR][EMBED] profileId={}, modelName={}, providerType={}, dimensions={}, latencyMs={}, success=false",
                    null,
                    getConfiguredModelName(),
                    "legacy",
                    getConfiguredExpectedDimensions(),
                    System.currentTimeMillis() - startedAt
            );
            return null;
        }
        log.info(
                "[VECTOR][EMBED] profileId={}, modelName={}, providerType={}, dimensions={}, latencyMs={}, success=true",
                null,
                getConfiguredModelName(),
                "legacy",
                getConfiguredExpectedDimensions(),
                System.currentTimeMillis() - startedAt
        );
        return response.getResult().getOutput();
    }

    private EmbeddingRequest buildEmbeddingRequest(String text, EmbeddingRouteResolution routeResolution) {
        String providerType = normalizeProviderType(routeResolution.getProviderType());
        if ("ollama".equals(providerType)) {
            OllamaEmbeddingOptions options = OllamaEmbeddingOptions.builder()
                    .model(routeResolution.getModelName())
                    .build();
            return new EmbeddingRequest(List.of(text), options);
        }
        OpenAiEmbeddingOptions.Builder builder = OpenAiEmbeddingOptions.builder();
        builder.model(routeResolution.getModelName());
        if (routeResolution.getExpectedDimensions() != null && routeResolution.getExpectedDimensions().intValue() > 0) {
            builder.dimensions(routeResolution.getExpectedDimensions());
        }
        return new EmbeddingRequest(List.of(text), builder.build());
    }

    private OpenAiEmbeddingOptions buildLegacyEmbeddingOptions() {
        OpenAiEmbeddingOptions.Builder builder = OpenAiEmbeddingOptions.builder();
        String configuredModelName = querySearchProperties.getVector().getEmbeddingModel();
        if (configuredModelName != null && !configuredModelName.trim().isBlank()) {
            builder.model(configuredModelName.trim());
        }
        int expectedDimensions = querySearchProperties.getVector().getExpectedDimensions();
        if (expectedDimensions > 0) {
            builder.dimensions(expectedDimensions);
        }
        return builder.build();
    }

    private String normalizeProviderType(String providerType) {
        if (providerType == null || providerType.isBlank()) {
            return "openai";
        }
        return providerType.trim().toLowerCase(Locale.ROOT);
    }
}
