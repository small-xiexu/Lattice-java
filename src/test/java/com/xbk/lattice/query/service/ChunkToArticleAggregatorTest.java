package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chunk 聚合测试
 *
 * 职责：验证同一文章多 chunk 命中时按最高分 chunk 聚合
 *
 * @author xiexu
 */
class ChunkToArticleAggregatorTest {

    /**
     * 验证同一 article 多个 chunk 命中时取最高分 chunk。
     */
    @Test
    void shouldUseBestChunkWhenAggregatingSameArticle() {
        ChunkToArticleAggregator aggregator = new ChunkToArticleAggregator();

        List<QueryArticleHit> aggregatedHits = aggregator.aggregate(List.of(
                new ArticleChunkVectorHit(1L, "payment-timeout", "Payment Timeout", "full article", "{}",
                        List.of("payment.md"), 0, "chunk-low", 0.61D),
                new ArticleChunkVectorHit(1L, "payment-timeout", "Payment Timeout", "full article", "{}",
                        List.of("payment.md"), 1, "chunk-high", 0.93D)
        ));

        assertThat(aggregatedHits).hasSize(1);
        assertThat(aggregatedHits.get(0).getConceptId()).isEqualTo("payment-timeout");
        assertThat(aggregatedHits.get(0).getContent()).isEqualTo("chunk-high");
        assertThat(aggregatedHits.get(0).getScore()).isEqualTo(0.93D);
    }
}
