package com.xbk.lattice.query.service;

import org.springframework.context.annotation.Profile;
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
@Profile("jdbc")
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
            ArticleChunkVectorHit currentBest = bestHitByConcept.get(chunkHit.getConceptId());
            if (currentBest == null || chunkHit.getScore() > currentBest.getScore()) {
                bestHitByConcept.put(chunkHit.getConceptId(), chunkHit);
            }
        }
        return bestHitByConcept.values().stream()
                .map(chunkHit -> new QueryArticleHit(
                        QueryEvidenceType.ARTICLE,
                        chunkHit.getConceptId(),
                        chunkHit.getTitle(),
                        chunkHit.getChunkText(),
                        chunkHit.getMetadataJson(),
                        chunkHit.getSourcePaths(),
                        chunkHit.getScore()
                ))
                .toList();
    }
}
