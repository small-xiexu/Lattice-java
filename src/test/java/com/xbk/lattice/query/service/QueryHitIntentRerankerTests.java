package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Query 命中意图重排器测试
 *
 * 职责：验证配置题与架构题会优先保留更像直接答案的证据，而不是被截图或元文档带偏
 *
 * @author xiexu
 */
class QueryHitIntentRerankerTests {

    /**
     * 验证配置题会提升真实配置命中，并压低截图类命中。
     */
    @Test
    void shouldPromoteStructuredConfigEvidenceOverScreenshotHits() {
        List<QueryArticleHit> rerankedHits = QueryHitIntentReranker.rerank(
                "payment timeout retry 是什么配置？",
                QueryIntent.CONFIGURATION,
                List.of(
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                1L,
                                "legacy-default--images",
                                "images",
                                "Images",
                                "payment timeout retry 是什么配置",
                                "{\"description\":\"控制台截图\"}",
                                List.of("images/readme/ask-answer-panel-20260423.png"),
                                24.0D
                        ),
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                1L,
                                "legacy-default--payments",
                                "payments",
                                "Payments",
                                "payment.timeout.retry = 3",
                                "{\"description\":\"支付配置\"}",
                                List.of("payments/gateway-config.yaml"),
                                8.0D
                        )
                )
        );

        assertThat(rerankedHits).hasSize(2);
        assertThat(rerankedHits.get(0).getConceptId()).isEqualTo("payments");
        assertThat(rerankedHits.get(1).getConceptId()).isEqualTo("images");
    }

    /**
     * 验证架构题会提升 ADR / architecture 资料，并压低说明型元文档。
     */
    @Test
    void shouldPromoteArchitectureEvidenceOverMetaDocs() {
        List<QueryArticleHit> rerankedHits = QueryHitIntentReranker.rerank(
                "为什么订单服务不直接同步调用库存服务，而要走消息队列？",
                QueryIntent.ARCHITECTURE,
                List.of(
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                1L,
                                "legacy-default--项目全流程真实验收手册",
                                "项目全流程真实验收手册",
                                "项目全流程真实验收手册",
                                "这里提到过这句示例问题。",
                                "{\"description\":\"验收说明\"}",
                                List.of("项目全流程真实验收手册.md"),
                                18.0D
                        ),
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                1L,
                                "legacy-default--architecture",
                                "architecture",
                                "Architecture",
                                "订单服务通过消息队列驱动库存预留，避免同步耦合。",
                                "{\"description\":\"订单到库存的架构决策\"}",
                                List.of("architecture/order-inventory-adr.md"),
                                10.0D
                        )
                )
        );

        assertThat(rerankedHits).hasSize(2);
        assertThat(rerankedHits.get(0).getConceptId()).isEqualTo("architecture");
        assertThat(rerankedHits.get(1).getConceptId()).isEqualTo("项目全流程真实验收手册");
    }

    /**
     * 验证“消息队列 / 同步调用”类问题会被识别为架构题，而不是先落到排障题。
     */
    @Test
    void shouldClassifyQueueDecisionQuestionAsArchitecture() {
        QueryIntentClassifier queryIntentClassifier = new QueryIntentClassifier();

        QueryIntent queryIntent = queryIntentClassifier.classify(
                "为什么订单服务不直接同步调用库存服务，而要走消息队列？"
        );

        assertThat(queryIntent).isEqualTo(QueryIntent.ARCHITECTURE);
    }
}
