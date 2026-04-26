package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryArticleResponse;
import com.xbk.lattice.api.query.QuerySourceResponse;
import com.xbk.lattice.query.evidence.domain.AnswerProjection;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import com.xbk.lattice.query.evidence.domain.ProjectionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryResponseCitationAssembler 测试
 *
 * 职责：验证 simple query 最终 sources/articles 的 projection 组装去重口径
 *
 * @author xiexu
 */
class QueryResponseCitationAssemblerTests {

    /**
     * 验证同一 article 同时出现 ARTICLE 与 SOURCE_FILE projection 时，只保留 article 级来源。
     */
    @Test
    void shouldPreferArticleSourceWhenArticleAndSourceFileProjectSameHit() {
        QueryArticleHit articleHit = new QueryArticleHit(
                11L,
                "payment-timeout-article",
                "payment-timeout",
                "Payment Timeout",
                "payment.timeout.retry=5",
                "{\"description\":\"payment timeout retry config\"}",
                List.of("payments/gateway-config.yaml"),
                10.0D
        );
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                "结论：`payment.timeout.retry` 默认值为 `5`。[[payment-timeout-article]][→ payments/gateway-config.yaml]",
                List.of(
                        new AnswerProjection(
                                1,
                                "query-top-k:ARTICLE:payment-timeout-article",
                                ProjectionCitationFormat.ARTICLE,
                                "[[payment-timeout-article]]",
                                "payment-timeout-article",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        ),
                        new AnswerProjection(
                                2,
                                "query-top-k:SOURCE_FILE:payments/gateway-config.yaml",
                                ProjectionCitationFormat.SOURCE_FILE,
                                "[→ payments/gateway-config.yaml]",
                                "payments/gateway-config.yaml",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        )
                )
        );

        List<QuerySourceResponse> sourceResponses = QueryResponseCitationAssembler.toSourceResponses(
                answerProjectionBundle,
                List.of(articleHit),
                true
        );
        List<QueryArticleResponse> articleResponses = QueryResponseCitationAssembler.toArticleResponses(
                answerProjectionBundle,
                List.of(articleHit),
                true
        );

