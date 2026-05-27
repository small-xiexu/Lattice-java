package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chunk 聚合测试
 *
 * 职责：验证 chunk 向量命中的身份保留与同 chunk 去重
 *
 * @author xiexu
 */
class ChunkToArticleAggregatorTest {

    /**
     * 验证同一 article 的不同 chunk 命中保留独立席位。
     */
    @Test
    void shouldKeepDifferentChunksAsIndependentHits() {
        ChunkToArticleAggregator aggregator = new ChunkToArticleAggregator();

        List<QueryArticleHit> aggregatedHits = aggregator.aggregate(List.of(
                new ArticleChunkVectorHit(1L, "payment-timeout", "Payment Timeout", "full article", "{}",
                        List.of("payment.md"), 0, "chunk-low", 0.61D),
                new ArticleChunkVectorHit(1L, "payment-timeout", "Payment Timeout", "full article", "{}",
                        List.of("payment.md"), 1, "chunk-high", 0.93D)
        ));

        assertThat(aggregatedHits).hasSize(2);
        assertThat(aggregatedHits)
                .extracting(QueryArticleHit::getContent)
                .containsExactly("chunk-low", "chunk-high");
        assertThat(aggregatedHits)
                .extracting(QueryArticleHit::getMetadataJson)
                .allSatisfy(metadataJson -> assertThat(metadataJson).contains("\"chunkIdentity\""));
    }

    /**
     * 验证同一 chunk 重复命中时仍取最高分结果。
     */
    @Test
    void shouldUseBestHitWhenSameChunkAppearsMoreThanOnce() {
        ChunkToArticleAggregator aggregator = new ChunkToArticleAggregator();

        List<QueryArticleHit> aggregatedHits = aggregator.aggregate(List.of(
                new ArticleChunkVectorHit(1L, "payment-timeout", "Payment Timeout", "full article", "{}",
                        List.of("payment.md"), 1, "chunk-low", 0.61D),
                new ArticleChunkVectorHit(1L, "payment-timeout", "Payment Timeout", "full article", "{}",
                        List.of("payment.md"), 1, "chunk-high", 0.93D)
        ));

        assertThat(aggregatedHits).hasSize(1);
        assertThat(aggregatedHits.get(0).getConceptId()).isEqualTo("payment-timeout");
        assertThat(aggregatedHits.get(0).getContent()).isEqualTo("chunk-high");
        assertThat(aggregatedHits.get(0).getScore()).isEqualTo(0.93D);
    }
}
