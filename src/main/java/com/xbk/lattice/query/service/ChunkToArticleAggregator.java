package com.xbk.lattice.query.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chunk 命中聚合器
 *
 * 职责：把 chunk 级命中聚合为 article 级候选
 *
 * @author xiexu
 */
@Component
public class ChunkToArticleAggregator {

    /**
     * 把 chunk 命中聚合为 article 命中。
     *
     * @param chunkHits chunk 命中列表
     * @return article 命中列表
     */
    public List<QueryArticleHit> aggregate(List<ArticleChunkVectorHit> chunkHits) {
        Map<String, ArticleChunkVectorHit> bestHitByConcept = new LinkedHashMap<String, ArticleChunkVectorHit>();
        for (ArticleChunkVectorHit chunkHit : chunkHits) {
            String articleIdentity = chunkHit.getArticleKey();
            if (articleIdentity == null || articleIdentity.isBlank()) {
                articleIdentity = chunkHit.getConceptId();
            }
            ArticleChunkVectorHit currentBest = bestHitByConcept.get(articleIdentity);
            if (currentBest == null || chunkHit.getScore() > currentBest.getScore()) {
                bestHitByConcept.put(articleIdentity, chunkHit);
            }
        }
        return bestHitByConcept.values().stream()
                .map(chunkHit -> new QueryArticleHit(
                        QueryEvidenceType.ARTICLE,
                        chunkHit.getSourceId(),
                        chunkHit.getArticleKey(),
                        chunkHit.getConceptId(),
                        chunkHit.getTitle(),
                        chunkHit.getChunkText(),
                        chunkHit.getMetadataJson(),
                        chunkHit.getReviewStatus(),
                        chunkHit.getSourcePaths(),
                        chunkHit.getScore()
                ))
                .toList();
    }
}