        assertThat(sourceResponses).hasSize(1);
        assertThat(sourceResponses.get(0).getArticleKey()).isEqualTo("payment-timeout-article");
        assertThat(sourceResponses.get(0).getSourcePaths()).containsExactly("payments/gateway-config.yaml");
        assertThat(sourceResponses.get(0).getDerivation()).isEqualTo("PROJECTION");
        assertThat(articleResponses).hasSize(1);
        assertThat(articleResponses.get(0).getArticleKey()).isEqualTo("payment-timeout-article");
        assertThat(articleResponses.get(0).getDerivation()).isEqualTo("PROJECTION");
    }

    /**
     * 验证 ARTICLE projection 命中 conceptId、SOURCE_FILE projection 命中 articleKey 时，也不会重复返回 articles/sources。
     */
    @Test
    void shouldDeduplicateProjectionResponsesAcrossConceptIdAndArticleKeyIdentities() {
        QueryArticleHit articleHit = new QueryArticleHit(
                21L,
                "legacy-default--readme",
                "readme",
                "Readme",
                "payment.timeout.retry=5",
                "{\"description\":\"payment timeout retry config\"}",
                List.of("README.md"),
                10.0D
        );
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                null,
                null,
                "readme",
                "Readme",
                "payment.timeout.retry=5",
                "{\"filePath\":\"README.md\"}",
                List.of("README.md"),
                9.0D
        );
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                "结论：`payment.timeout.retry` 默认值为 `5`。[[readme]][→ README.md]",
                List.of(
                        new AnswerProjection(
                                1,
                                "query-top-k:ARTICLE:readme",
                                ProjectionCitationFormat.ARTICLE,
                                "[[readme]]",
                                "readme",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        ),
                        new AnswerProjection(
                                2,
                                "query-top-k:SOURCE_FILE:README.md",
                                ProjectionCitationFormat.SOURCE_FILE,
                                "[→ README.md]",
                                "README.md",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        )
                )
        );

        List<QuerySourceResponse> sourceResponses = QueryResponseCitationAssembler.toSourceResponses(
                answerProjectionBundle,
                List.of(sourceHit, articleHit),
                true
        );
        List<QueryArticleResponse> articleResponses = QueryResponseCitationAssembler.toArticleResponses(
                answerProjectionBundle,
                List.of(sourceHit, articleHit),
                true
        );

        assertThat(sourceResponses).hasSize(1);
        assertThat(sourceResponses.get(0).getArticleKey()).isEqualTo("legacy-default--readme");
        assertThat(sourceResponses.get(0).getConceptId()).isEqualTo("readme");
        assertThat(sourceResponses.get(0).getSourcePaths()).containsExactly("README.md");
        assertThat(articleResponses).hasSize(1);
        assertThat(articleResponses.get(0).getArticleKey()).isEqualTo("legacy-default--readme");
        assertThat(articleResponses.get(0).getConceptId()).isEqualTo("readme");
    }

    /**
     * 验证当 SOURCE 命中先占据 conceptId、ARTICLE 命中后补全 articleKey/路径时，最终仍只返回统一 article 身份。
     */
    @Test
    void shouldPreferArticleMetadataWhenSourceHitOnlyProvidesConceptIdentity() {
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                null,
                null,
                "readme",
                "Readme",
                "payment.timeout.retry=5",
                "{\"filePath\":\"README.md\"}",
                List.of(),
                9.5D
        );
        QueryArticleHit articleHit = new QueryArticleHit(
                22L,
                "legacy-default--readme",
                "readme",
                "Readme",
                "payment.timeout.retry=5",
                "{\"description\":\"payment timeout retry config\"}",
                List.of("README.md"),
                9.0D
        );
        AnswerProjectionBundle answerProjectionBundle = new AnswerProjectionBundle(
                "结论：`payment.timeout.retry` 默认值为 `5`。[[readme]][→ README.md]",
                List.of(
                        new AnswerProjection(
                                1,
                                "query-top-k:ARTICLE:readme",
                                ProjectionCitationFormat.ARTICLE,
                                "[[readme]]",
                                "readme",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        ),
                        new AnswerProjection(
                                2,
                                "query-top-k:SOURCE_FILE:README.md",
                                ProjectionCitationFormat.SOURCE_FILE,
                                "[→ README.md]",
                                "README.md",
                                ProjectionStatus.ACTIVE,
                                0,
                                null
                        )
                )
        );

        List<QuerySourceResponse> sourceResponses = QueryResponseCitationAssembler.toSourceResponses(
                answerProjectionBundle,
                List.of(sourceHit, articleHit),
                true
        );
        List<QueryArticleResponse> articleResponses = QueryResponseCitationAssembler.toArticleResponses(
                answerProjectionBundle,
                List.of(sourceHit, articleHit),
                true
        );

        assertThat(sourceResponses).hasSize(1);
        assertThat(sourceResponses.get(0).getArticleKey()).isEqualTo("legacy-default--readme");
        assertThat(sourceResponses.get(0).getConceptId()).isEqualTo("readme");
        assertThat(articleResponses).hasSize(1);
        assertThat(articleResponses.get(0).getArticleKey()).isEqualTo("legacy-default--readme");
        assertThat(articleResponses.get(0).getConceptId()).isEqualTo("readme");
    }

    /**
     * 验证 TOP_K 兜底 articles 会按 article 身份去重，而不是把同一 article 重复返回。
     */
    @Test
    void shouldDeduplicateTopKArticleResponsesByArticleIdentity() {
        QueryArticleHit firstHit = new QueryArticleHit(
                11L,
                "payment-timeout-article",
                "payment-timeout",
                "Payment Timeout",
                "payment.timeout.retry=5",
                "{\"description\":\"payment timeout retry config\"}",
                List.of("payments/gateway-config.yaml"),
                10.0D
        );
        QueryArticleHit duplicateHit = new QueryArticleHit(
                12L,
                "payment-timeout-article",
                "payment-timeout",
                "Payment Timeout Duplicate",
                "payment.timeout.retry=5",
                "{\"description\":\"duplicate hit from another channel\"}",
                List.of("payments/gateway-config.yaml"),
                9.2D
        );

        List<QueryArticleResponse> articleResponses = QueryResponseCitationAssembler.toArticleResponses(
                null,
                List.of(firstHit, duplicateHit),
                true
        );

        assertThat(articleResponses).hasSize(1);
        assertThat(articleResponses.get(0).getArticleKey()).isEqualTo("payment-timeout-article");
        assertThat(articleResponses.get(0).getDerivation()).isEqualTo("TOP_K");
    }

    /**
     * 验证 TOP_K 兜底 sources 会优先保留 article 级来源，而不是再把同一路径的 source hit 重复暴露。
     */
    @Test
    void shouldDeduplicateTopKSourceResponsesAcrossEvidenceTypes() {
        QueryArticleHit articleHit = new QueryArticleHit(
                31L,
                "legacy-default--readme",
                "readme",
                "Readme",
                "payment.timeout.retry=5",
                "{\"description\":\"payment timeout retry config\"}",
                List.of("README.md"),
                10.0D
        );
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                null,
                null,
                "README.md#0",
                "README.md",
                "payment.timeout.retry=5",
                "{\"filePath\":\"README.md\"}",
                List.of("README.md"),
                9.0D
        );

        List<QuerySourceResponse> sourceResponses = QueryResponseCitationAssembler.toSourceResponses(
                null,
                List.of(articleHit, sourceHit),
                true
        );

        assertThat(sourceResponses).hasSize(1);
        assertThat(sourceResponses.get(0).getArticleKey()).isEqualTo("legacy-default--readme");
        assertThat(sourceResponses.get(0).getSourcePaths()).containsExactly("README.md");
        assertThat(sourceResponses.get(0).getDerivation()).isEqualTo("TOP_K");
    }
}
