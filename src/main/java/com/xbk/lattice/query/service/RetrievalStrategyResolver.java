package com.xbk.lattice.query.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 检索策略解析器
 *
 * 职责：把全局检索配置与查询意图合成为单次查询的通道权重和启停策略
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class RetrievalStrategyResolver {

    public static final String CHANNEL_FTS = "fts";

    public static final String CHANNEL_ARTICLE_CHUNK_FTS = "article_chunk_fts";

    public static final String CHANNEL_REFKEY = "refkey";

    public static final String CHANNEL_SOURCE = "source";

    public static final String CHANNEL_SOURCE_CHUNK_FTS = "source_chunk_fts";

    public static final String CHANNEL_CONTRIBUTION = "contribution";

    public static final String CHANNEL_GRAPH = "graph";

    public static final String CHANNEL_ARTICLE_VECTOR = "article_vector";

    public static final String CHANNEL_CHUNK_VECTOR = "chunk_vector";

    /**
     * 解析检索策略。
     *
     * @param retrievalQuestion 有效检索问题
     * @param queryIntent 查询意图
     * @param settings 检索配置
     * @return 检索策略
     */
    public RetrievalStrategy resolve(
            String retrievalQuestion,
            QueryIntent queryIntent,
            QueryRetrievalSettingsState settings
    ) {
        QueryRetrievalSettingsState effectiveSettings = settings == null
                ? new QueryRetrievalSettingsService().defaultState()
                : settings;
        QueryIntent effectiveIntent = queryIntent == null ? QueryIntent.GENERAL : queryIntent;
        Map<String, Double> weights = baseWeights(effectiveSettings);
        applyIntentBoost(weights, effectiveIntent);
        applySemanticBoost(weights, retrievalQuestion, effectiveIntent);
        Set<String> enabledChannels = enabledChannels(weights);
        if (!shouldEnableVector(retrievalQuestion, effectiveIntent, effectiveSettings)) {
            enabledChannels.remove(CHANNEL_ARTICLE_VECTOR);
            enabledChannels.remove(CHANNEL_CHUNK_VECTOR);
            weights.put(CHANNEL_ARTICLE_VECTOR, 0.0D);
            weights.put(CHANNEL_CHUNK_VECTOR, 0.0D);
        }
        return new RetrievalStrategy(
                retrievalQuestion,
                effectiveIntent,
                effectiveSettings.isParallelEnabled(),
                effectiveSettings.getRrfK(),
                weights,
                enabledChannels
        );
    }

    /**
     * 构建基础通道权重。
     *
     * @param settings 检索配置
     * @return 基础权重
     */
    private Map<String, Double> baseWeights(QueryRetrievalSettingsState settings) {
        Map<String, Double> weights = new LinkedHashMap<String, Double>();
        weights.put(CHANNEL_FTS, settings.getFtsWeight());
        weights.put(CHANNEL_ARTICLE_CHUNK_FTS, settings.getArticleChunkWeight());
        weights.put(CHANNEL_REFKEY, settings.getRefkeyWeight());
        weights.put(CHANNEL_SOURCE, settings.getSourceWeight());
        weights.put(CHANNEL_SOURCE_CHUNK_FTS, settings.getSourceChunkWeight());
        weights.put(CHANNEL_CONTRIBUTION, settings.getContributionWeight());
        weights.put(CHANNEL_GRAPH, settings.getGraphWeight());
        weights.put(CHANNEL_ARTICLE_VECTOR, settings.getArticleVectorWeight());
        weights.put(CHANNEL_CHUNK_VECTOR, settings.getChunkVectorWeight());
        return weights;
    }

    /**
     * 根据意图调整权重。
     *
     * @param weights 通道权重
     * @param queryIntent 查询意图
     */
    private void applyIntentBoost(Map<String, Double> weights, QueryIntent queryIntent) {
        if (queryIntent == QueryIntent.CODE_STRUCTURE) {
            multiply(weights, CHANNEL_GRAPH, 1.35D);
            multiply(weights, CHANNEL_SOURCE, 1.15D);
            multiply(weights, CHANNEL_SOURCE_CHUNK_FTS, 1.35D);
            multiply(weights, CHANNEL_ARTICLE_CHUNK_FTS, 1.15D);
        }
        else if (queryIntent == QueryIntent.CONFIGURATION) {
            multiply(weights, CHANNEL_REFKEY, 1.35D);
            multiply(weights, CHANNEL_SOURCE, 1.15D);
            multiply(weights, CHANNEL_SOURCE_CHUNK_FTS, 1.35D);
            multiply(weights, CHANNEL_ARTICLE_CHUNK_FTS, 1.15D);
        }
        else if (queryIntent == QueryIntent.TROUBLESHOOTING) {
            multiply(weights, CHANNEL_CONTRIBUTION, 1.25D);
            multiply(weights, CHANNEL_SOURCE_CHUNK_FTS, 1.20D);
            multiply(weights, CHANNEL_ARTICLE_CHUNK_FTS, 1.15D);
            multiply(weights, CHANNEL_REFKEY, 1.10D);
            multiply(weights, CHANNEL_GRAPH, 1.10D);
        }
        else if (queryIntent == QueryIntent.ARCHITECTURE) {
            multiply(weights, CHANNEL_GRAPH, 1.25D);
            multiply(weights, CHANNEL_ARTICLE_VECTOR, 1.25D);
            multiply(weights, CHANNEL_CHUNK_VECTOR, 1.20D);
            multiply(weights, CHANNEL_ARTICLE_CHUNK_FTS, 1.10D);
        }
    }

    /**
     * 对宽泛概念类问题做受控的语义召回提升。
     *
     * @param weights 通道权重
     * @param retrievalQuestion 有效检索问题
     * @param queryIntent 查询意图
     */
    private void applySemanticBoost(
            Map<String, Double> weights,
            String retrievalQuestion,
            QueryIntent queryIntent
    ) {
        if (queryIntent != QueryIntent.GENERAL && queryIntent != QueryIntent.ARCHITECTURE) {
            return;
        }
        if (!hasSemanticSignal(retrievalQuestion)) {
            return;
        }
        multiply(weights, CHANNEL_ARTICLE_VECTOR, 1.10D);
        multiply(weights, CHANNEL_CHUNK_VECTOR, 1.05D);
    }

    /**
     * 乘以指定权重系数。
     *
     * @param weights 通道权重
     * @param channel 通道名
     * @param multiplier 系数
     */
    private void multiply(Map<String, Double> weights, String channel, double multiplier) {
        weights.put(channel, weights.getOrDefault(channel, 0.0D) * multiplier);
    }

    /**
     * 根据权重大于 0 推导启用通道。
     *
     * @param weights 通道权重
     * @return 启用通道
     */
    private Set<String> enabledChannels(Map<String, Double> weights) {
        Set<String> enabledChannels = new LinkedHashSet<String>();
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            if (entry.getValue() != null && entry.getValue().doubleValue() > 0.0D) {
                enabledChannels.add(entry.getKey());
            }
        }
        return enabledChannels;
    }

    /**
     * 判断当前查询是否应该触发向量通道。
     *
     * @param retrievalQuestion 有效检索问题
     * @param queryIntent 查询意图
     * @param settings 检索配置
     * @return 是否启用向量通道
     */
    private boolean shouldEnableVector(
            String retrievalQuestion,
            QueryIntent queryIntent,
            QueryRetrievalSettingsState settings
    ) {
        if (!settings.isIntentAwareVectorEnabled()) {
            return true;
        }
        if (queryIntent == QueryIntent.CODE_STRUCTURE || queryIntent == QueryIntent.CONFIGURATION) {
            return false;
        }
        return hasSemanticSignal(retrievalQuestion);
    }

    /**
     * 判断问题是否具备足够的语义信号，值得触发向量通道。
     *
     * @param retrievalQuestion 有效检索问题
     * @return 是否具备语义信号
     */
    private boolean hasSemanticSignal(String retrievalQuestion) {
        return !QueryEvidenceRelevanceSupport.extractHighSignalTokens(retrievalQuestion).isEmpty();
    }
}
