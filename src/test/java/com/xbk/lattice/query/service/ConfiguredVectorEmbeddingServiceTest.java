package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 可配置 embedding 服务测试
 *
 * 职责：验证 legacy fallback 路径会把模型名和维度真正下发到请求
 *
 * @author xiexu
 */
class ConfiguredVectorEmbeddingServiceTest {

    /**
     * 验证 legacy fallback 会把模型名和维度传给 embedding 请求。
     */
    @Test
    void shouldPassConfiguredModelAndDimensionsIntoLegacyEmbeddingRequest() {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getVector().setEmbeddingModel("text-embedding-3-large");
        querySearchProperties.getVector().setExpectedDimensions(3072);
        FixedEmbeddingModel fixedEmbeddingModel = new FixedEmbeddingModel();
        ConfiguredVectorEmbeddingService embeddingService = new ConfiguredVectorEmbeddingService(
                querySearchProperties,
                fixedEmbeddingModel
        );

        float[] embedding = embeddingService.embed("test-input");

        assertThat(embedding).hasSize(3);
        assertThat(fixedEmbeddingModel.getLastRequestedModel()).isEqualTo("text-embedding-3-large");
        assertThat(fixedEmbeddingModel.getLastRequestedDimensions()).isEqualTo(3072);
    }

    /**
     * 验证配置了 profile 后，会优先走 profile 路由而不是 legacy Bean。
     */
    @Test
    void shouldPreferProfileRouteOverLegacyEmbeddingModelWhenProfileConfigured() {
        QuerySearchProperties querySearchProperties = new QuerySearchProperties();
        querySearchProperties.getVector().setEmbeddingModelProfileId(Long.valueOf(9L));
        querySearchProperties.getVector().setEmbeddingModel("legacy-openai-model");
        querySearchProperties.getVector().setExpectedDimensions(1536);
        FixedEmbeddingModel legacyEmbeddingModel = new FixedEmbeddingModel();
        FixedEmbeddingModel profileEmbeddingModel = new FixedEmbeddingModel();
        EmbeddingRouteResolver embeddingRouteResolver = new EmbeddingRouteResolver(null, null, null) {
            @Override
            public EmbeddingRouteResolution resolve(Long embeddingModelProfileId) {
                return new EmbeddingRouteResolution(
                        embeddingModelProfileId,
                        "openai_compatible",
                        "https://api.siliconflow.cn",
                        "test-key",
                        "BAAI/bge-m3",
                        Integer.valueOf(1024),
                        Integer.valueOf(7)
                );
            }
        };
        EmbeddingClientFactory embeddingClientFactory = new EmbeddingClientFactory(null, null) {
            @Override
            public EmbeddingModel getOrCreate(EmbeddingRouteResolution routeResolution) {
                return profileEmbeddingModel;
            }
        };
        ConfiguredVectorEmbeddingService embeddingService = new ConfiguredVectorEmbeddingService(
                querySearchProperties,
                embeddingRouteResolver,
                embeddingClientFactory,
                legacyEmbeddingModel
        );

        float[] embedding = embeddingService.embed("test-input");

        assertThat(embedding).hasSize(3);
        assertThat(profileEmbeddingModel.getLastRequestedModel()).isEqualTo("BAAI/bge-m3");
        assertThat(profileEmbeddingModel.getLastRequestedDimensions()).isEqualTo(1024);
        assertThat(legacyEmbeddingModel.getLastRequestedModel()).isNull();
        assertThat(embeddingService.getConfiguredModelName()).isEqualTo("BAAI/bge-m3");
        assertThat(embeddingService.getConfiguredExpectedDimensions()).isEqualTo(1024);
    }

    private static class FixedEmbeddingModel implements EmbeddingModel {

        private String lastRequestedModel;

        private Integer lastRequestedDimensions;

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            if (request.getOptions() instanceof OpenAiEmbeddingOptions) {
                OpenAiEmbeddingOptions options = (OpenAiEmbeddingOptions) request.getOptions();
                this.lastRequestedModel = options.getModel();
                this.lastRequestedDimensions = options.getDimensions();
            }
            return new EmbeddingResponse(List.of(new Embedding(new float[]{0.1F, 0.2F, 0.3F}, 0)));
        }

        @Override
        public float[] embed(Document document) {
            return new float[]{0.1F, 0.2F, 0.3F};
        }

        private String getLastRequestedModel() {
            return lastRequestedModel;
        }

        private Integer getLastRequestedDimensions() {
            return lastRequestedDimensions;
        }
    }
}
