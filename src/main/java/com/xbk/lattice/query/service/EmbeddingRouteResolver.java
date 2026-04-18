package com.xbk.lattice.query.service;

import com.xbk.lattice.llm.domain.LlmModelProfile;
import com.xbk.lattice.llm.domain.LlmProviderConnection;
import com.xbk.lattice.llm.infra.LlmModelProfileJdbcRepository;
import com.xbk.lattice.llm.infra.LlmProviderConnectionJdbcRepository;
import com.xbk.lattice.llm.service.LlmSecretCryptoService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Embedding 路由解析服务
 *
 * 职责：把 embedding profile 主键解析为真实 provider 连接与模型路由
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class EmbeddingRouteResolver {

    private final LlmModelProfileJdbcRepository llmModelProfileJdbcRepository;

    private final LlmProviderConnectionJdbcRepository llmProviderConnectionJdbcRepository;

    private final LlmSecretCryptoService llmSecretCryptoService;

    /**
     * 创建 Embedding 路由解析服务。
     *
     * @param llmModelProfileJdbcRepository 模型配置仓储
     * @param llmProviderConnectionJdbcRepository provider 连接仓储
     * @param llmSecretCryptoService 密钥解密服务
     */
    public EmbeddingRouteResolver(
            LlmModelProfileJdbcRepository llmModelProfileJdbcRepository,
            LlmProviderConnectionJdbcRepository llmProviderConnectionJdbcRepository,
            LlmSecretCryptoService llmSecretCryptoService
    ) {
        this.llmModelProfileJdbcRepository = llmModelProfileJdbcRepository;
        this.llmProviderConnectionJdbcRepository = llmProviderConnectionJdbcRepository;
        this.llmSecretCryptoService = llmSecretCryptoService;
    }

    /**
     * 解析指定 embedding profile。
     *
     * @param embeddingModelProfileId embedding profile 主键
     * @return 路由解析结果
     */
    public EmbeddingRouteResolution resolve(Long embeddingModelProfileId) {
        if (embeddingModelProfileId == null) {
            throw new IllegalArgumentException("embeddingModelProfileId不能为空");
        }
        Optional<LlmModelProfile> modelProfile = llmModelProfileJdbcRepository.findEnabledById(embeddingModelProfileId);
        if (modelProfile.isEmpty()) {
            throw new IllegalArgumentException("embedding profile不存在或未启用: " + embeddingModelProfileId);
        }
        if (!LlmModelProfile.MODEL_KIND_EMBEDDING.equalsIgnoreCase(modelProfile.orElseThrow().getModelKind())) {
            throw new IllegalStateException("embedding profile类型不是EMBEDDING: " + embeddingModelProfileId);
        }
        Optional<LlmProviderConnection> providerConnection = llmProviderConnectionJdbcRepository.findEnabledById(
                modelProfile.orElseThrow().getConnectionId()
        );
        if (providerConnection.isEmpty()) {
            throw new IllegalStateException("embedding provider connection不存在或未启用: "
                    + modelProfile.orElseThrow().getConnectionId());
        }
        return new EmbeddingRouteResolution(
                modelProfile.orElseThrow().getId(),
                providerConnection.orElseThrow().getProviderType(),
                providerConnection.orElseThrow().getBaseUrl(),
                llmSecretCryptoService.decrypt(providerConnection.orElseThrow().getApiKeyCiphertext()),
                modelProfile.orElseThrow().getModelName(),
                modelProfile.orElseThrow().getExpectedDimensions()
        );
    }
}
