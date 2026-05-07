package com.xbk.lattice.query.service;

import com.xbk.lattice.query.evidence.domain.AnswerShape;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RRF 融合服务
 *
 * 职责：按 channel 权重执行 RRF 融合
 *
 * @author xiexu
 */
@Service
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
     * 按检索策略融合多路检索结果。
     *
     * @param channelHits 分通道命中
     * @param retrievalStrategy 检索策略
     * @param limit 返回数量
     * @return 融合结果
     */
    public List<QueryArticleHit> fuse(
            Map<String, List<QueryArticleHit>> channelHits,
            RetrievalStrategy retrievalStrategy,
            int limit
    ) {
        if (retrievalStrategy == null) {
            return fuse(channelHits, Map.of(), limit);
        }
        return fuse(
                channelHits,
                retrievalStrategy.getChannelWeights(),
                limit,
                retrievalStrategy.getRrfK(),
                retrievalStrategy.getAnswerShape(),
                retrievalStrategy.getRetrievalQuestion()
        );
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
        return fuse(channelHits, weights, limit, rrfK, AnswerShape.GENERAL);
    }

    /**
     * 按 channel、权重、rrfK 与答案形态融合多路检索结果。
     *
     * @param channelHits 分通道命中
     * @param weights 通道权重
     * @param limit 返回数量
     * @param rrfK RRF K 值
     * @param answerShape 答案形态
     * @return 融合结果
     */
    private List<QueryArticleHit> fuse(
            Map<String, List<QueryArticleHit>> channelHits,
            Map<String, Double> weights,
            int limit,
            int rrfK,
            AnswerShape answerShape
    ) {
        return fuse(channelHits, weights, limit, rrfK, answerShape, "");
    }

    /**
     * 按 channel、权重、rrfK、答案形态与检索问题融合多路检索结果。
     *
     * @param channelHits 分通道命中
     * @param weights 通道权重
     * @param limit 返回数量
     * @param rrfK RRF K 值
     * @param answerShape 答案形态
     * @param retrievalQuestion 检索问题
     * @return 融合结果
     */
    private List<QueryArticleHit> fuse(
            Map<String, List<QueryArticleHit>> channelHits,
            Map<String, Double> weights,
            int limit,
            int rrfK,
            AnswerShape answerShape,
            String retrievalQuestion
    ) {
        if (limit <= 0 || channelHits == null || channelHits.isEmpty()) {
            return List.of();
        }
        Map<String, Double> effectiveWeights = weights == null ? Map.of() : weights;
        Map<String, QueryArticleHit> articleHitMap = new LinkedHashMap<String, QueryArticleHit>();
        Map<String, Double> scoreMap = new LinkedHashMap<String, Double>();
        Map<String, Set<String>> channelMap = new LinkedHashMap<String, Set<String>>();
        for (Map.Entry<String, List<QueryArticleHit>> entry : channelHits.entrySet()) {
            double weight = effectiveWeights.getOrDefault(entry.getKey(), 1.0D);
            mergeHits(articleHitMap, scoreMap, channelMap, entry.getKey(), entry.getValue(), weight, rrfK);
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
                .thenComparing(QueryArticleHit::getConceptId, Comparator.nullsLast(String::compareTo)));
        if (isStructuredAnswerShape(answerShape)
                || hasRelevantDirectEvidence(retrievalQuestion, fusedHits, channelMap)) {
            return applyStructuredEvidenceGuardrail(fusedHits, channelMap, limit, retrievalQuestion);
        }
        if (fusedHits.size() <= limit) {
            return fusedHits;
        }
        return fusedHits.subList(0, limit);
    }

    /**
     * 对结构化答案保留 Fact Card 与 source chunk 主证据，避免背景 article 抢占 topK。
     *
     * @param fusedHits 已按 RRF 排序的全量命中
     * @param channelMap 命中所属通道
     * @param limit 返回数量
     * @return 应用保护后的命中
     */
    private List<QueryArticleHit> applyStructuredEvidenceGuardrail(
            List<QueryArticleHit> fusedHits,
            Map<String, Set<String>> channelMap,
            int limit,
            String retrievalQuestion
    ) {
        if (fusedHits.size() <= limit) {
            return sortStructuredHits(fusedHits, channelMap, retrievalQuestion);
        }
        List<QueryArticleHit> rankedHits = sortStructuredHits(fusedHits, channelMap, retrievalQuestion);
        List<QueryArticleHit> selectedHits = new ArrayList<QueryArticleHit>(rankedHits.subList(0, limit));
        Map<String, QueryArticleHit> selectedHitMap = toHitMap(selectedHits);
        for (QueryArticleHit fusedHit : rankedHits) {
            if (!isPrimaryStructuredEvidence(fusedHit, channelMap)) {
                continue;
            }
            String hitKey = buildHitKey(fusedHit);
            if (selectedHitMap.containsKey(hitKey)) {
                continue;
            }
            String replaceableHitKey = findReplaceableBackgroundHitKey(selectedHitMap, channelMap);
            if (replaceableHitKey == null) {
                break;
            }
            selectedHitMap.remove(replaceableHitKey);
            selectedHitMap.put(hitKey, fusedHit);
        }
        return sortStructuredHits(new ArrayList<QueryArticleHit>(selectedHitMap.values()), channelMap, retrievalQuestion);
    }

    /**
     * 转换为按命中身份去重的有序 Map。
     *
     * @param hits 命中列表
     * @return 命中 Map
     */
    private Map<String, QueryArticleHit> toHitMap(List<QueryArticleHit> hits) {
        Map<String, QueryArticleHit> hitMap = new LinkedHashMap<String, QueryArticleHit>();
        for (QueryArticleHit hit : hits) {
            hitMap.put(buildHitKey(hit), hit);
        }
        return hitMap;
    }

    /**
     * 查找可被结构化主证据替换的背景命中。
     *
     * @param selectedHitMap 当前已选命中
     * @param channelMap 命中所属通道
     * @return 可替换命中键
     */
    private String findReplaceableBackgroundHitKey(
            Map<String, QueryArticleHit> selectedHitMap,
            Map<String, Set<String>> channelMap
    ) {
        List<Map.Entry<String, QueryArticleHit>> selectedEntries =
                new ArrayList<Map.Entry<String, QueryArticleHit>>(selectedHitMap.entrySet());
        for (int index = selectedEntries.size() - 1; index >= 0; index--) {
            Map.Entry<String, QueryArticleHit> selectedEntry = selectedEntries.get(index);
            if (!isPrimaryStructuredEvidence(selectedEntry.getValue(), channelMap)) {
                return selectedEntry.getKey();
            }
        }
        return null;
    }

    /**
     * 结构化答案中按主证据优先、背景证据靠后的顺序输出。
     *
     * @param hits 命中列表
     * @param channelMap 命中所属通道
     * @return 排序后的命中
     */
    private List<QueryArticleHit> sortStructuredHits(
            List<QueryArticleHit> hits,
            Map<String, Set<String>> channelMap,
            String retrievalQuestion
    ) {
        List<QueryArticleHit> sortedHits = new ArrayList<QueryArticleHit>(hits);
        sortedHits.sort((leftHit, rightHit) -> {
            int leftTier = evidenceTier(leftHit, channelMap);
            int rightTier = evidenceTier(rightHit, channelMap);
            if (leftTier != rightTier) {
                return Integer.compare(leftTier, rightTier);
            }
            int relevanceComparison = Integer.compare(
                    structuredEvidenceRelevance(retrievalQuestion, rightHit, channelMap),
                    structuredEvidenceRelevance(retrievalQuestion, leftHit, channelMap)
            );
            if (relevanceComparison != 0) {
                return relevanceComparison;
            }
            int scoreComparison = Double.compare(rightHit.getScore(), leftHit.getScore());
            if (scoreComparison != 0) {
                return scoreComparison;
            }
            return buildHitKey(leftHit).compareTo(buildHitKey(rightHit));
        });
        return sortedHits;
    }

    /**
     * 计算结构化主证据与问题的相关性。
     *
     * @param retrievalQuestion 检索问题
     * @param hit 查询命中
     * @param channelMap 命中所属通道
     * @return 相关性分值
     */
    private int structuredEvidenceRelevance(
            String retrievalQuestion,
            QueryArticleHit hit,
            Map<String, Set<String>> channelMap
    ) {
        if (!isPrimaryStructuredEvidence(hit, channelMap)) {
            return 0;
        }
        return QueryEvidenceRelevanceSupport.score(retrievalQuestion, hit);
    }

    /**
     * 判断泛化问题是否仍需要保护直接原文 / fact card 证据。
     *
     * @param retrievalQuestion 检索问题
     * @param fusedHits 全量融合候选
     * @param channelMap 命中所属通道
     * @return 需要保护返回 true
     */
    private boolean hasRelevantDirectEvidence(
            String retrievalQuestion,
            List<QueryArticleHit> fusedHits,
            Map<String, Set<String>> channelMap
    ) {
        if (retrievalQuestion == null || retrievalQuestion.isBlank()
                || fusedHits == null || fusedHits.isEmpty()) {
            return false;
        }
        if (QueryEvidenceRelevanceSupport.extractHighSignalTokens(retrievalQuestion).size() < 3) {
            return false;
        }
        int bestDirectEvidenceScore = Integer.MIN_VALUE;
        int bestBackgroundScore = Integer.MIN_VALUE;
        for (QueryArticleHit fusedHit : fusedHits) {
            int relevanceScore = QueryEvidenceRelevanceSupport.score(retrievalQuestion, fusedHit);
            if (isPrimaryStructuredEvidence(fusedHit, channelMap)) {
                bestDirectEvidenceScore = Math.max(bestDirectEvidenceScore, relevanceScore);
                continue;
            }
            if (fusedHit != null
                    && (fusedHit.getEvidenceType() == QueryEvidenceType.ARTICLE
                    || fusedHit.getEvidenceType() == QueryEvidenceType.CONTRIBUTION)) {
                bestBackgroundScore = Math.max(bestBackgroundScore, relevanceScore);
            }
        }
        if (bestDirectEvidenceScore < 8) {
            return false;
        }
        if (bestBackgroundScore == Integer.MIN_VALUE) {
            return true;
        }
        return bestDirectEvidenceScore >= bestBackgroundScore - 6;
    }

    /**
     * 计算结构化答案内证据层级。
     *
     * @param hit 查询命中
     * @param channelMap 命中所属通道
     * @return 证据层级，数值越小越靠前
     */
    private int evidenceTier(QueryArticleHit hit, Map<String, Set<String>> channelMap) {
        if (isPrimaryStructuredEvidence(hit, channelMap)) {
            return 0;
        }
        if (hit != null
                && hit.getEvidenceType() == QueryEvidenceType.FACT_CARD
                && FactCardReviewUsagePolicy.isBackgroundOnly(hit.getReviewStatus())) {
            return 3;
        }
        if (hit != null && hit.getEvidenceType() == QueryEvidenceType.ARTICLE) {
            return 2;
        }
        return 1;
    }

    /**
     * 判断命中是否来自结构化主证据通道。
     *
     * @param hit 查询命中
     * @param channelMap 命中所属通道
     * @return 是主证据返回 true
     */
    private boolean isPrimaryStructuredEvidence(QueryArticleHit hit, Map<String, Set<String>> channelMap) {
        if (hit == null) {
            return false;
        }
        if (hit.getEvidenceType() == QueryEvidenceType.FACT_CARD
                && !FactCardReviewUsagePolicy.allowsPrimaryEvidence(hit.getReviewStatus())) {
            return false;
        }
        Set<String> channels = channelMap.get(buildHitKey(hit));
        if (channels == null || channels.isEmpty()) {
            return false;
        }
        return channels.contains(RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS)
                || channels.contains(RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR)
                || channels.contains(RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS);
    }

    /**
     * 判断答案形态是否需要结构化证据保护。
     *
     * @param answerShape 答案形态
     * @return 需要保护返回 true
     */
    private boolean isStructuredAnswerShape(AnswerShape answerShape) {
        return answerShape == AnswerShape.ENUM
                || answerShape == AnswerShape.COMPARE
                || answerShape == AnswerShape.SEQUENCE
                || answerShape == AnswerShape.STATUS
                || answerShape == AnswerShape.POLICY;
    }

    /**
     * 合并单通道命中。
     *
     * @param articleHitMap 命中 Map
     * @param scoreMap 分数 Map
     * @param channelMap 通道 Map
     * @param channel 通道名
     * @param hits 通道命中
     * @param weight 通道权重
     * @param rrfK RRF K 值
     */
    private void mergeHits(
            Map<String, QueryArticleHit> articleHitMap,
            Map<String, Double> scoreMap,
            Map<String, Set<String>> channelMap,
            String channel,
            List<QueryArticleHit> hits,
            double weight,
            int rrfK
    ) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        for (int index = 0; index < hits.size(); index++) {
            QueryArticleHit hit = hits.get(index);
            if (hit == null) {
                continue;
            }
            String hitKey = buildHitKey(hit);
            articleHitMap.putIfAbsent(hitKey, hit);
            channelMap.computeIfAbsent(hitKey, key -> new LinkedHashSet<String>()).add(channel);
            double rrfScore = weight / (rrfK + index + 1.0D);
            scoreMap.merge(hitKey, rrfScore, Double::sum);
        }
    }

    /**
     * 构建命中身份键。
     *
     * @param queryArticleHit 查询命中
     * @return 命中身份键
     */
    private String buildHitKey(QueryArticleHit queryArticleHit) {
        String identity = queryArticleHit.getArticleKey();
        if (identity == null || identity.isBlank()) {
            identity = queryArticleHit.getConceptId();
        }
        return queryArticleHit.getEvidenceType().name() + ":" + identity;
    }
}
