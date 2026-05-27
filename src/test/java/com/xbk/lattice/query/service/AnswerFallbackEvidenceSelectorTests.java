package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerFallbackEvidenceSelector 测试
 *
 * 职责：验证 deterministic fallback 证据选择、排序与补充逻辑
 *
 * @author xiexu
 */
class AnswerFallbackEvidenceSelectorTests {

    private final AnswerGenerationService support = new AnswerGenerationService();

    private final AnswerFallbackEvidenceSelector selector = new AnswerFallbackEvidenceSelector(support);

    /**
     * 验证空输入不会产生 fallback 证据。
     */
    @Test
    void shouldReturnEmptyHitsWhenInputMissing() {
        List<QueryArticleHit> nullHits = selector.selectFallbackEvidenceHits("timeout 配置是多少", null);
        List<QueryArticleHit> emptyHits = selector.selectFallbackEvidenceHits("timeout 配置是多少", List.of());

        assertThat(nullHits).isEmpty();
        assertThat(emptyHits).isEmpty();
    }

    /**
     * 验证精确查值题会优先保留更直接的 source 证据。
     */
    @Test
    void shouldPreferDirectSourceEvidenceForExactLookupQuestion() {
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "timeout-summary",
                "Timeout Summary",
                "summary: timeout 配置存在，详见源文件。",
                "{\"description\":\"timeout 配置摘要\"}",
                List.of("docs/timeout.md"),
                3.0D
        );
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "docs/timeout.md#0",
                "docs/timeout.md",
                "timeout_seconds = 30\nretry_count = 3",
                "{\"filePath\":\"docs/timeout.md\"}",
                List.of("docs/timeout.md"),
                2.0D
        );

        List<QueryArticleHit> selectedHits = selector.selectFallbackEvidenceHits(
                "timeout_seconds 配置是多少",
                List.of(articleHit, sourceHit)
        );

        assertThat(selectedHits).isNotEmpty();
        assertThat(selectedHits.get(0).getEvidenceType()).isEqualTo(QueryEvidenceType.SOURCE);
        assertThat(selectedHits).extracting(QueryArticleHit::getEvidenceType).contains(QueryEvidenceType.SOURCE);
    }

    /**
     * 验证显式 path 契约题会补充包含契约信号的原文证据。
     */
    @Test
    void shouldEnrichPathContractCompanionEvidence() {
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "order-api-summary",
                "Order API Summary",
                "接口 /api/orders 目前用于订单查询。",
                "{\"description\":\"订单接口摘要\"}",
                List.of("docs/order-api.md"),
                3.0D
        );
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "docs/order-api.md#2",
                "docs/order-api.md",
                "path /api/orders 保持兼容，endpoint 不变。",
                "{\"filePath\":\"docs/order-api.md\"}",
                List.of("docs/order-api.md"),
                2.0D
        );

        List<QueryArticleHit> selectedHits = selector.selectFallbackEvidenceHits(
                "/api/orders 的 path 契约是否保持兼容？",
                List.of(articleHit, sourceHit)
        );

        assertThat(selectedHits).extracting(QueryArticleHit::getEvidenceType).contains(QueryEvidenceType.SOURCE);
        assertThat(selectedHits)
                .anySatisfy(selectedHit -> assertThat(selectedHit.getContent()).contains("保持兼容"));
    }

    /**
     * 验证互补证据已选中原文和摘要时，仍会补入高分且贴题的结构化路径 fact card。
     */
    @Test
    void shouldIncludeQuestionFocusedStructuredFactCardWithSourceAndArticleComplement() {
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "examples/service-config.yaml#0",
                "service-config.yaml",
                """
                        image: registry.example/system-agent:2.4.0
                        artifact: example.org/release-bundle:2.4.0
                        """,
                "{\"filePath\":\"examples/service-config.yaml\"}",
                List.of("examples/service-config.yaml"),
                3.0D
        );
        QueryArticleHit factCardHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "fact-card:service-config:primary-limit",
                "Service Config Path Values",
                """
                        fieldPath: spec.primaryLimit.settings.value = 9091
                        fieldPath: spec.secondaryWindow.settings.value = 1001
                        """,
                "{\"cardType\":\"FACT_ENUM\",\"answerShape\":\"ENUM\"}",
                List.of("examples/service-config.yaml"),
                2.0D
        );
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "service-config-summary",
                "Service Config Summary",
                "service-config.yaml primaryLimit value primaryLimit value primaryLimit value = 9091",
                "{\"description\":\"service-config primaryLimit value summary\"}",
                List.of("examples/service-config.yaml"),
                2.5D
        );

        List<QueryArticleHit> selectedHits = selector.selectFallbackEvidenceHits(
                "service-config.yaml primaryLimit value 是多少？",
                List.of(sourceHit, factCardHit, articleHit)
        );

        assertThat(selectedHits).extracting(QueryArticleHit::getEvidenceType)
                .contains(QueryEvidenceType.SOURCE, QueryEvidenceType.ARTICLE, QueryEvidenceType.FACT_CARD);
        assertThat(selectedHits)
                .anySatisfy(selectedHit -> assertThat(selectedHit.getContent())
                        .contains("spec.primaryLimit.settings.value = 9091"));
    }

    /**
     * 验证不覆盖问题焦点的结构化 fact card 不会被互补证据逻辑强行加入。
     */
    @Test
    void shouldNotIncludeUnfocusedStructuredFactCardWithSourceAndArticleComplement() {
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "examples/service-config.yaml#0",
                "service-config.yaml",
                "image: registry.example/system-agent:2.4.0",
                "{\"filePath\":\"examples/service-config.yaml\"}",
                List.of("examples/service-config.yaml"),
                3.0D
        );
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "service-config-summary",
                "Service Config Summary",
                "service-config.yaml primaryLimit value primaryLimit value = 9091",
                "{\"description\":\"service-config primaryLimit value summary\"}",
                List.of("examples/service-config.yaml"),
                2.5D
        );
        QueryArticleHit unrelatedFactCardHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "fact-card:service-config:secondary-window",
                "Secondary Window Values",
                "fieldPath: spec.secondaryWindow.settings.value = 1001",
                "{\"cardType\":\"FACT_ENUM\",\"answerShape\":\"ENUM\"}",
                List.of("examples/service-config.yaml"),
                2.0D
        );

        List<QueryArticleHit> selectedHits = selector.selectFallbackEvidenceHits(
                "service-config.yaml primaryLimit value 是多少？",
                List.of(sourceHit, unrelatedFactCardHit, articleHit)
        );

        assertThat(selectedHits).extracting(QueryArticleHit::getEvidenceType)
                .contains(QueryEvidenceType.SOURCE, QueryEvidenceType.ARTICLE);
        assertThat(selectedHits).extracting(QueryArticleHit::getEvidenceType)
                .doesNotContain(QueryEvidenceType.FACT_CARD);
    }

    /**
     * 验证 endpoint、image/version 等问题不会把无关结构化路径 fact card 错误提升。
     */
    @Test
    void shouldNotPromoteUnrelatedStructuredFactCardForEndpointOrMachineIdentifierQuestions() {
        QueryArticleHit endpointSourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "examples/order-api.md#0",
                "order-api.md",
                "endpoint: https://api.example.com/orders",
                "{\"filePath\":\"examples/order-api.md\"}",
                List.of("examples/order-api.md"),
                3.0D
        );
        QueryArticleHit endpointArticleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "order-api-summary",
                "Order API Summary",
                "Order API endpoint endpoint endpoint is https://api.example.com/orders",
                "{\"description\":\"Order API endpoint summary\"}",
                List.of("examples/order-api.md"),
                2.5D
        );
        QueryArticleHit endpointUnrelatedFactCardHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "fact-card:runtime:limit",
                "Runtime Limit Values",
                "fieldPath: spec.runtimeLimit.settings.value = 5",
                "{\"cardType\":\"FACT_ENUM\",\"answerShape\":\"ENUM\"}",
                List.of("examples/runtime-config.yaml"),
                2.0D
        );

        List<QueryArticleHit> endpointHits = selector.selectFallbackEvidenceHits(
                "Order API endpoint 是什么？",
                List.of(endpointSourceHit, endpointUnrelatedFactCardHit, endpointArticleHit)
        );

        assertThat(endpointHits).extracting(QueryArticleHit::getEvidenceType)
                .doesNotContain(QueryEvidenceType.FACT_CARD);
        assertThat(endpointHits)
                .anySatisfy(selectedHit -> assertThat(selectedHit.getContent())
                        .contains("https://api.example.com/orders"));

        QueryArticleHit imageSourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "examples/runtime-manifest.yaml#0",
                "runtime-manifest.yaml",
                "image: registry.example/runtime-worker:2.7.1",
                "{\"filePath\":\"examples/runtime-manifest.yaml\"}",
                List.of("examples/runtime-manifest.yaml"),
                3.0D
        );
        QueryArticleHit imageArticleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "runtime-worker-summary",
                "Runtime Worker Summary",
                "runtime-worker image version image version is registry.example/runtime-worker:2.7.1",
                "{\"description\":\"runtime-worker image version summary\"}",
                List.of("examples/runtime-manifest.yaml"),
                2.5D
        );
        QueryArticleHit imageUnrelatedFactCardHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "fact-card:runtime:threshold",
                "Runtime Threshold Values",
                "fieldPath: spec.runtimeThreshold.settings.value = 7",
                "{\"cardType\":\"FACT_ENUM\",\"answerShape\":\"ENUM\"}",
                List.of("examples/runtime-manifest.yaml"),
                2.0D
        );

        List<QueryArticleHit> imageHits = selector.selectFallbackEvidenceHits(
                "runtime-worker image version 是什么？",
                List.of(imageSourceHit, imageUnrelatedFactCardHit, imageArticleHit)
        );

        assertThat(imageHits).extracting(QueryArticleHit::getEvidenceType)
                .doesNotContain(QueryEvidenceType.FACT_CARD);
        assertThat(imageHits)
                .anySatisfy(selectedHit -> assertThat(selectedHit.getContent())
                        .contains("registry.example/runtime-worker:2.7.1"));
    }
}
