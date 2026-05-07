package com.xbk.lattice.query.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xbk.lattice.query.evidence.domain.AnswerShape;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 检索策略
 *
 * 职责：封装单次查询的有效检索问题、通道启停、通道权重与 RRF 参数
 *
 * @author xiexu
 */
public class RetrievalStrategy {

    private final String retrievalQuestion;

    private final QueryIntent queryIntent;

    private final AnswerShape answerShape;

    private final boolean parallelEnabled;

    private final int rrfK;

    private final Map<String, Double> channelWeights;

    private final Set<String> enabledChannels;

    /**
     * 创建检索策略。
     *
     * @param retrievalQuestion 有效检索问题
     * @param queryIntent 查询意图
     * @param answerShape 答案形态
     * @param parallelEnabled 是否并行召回
     * @param rrfK RRF K 值
     * @param channelWeights 通道权重
     * @param enabledChannels 启用通道
     */
    @JsonCreator
    public RetrievalStrategy(
            @JsonProperty("retrievalQuestion") String retrievalQuestion,
            @JsonProperty("queryIntent") QueryIntent queryIntent,
            @JsonProperty("answerShape") AnswerShape answerShape,
            @JsonProperty("parallelEnabled") boolean parallelEnabled,
            @JsonProperty("rrfK") int rrfK,
            @JsonProperty("channelWeights") Map<String, Double> channelWeights,
            @JsonProperty("enabledChannels") Set<String> enabledChannels
    ) {
        this.retrievalQuestion = retrievalQuestion;
        this.queryIntent = queryIntent == null ? QueryIntent.GENERAL : queryIntent;
        this.answerShape = answerShape == null ? AnswerShape.GENERAL : answerShape;
        this.parallelEnabled = parallelEnabled;
        this.rrfK = rrfK;
        this.channelWeights = channelWeights == null
                ? new LinkedHashMap<String, Double>()
                : new LinkedHashMap<String, Double>(channelWeights);
        this.enabledChannels = enabledChannels == null
                ? new LinkedHashSet<String>()
                : new LinkedHashSet<String>(enabledChannels);
    }

    /**
     * 创建检索策略。
     *
     * @param retrievalQuestion 有效检索问题
     * @param queryIntent 查询意图
     * @param parallelEnabled 是否并行召回
     * @param rrfK RRF K 值
     * @param channelWeights 通道权重
     * @param enabledChannels 启用通道
     */
    public RetrievalStrategy(
            String retrievalQuestion,
            QueryIntent queryIntent,
            boolean parallelEnabled,
            int rrfK,
            Map<String, Double> channelWeights,
            Set<String> enabledChannels
    ) {
        this(
                retrievalQuestion,
                queryIntent,
                AnswerShape.GENERAL,
                parallelEnabled,
                rrfK,
                channelWeights,
                enabledChannels
        );
    }

    /**
     * 返回有效检索问题。
     *
     * @return 有效检索问题
     */
    public String getRetrievalQuestion() {
        return retrievalQuestion;
    }

    /**
     * 返回查询意图。
     *
     * @return 查询意图
     */
    public QueryIntent getQueryIntent() {
        return queryIntent;
    }

    /**
     * 返回答案形态。
     *
     * @return 答案形态
     */
    public AnswerShape getAnswerShape() {
        return answerShape;
    }

    /**
     * 返回是否启用并行召回。
     *
     * @return 是否启用并行召回
     */
    public boolean isParallelEnabled() {
        return parallelEnabled;
    }

    /**
     * 返回 RRF K 值。
     *
     * @return RRF K 值
     */
    public int getRrfK() {
        return rrfK;
    }

    /**
     * 返回通道权重。
     *
     * @return 通道权重
     */
    public Map<String, Double> getChannelWeights() {
        return new LinkedHashMap<String, Double>(channelWeights);
    }

    /**
     * 返回启用通道。
     *
     * @return 启用通道
     */
    public Set<String> getEnabledChannels() {
        return new LinkedHashSet<String>(enabledChannels);
    }

    /**
     * 判断通道是否启用。
     *
     * @param channel 通道名
     * @return 是否启用
     */
    public boolean isChannelEnabled(String channel) {
        return enabledChannels.contains(channel);
    }

    /**
     * 读取通道权重。
     *
     * @param channel 通道名
     * @return 通道权重
     */
    public double weightOf(String channel) {
        return channelWeights.getOrDefault(channel, 0.0D);
    }
}
