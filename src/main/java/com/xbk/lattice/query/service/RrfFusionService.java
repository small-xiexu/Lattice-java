package com.xbk.lattice.query.service;

import org.springframework.beans.factory.annotation.Autowired;
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
 * 职责：按 channel 权重执行 RRF 融合
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class RrfFusionService {

    private static final int DEFAULT_RRF_K = 60;

    private final QueryRetrievalSettingsService queryRetrievalSettingsService;

    /**
     * 创建 RRF 融合服务。
     */
    public RrfFusionService() {
        this(null);
    }

    /**
     * 创建 RRF 融合服务。
     *
     * @param queryRetrievalSettingsService Query 检索配置服务
     */
    @Autowired
    public RrfFusionService(QueryRetrievalSettingsService queryRetrievalSettingsService) {
        this.queryRetrievalSettingsService = queryRetrievalSettingsService;
    }

    /**
     * 融合两路检索结果。
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
        Map<String, List<QueryArticleHit>> channelHits = new LinkedHashMap<String, List<QueryArticleHit>>();
        Map<String, Double> weights = new LinkedHashMap<String, Double>();
        for (int index = 0; index < hitGroups.size(); index++) {
            String channel = "channel_" + index;
            channelHits.put(channel, hitGroups.get(index));
            weights.put(channel, 1.0D);
        }
        return fuse(channelHits, weights, limit, DEFAULT_RRF_K);
    }

    /**
     * 按 channel 与权重融合多路检索结果。
     *
     * @param channelHits 分通道命中
     * @param weights 通道权重
     * @param limit 返回数量
     * @return 融合结果
     */
    public List<QueryArticleHit> fuse(
            Map<String, List<QueryArticleHit>> channelHits,
            Map<String, Double> weights,
            int limit
    ) {
        int rrfK = queryRetrievalSettingsService == null
                ? DEFAULT_RRF_K
                : queryRetrievalSettingsService.getCurrentState().getRrfK();
        return fuse(channelHits, weights, limit, rrfK);
    }

    /**
     * 按 channel、权重与 rrfK 融合多路检索结果。
     *
     * @param channelHits 分通道命中
     * @param weights 通道权重
     * @param limit 返回数量
     * @param rrfK RRF K 值
     * @return 融合结果
     */
    public List<QueryArticleHit> fuse(
            Map<String, List<QueryArticleHit>> channelHits,
            Map<String, Double> weights,
            int limit,
            int rrfK
    ) {
        Map<String, QueryArticleHit> articleHitMap = new LinkedHashMap<String, QueryArticleHit>();
        Map<String, Double> scoreMap = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, List<QueryArticleHit>> entry : channelHits.entrySet()) {
            double weight = weights.getOrDefault(entry.getKey(), 1.0D);
            mergeHits(articleHitMap, scoreMap, entry.getValue(), weight, rrfK);
        }

        List<QueryArticleHit> fusedHits = new ArrayList<QueryArticleHit>();
        for (Map.Entry<String, QueryArticleHit> entry : articleHitMap.entrySet()) {
            fusedHits.add(new QueryArticleHit(
                    entry.getValue().getEvidenceType(),
                    entry.getValue().getSourceId(),
                    entry.getValue().getArticleKey(),
                    entry.getValue().getConceptId(),
                    entry.getValue().getTitle(),
                    entry.getValue().getContent(),
                    entry.getValue().getMetadataJson(),
                    entry.getValue().getReviewStatus(),
                    entry.getValue().getSourcePaths(),
                    scoreMap.getOrDefault(entry.getKey(), 0.0D)
            ));
        }
        fusedHits.sort(Comparator.comparing(QueryArticleHit::getScore).reversed()
                .thenComparing(QueryArticleHit::getConceptId));
        if (fusedHits.size() <= limit) {
            return fusedHits;
        }
        return fusedHits.subList(0, limit);
    }

    private void mergeHits(
            Map<String, QueryArticleHit> articleHitMap,
            Map<String, Double> scoreMap,
            List<QueryArticleHit> hits,
            double weight,
            int rrfK
    ) {
        for (int index = 0; index < hits.size(); index++) {
            QueryArticleHit hit = hits.get(index);
            String hitKey = buildHitKey(hit);
            articleHitMap.putIfAbsent(hitKey, hit);
            double rrfScore = weight / (rrfK + index + 1.0D);
            scoreMap.merge(hitKey, rrfScore, Double::sum);
        }
    }

    private String buildHitKey(QueryArticleHit queryArticleHit) {
        String identity = queryArticleHit.getArticleKey();
        if (identity == null || identity.isBlank()) {
            identity = queryArticleHit.getConceptId();
        }
        return queryArticleHit.getEvidenceType().name() + ":" + identity;
    }
}
