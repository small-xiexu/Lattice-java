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
        Map<String, ArticleChunkVectorHit> bestHitByChunk = new LinkedHashMap<String, ArticleChunkVectorHit>();
        for (ArticleChunkVectorHit chunkHit : chunkHits) {
            String chunkIdentity = ChunkHitIdentitySupport.articleChunkIdentity(
                    chunkHit.getArticleKey(),
                    chunkHit.getConceptId(),
                    Integer.valueOf(chunkHit.getChunkIndex())
            );
            ArticleChunkVectorHit currentBest = bestHitByChunk.get(chunkIdentity);
            if (currentBest == null || chunkHit.getScore() > currentBest.getScore()) {
                bestHitByChunk.put(chunkIdentity, chunkHit);
            }
        }
        return bestHitByChunk.values().stream()
                .map(this::toQueryArticleHit)
                .toList();
    }

    /**
     * 转换为携带 chunk 身份的查询命中。
     *
     * @param chunkHit chunk 向量命中
     * @return 查询命中
     */
    private QueryArticleHit toQueryArticleHit(ArticleChunkVectorHit chunkHit) {
        String sectionAnchor = ChunkHitIdentitySupport.extractSectionAnchor(chunkHit.getChunkText());
        String displayTitle = ChunkHitIdentitySupport.displayTitle(chunkHit.getTitle(), sectionAnchor);
        String metadataJson = ChunkHitIdentitySupport.enrichMetadata(
                chunkHit.getMetadataJson(),
                chunkHit.getArticleKey(),
                chunkHit.getConceptId(),
                Integer.valueOf(chunkHit.getChunkIndex()),
                RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR,
                sectionAnchor
        );
        return new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                chunkHit.getSourceId(),
                chunkHit.getArticleKey(),
                chunkHit.getConceptId(),
                displayTitle,
                chunkHit.getChunkText(),
                metadataJson,
                chunkHit.getReviewStatus(),
                chunkHit.getSourcePaths(),
                chunkHit.getScore()
        );
    }
}
