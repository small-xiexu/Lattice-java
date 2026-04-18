package com.xbk.lattice.query.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.llm.domain.LlmModelProfile;
import com.xbk.lattice.llm.domain.LlmProviderConnection;
import com.xbk.lattice.llm.infra.LlmModelProfileJdbcRepository;
import com.xbk.lattice.llm.infra.LlmProviderConnectionJdbcRepository;
import com.xbk.lattice.llm.service.LlmSecretCryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Embedding 路由解析测试
 *
 * 职责：验证 embedding profile 能正确解析到 provider 路由
 *
 * @author xiexu
 */
class EmbeddingRouteResolverTest {

    /**
     * 验证可用的 embedding profile 会被正确解析。
     */
    @Test
    void shouldResolveEnabledEmbeddingProfile() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setSecretEncryptionKey("test-phase8-key-0123456789abcdef");
        LlmSecretCryptoService cryptoService = new LlmSecretCryptoService(llmProperties);
        EmbeddingRouteResolver resolver = new EmbeddingRouteResolver(
                new StubModelProfileRepository(new LlmModelProfile(
                        1L,
                        "bge-m3",
                        2L,
                        "BAAI/bge-m3",
                        LlmModelProfile.MODEL_KIND_EMBEDDING,
                        1024,
                        false,
                        null,
                        null,
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "{}",
                        true,
                        null,
                        "tester",
                        "tester",
                        null,
                        null
                )),
                new StubProviderConnectionRepository(new LlmProviderConnection(
                        2L,
                        "siliconflow",
                        "openai_compatible",
                        "https://api.siliconflow.cn/v1",
                        cryptoService.encrypt("sk-test"),
                        "sk-****",
                        true,
                        null,
                        "tester",
                        "tester",
                        null,
                        null
                )),
                cryptoService
        );

        EmbeddingRouteResolution resolution = resolver.resolve(1L);

        assertThat(resolution.getProfileId()).isEqualTo(1L);
        assertThat(resolution.getProviderType()).isEqualTo("openai_compatible");
        assertThat(resolution.getModelName()).isEqualTo("BAAI/bge-m3");
        assertThat(resolution.getExpectedDimensions()).isEqualTo(1024);
        assertThat(resolution.getApiKey()).isEqualTo("sk-test");
    }

    /**
     * 验证非 EMBEDDING 模型会直接报错。
     */
    @Test
    void shouldRejectNonEmbeddingProfile() {
        EmbeddingRouteResolver resolver = new EmbeddingRouteResolver(
                new StubModelProfileRepository(new LlmModelProfile(
                        1L,
                        "gpt54",
                        2L,
                        "gpt-5.4",
                        LlmModelProfile.MODEL_KIND_CHAT,
                        null,
                        false,
                        null,
                        null,
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "{}",
                        true,
                        null,
                        "tester",
                        "tester",
                        null,
                        null
                )),
                new StubProviderConnectionRepository(null),
                new LlmSecretCryptoService(new LlmProperties())
        );

        assertThatThrownBy(() -> resolver.resolve(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMBEDDING");
    }

    private static class StubModelProfileRepository extends LlmModelProfileJdbcRepository {

        private final LlmModelProfile modelProfile;

        private StubModelProfileRepository(LlmModelProfile modelProfile) {
            super(new JdbcTemplate());
            this.modelProfile = modelProfile;
        }

        @Override
        public Optional<LlmModelProfile> findEnabledById(Long id) {
            return Optional.ofNullable(modelProfile);
        }
    }

    private static class StubProviderConnectionRepository extends LlmProviderConnectionJdbcRepository {

        private final LlmProviderConnection connection;

        private StubProviderConnectionRepository(LlmProviderConnection connection) {
            super(new JdbcTemplate());
            this.connection = connection;
        }

        @Override
        public Optional<LlmProviderConnection> findEnabledById(Long id) {
            return Optional.ofNullable(connection);
        }
    }
}
