package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 加权 RRF 融合测试
 *
 * 职责：验证不同 channel 权重会影响最终排序
 *
 * @author xiexu
 */
class WeightedRrfFusionTest {

    /**
     * 验证 chunk 向量权重更高时，会把对应候选提升到前面。
     */
    @Test
    void shouldPromoteCandidateWhenChunkVectorWeightHigher() {
        RrfFusionService rrfFusionService = new RrfFusionService();

        QueryArticleHit articleA = new QueryArticleHit("article-a", "Article A", "A", "{}", List.of("a.md"), 0.9D);
        QueryArticleHit articleB = new QueryArticleHit("article-b", "Article B", "B", "{}", List.of("b.md"), 0.8D);
        QueryArticleHit articleBChunk = new QueryArticleHit("article-b", "Article B", "B chunk", "{}", List.of("b.md"), 0.95D);

        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(
                Map.of(
                        "fts", List.of(articleA, articleB),
                        "chunk_vector", List.of(articleBChunk)
                ),
                Map.of(
                        "fts", 1.0D,
                        "chunk_vector", 8.0D
                ),
                5,
                1
        );

        assertThat(fusedHits).hasSize(2);
        assertThat(fusedHits.get(0).getConceptId()).isEqualTo("article-b");
        assertThat(fusedHits.get(1).getConceptId()).isEqualTo("article-a");
    }
}
