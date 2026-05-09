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
}
