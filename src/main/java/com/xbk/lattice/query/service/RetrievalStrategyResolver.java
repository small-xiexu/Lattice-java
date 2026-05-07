package com.xbk.lattice.query.service;

import com.xbk.lattice.query.evidence.domain.AnswerShape;
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
public class RetrievalStrategyResolver {

    public static final String CHANNEL_FTS = "fts";

    public static final String CHANNEL_ARTICLE_CHUNK_FTS = "article_chunk_fts";

    public static final String CHANNEL_REFKEY = "refkey";

    public static final String CHANNEL_SOURCE = "source";

    public static final String CHANNEL_SOURCE_CHUNK_FTS = "source_chunk_fts";

    public static final String CHANNEL_FACT_CARD_FTS = "fact_card_fts";

    public static final String CHANNEL_FACT_CARD_VECTOR = "fact_card_vector";

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
        return resolve(retrievalQuestion, queryIntent, AnswerShape.GENERAL, settings);
    }

    /**
     * 解析检索策略。
     *
     * @param retrievalQuestion 有效检索问题
     * @param queryIntent 查询意图
     * @param answerShape 答案形态
     * @param settings 检索配置
     * @return 检索策略
     */
    public RetrievalStrategy resolve(
            String retrievalQuestion,
            QueryIntent queryIntent,
            AnswerShape answerShape,
            QueryRetrievalSettingsState settings
    ) {
        QueryRetrievalSettingsState effectiveSettings = settings == null
                ? new QueryRetrievalSettingsService().defaultState()
                : settings;
        QueryIntent effectiveIntent = queryIntent == null ? QueryIntent.GENERAL : queryIntent;
        AnswerShape effectiveAnswerShape = answerShape == null ? AnswerShape.GENERAL : answerShape;
        Map<String, Double> weights = baseWeights(effectiveSettings);
        applyIntentBoost(weights, effectiveIntent);
        applyAnswerShapeBoost(weights, effectiveAnswerShape);
        applySemanticBoost(weights, retrievalQuestion, effectiveIntent);
        applyExactLookupFocus(weights, retrievalQuestion, effectiveIntent, effectiveAnswerShape);
        Set<String> enabledChannels = enabledChannels(weights);
        if (shouldDisableGraphForExactLookup(retrievalQuestion, effectiveIntent)) {
            enabledChannels.remove(CHANNEL_GRAPH);
            weights.put(CHANNEL_GRAPH, 0.0D);
        }
        if (!shouldEnableVector(retrievalQuestion, effectiveIntent, effectiveAnswerShape, effectiveSettings)) {
            enabledChannels.remove(CHANNEL_FACT_CARD_VECTOR);
            enabledChannels.remove(CHANNEL_ARTICLE_VECTOR);
            enabledChannels.remove(CHANNEL_CHUNK_VECTOR);
            weights.put(CHANNEL_FACT_CARD_VECTOR, 0.0D);
            weights.put(CHANNEL_ARTICLE_VECTOR, 0.0D);
            weights.put(CHANNEL_CHUNK_VECTOR, 0.0D);
        }
        return new RetrievalStrategy(
                retrievalQuestion,
                effectiveIntent,
                effectiveAnswerShape,
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
        weights.put(CHANNEL_FACT_CARD_FTS, settings.getFactCardWeight());
        weights.put(CHANNEL_FACT_CARD_VECTOR, settings.getFactCardWeight());
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
            multiply(weights, CHANNEL_GRAPH, 1.60D);
            multiply(weights, CHANNEL_SOURCE, 1.25D);
            multiply(weights, CHANNEL_SOURCE_CHUNK_FTS, 1.50D);
            multiply(weights, CHANNEL_ARTICLE_CHUNK_FTS, 1.10D);
            multiply(weights, CHANNEL_FTS, 0.85D);
            multiply(weights, CHANNEL_REFKEY, 0.85D);
        }
        else if (queryIntent == QueryIntent.CONFIGURATION) {
            multiply(weights, CHANNEL_REFKEY, 1.45D);
            multiply(weights, CHANNEL_SOURCE, 1.20D);
            multiply(weights, CHANNEL_SOURCE_CHUNK_FTS, 1.45D);
            multiply(weights, CHANNEL_FACT_CARD_FTS, 1.15D);
            multiply(weights, CHANNEL_ARTICLE_CHUNK_FTS, 0.90D);
            multiply(weights, CHANNEL_FTS, 0.85D);
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
     * 根据答案形态调整结构化证据通道权重。
     *
     * @param weights 通道权重
     * @param answerShape 答案形态
     */
    private void applyAnswerShapeBoost(Map<String, Double> weights, AnswerShape answerShape) {
        if (!isStructuredAnswerShape(answerShape)) {
            return;
        }
        multiply(weights, CHANNEL_FACT_CARD_FTS, 1.65D);
        multiply(weights, CHANNEL_FACT_CARD_VECTOR, 1.25D);
        multiply(weights, CHANNEL_SOURCE_CHUNK_FTS, 1.45D);
        multiply(weights, CHANNEL_SOURCE, 1.15D);
        multiply(weights, CHANNEL_ARTICLE_CHUNK_FTS, 0.65D);
        multiply(weights, CHANNEL_FTS, 0.60D);
        multiply(weights, CHANNEL_ARTICLE_VECTOR, 0.45D);
        multiply(weights, CHANNEL_CHUNK_VECTOR, 0.60D);
    }

    /**
     * 判断答案形态是否属于结构化事实题。
     *
     * @param answerShape 答案形态
     * @return 结构化事实题返回 true
     */
    private boolean isStructuredAnswerShape(AnswerShape answerShape) {
        return answerShape == AnswerShape.ENUM
                || answerShape == AnswerShape.COMPARE
                || answerShape == AnswerShape.SEQUENCE
                || answerShape == AnswerShape.STATUS
                || answerShape == AnswerShape.POLICY;
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
        multiply(weights, CHANNEL_ARTICLE_CHUNK_FTS, 1.15D);
        multiply(weights, CHANNEL_ARTICLE_VECTOR, 1.25D);
        multiply(weights, CHANNEL_CHUNK_VECTOR, 1.20D);
    }

    /**
     * 对精确标识查值题收敛背景通道，优先保留可直接复核的结构化证据。
     *
     * @param weights 通道权重
     * @param retrievalQuestion 有效检索问题
     * @param queryIntent 查询意图
     * @param answerShape 答案形态
     */
    private void applyExactLookupFocus(
            Map<String, Double> weights,
            String retrievalQuestion,
            QueryIntent queryIntent,
            AnswerShape answerShape
    ) {
        if (!hasExactIdentifierSignal(retrievalQuestion) || queryIntent == QueryIntent.CODE_STRUCTURE) {
            return;
        }
        multiply(weights, CHANNEL_REFKEY, 1.20D);
        multiply(weights, CHANNEL_SOURCE, 1.15D);
        multiply(weights, CHANNEL_SOURCE_CHUNK_FTS, 1.25D);
        multiply(weights, CHANNEL_FACT_CARD_FTS, 1.25D);
        multiply(weights, CHANNEL_ARTICLE_CHUNK_FTS, 0.70D);
        multiply(weights, CHANNEL_FTS, 0.75D);
        if (isStructuredAnswerShape(answerShape) || queryIntent == QueryIntent.CONFIGURATION) {
            multiply(weights, CHANNEL_ARTICLE_VECTOR, 0.30D);
            multiply(weights, CHANNEL_CHUNK_VECTOR, 0.35D);
            multiply(weights, CHANNEL_FACT_CARD_VECTOR, 0.70D);
        }
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
            AnswerShape answerShape,
            QueryRetrievalSettingsState settings
    ) {
        if (!settings.isIntentAwareVectorEnabled()) {
            return true;
        }
        if (queryIntent == QueryIntent.CODE_STRUCTURE || queryIntent == QueryIntent.CONFIGURATION) {
            return false;
        }
        if (hasExactIdentifierSignal(retrievalQuestion) && isStructuredAnswerShape(answerShape)) {
            return false;
        }
        return hasSemanticSignal(retrievalQuestion);
    }

    /**
     * 判断精确查值题是否应关闭图谱通道。
     *
     * @param retrievalQuestion 有效检索问题
     * @param queryIntent 查询意图
     * @return 关闭返回 true
     */
    private boolean shouldDisableGraphForExactLookup(String retrievalQuestion, QueryIntent queryIntent) {
        if (queryIntent == QueryIntent.CODE_STRUCTURE || queryIntent == QueryIntent.ARCHITECTURE) {
            return false;
        }
        for (String token : QueryEvidenceRelevanceSupport.extractHighSignalTokens(retrievalQuestion)) {
            if (containsExactIdentifierSignal(token)) {
                return true;
            }
        }
        return false;
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

    /**
     * 判断问题是否包含结构化精确标识。
     *
     * @param retrievalQuestion 有效检索问题
     * @return 包含返回 true
     */
    private boolean hasExactIdentifierSignal(String retrievalQuestion) {
        for (String token : QueryEvidenceRelevanceSupport.extractHighSignalTokens(retrievalQuestion)) {
            if (containsExactIdentifierSignal(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 token 是否包含精确标识符信号。
     *
     * @param token token
     * @return 包含返回 true
     */
    private boolean containsExactIdentifierSignal(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return token.contains("_")
                || token.contains("-")
                || token.contains("=")
                || token.contains("/")
                || token.contains(".");
    }
}
