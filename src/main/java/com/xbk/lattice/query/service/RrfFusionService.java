package com.xbk.lattice.query.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF 融合服务
 *
 * 职责：融合多路查询结果
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class RrfFusionService {

    private static final double RRF_K = 60.0;

    /**
     * 融合多路检索结果。
     *
     * @param ftsHits FTS 命中
     * @param refKeyHits 引用词命中
     * @param limit 返回数量
     * @return 融合结果
     */
    public List<QueryArticleHit> fuse(List<QueryArticleHit> ftsHits, List<QueryArticleHit> refKeyHits, int limit) {
        return fuse(List.of(ftsHits, refKeyHits), limit);
    }

    /**
     * 融合多路检索结果。
     *
     * @param hitGroups 检索结果分组
     * @param limit 返回数量
     * @return 融合结果
     */
    public List<QueryArticleHit> fuse(List<List<QueryArticleHit>> hitGroups, int limit) {
        Map<String, QueryArticleHit> articleHitMap = new LinkedHashMap<String, QueryArticleHit>();
        Map<String, Double> scoreMap = new LinkedHashMap<String, Double>();
        for (List<QueryArticleHit> hitGroup : hitGroups) {
            mergeHits(articleHitMap, scoreMap, hitGroup);
        }

        List<QueryArticleHit> fusedHits = new ArrayList<QueryArticleHit>();
        for (Map.Entry<String, QueryArticleHit> entry : articleHitMap.entrySet()) {
            fusedHits.add(new QueryArticleHit(
                    entry.getValue().getEvidenceType(),
                    entry.getValue().getConceptId(),
                    entry.getValue().getTitle(),
                    entry.getValue().getContent(),
                    entry.getValue().getMetadataJson(),
                    entry.getValue().getSourcePaths(),
                    scoreMap.getOrDefault(entry.getKey(), 0.0)
            ));
        }
        fusedHits.sort(Comparator.comparing(QueryArticleHit::getScore).reversed()
                .thenComparing(QueryArticleHit::getConceptId));
        if (fusedHits.size() <= limit) {
            return fusedHits;
        }
        return fusedHits.subList(0, limit);
    }

    /**
     * 合并单路检索结果。
     *
     * @param articleHitMap 命中映射
     * @param scoreMap 评分映射
     * @param hits 命中列表
     */
    private void mergeHits(
            Map<String, QueryArticleHit> articleHitMap,
            Map<String, Double> scoreMap,
            List<QueryArticleHit> hits
    ) {
        for (int index = 0; index < hits.size(); index++) {
            QueryArticleHit hit = hits.get(index);
            String hitKey = buildHitKey(hit);
            articleHitMap.putIfAbsent(hitKey, hit);
            double rrfScore = 1.0 / (RRF_K + index + 1);
            scoreMap.merge(hitKey, rrfScore, Double::sum);
        }
    }

    /**
     * 构建命中的稳定融合键。
     *
     * @param queryArticleHit 查询命中
     * @return 融合键
     */
    private String buildHitKey(QueryArticleHit queryArticleHit) {
        return queryArticleHit.getEvidenceType().name() + ":" + queryArticleHit.getConceptId();
    }
}
