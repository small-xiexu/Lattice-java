package com.xbk.lattice.query.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xbk.lattice.query.evidence.domain.AnswerShape;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 检索策略。
 *
 * <p>封装单次查询的完整检索参数，包括有效检索问题、查询意图、答案形态、通道启停、
 * 通道权重和 RRF 融合参数。由 {@link RetrievalStrategyResolver} 根据后台配置、
 * 查询意图和答案形态动态生成，被检索调度器和各通道消费以决定召回行为和融合计算。
 *
 * <p>channelWeights 和 enabledChannels 的 getter 返回防御性拷贝，
 * 防止外部修改影响内部状态。如需修改，必须通过构造器重新创建实例。
 *
 * @author xiexu
 */
public class RetrievalStrategy {

    /**
     * 有效检索问题。
     *
     * <p>经过 Query Rewrite 改写后的标准化查询文本。各检索通道以这个文本（而非用户原始输入）
     * 作为检索输入。改写可能包括同义表达补充、查询意图标准化等，目的是让多通道召回拿到更稳定的查询。</p>
     */
    private final String retrievalQuestion;

    /**
     * 查询意图。
     *
     * <p>系统识别的用户查询意图分类，例如 GENERAL（通用问答）、STRUCTURED_QUERY（结构化查值）等。
     * 检索调度器根据意图决定哪些通道参与、是否走结构化查询路径、是否需要向量召回。</p>
     */
    private final QueryIntent queryIntent;

    /**
     * 答案形态。
     *
     * <p>预期的答案结构类型，例如 GENERAL（自由文本）、STRUCTURED（表格/键值对）等。
     * 影响检索通道选择和证据组装策略——结构化查值会优先走 fact card 和 source 通道。</p>
     */
    private final AnswerShape answerShape;

    /**
     * 是否并行执行多路召回。
     *
     * <p>开启后，FTS、Source、Fact Card、Graph、Vector 等通道可以并行发起，
     * 最终由融合器汇总；关闭时按串行方式执行，更利于排查单通道问题。</p>
     */
    private final boolean parallelEnabled;

    /**
     * RRF K 值。
     *
     * <p>控制多通道排名融合的分数衰减速度。值越小，单个通道靠前名次的优势越强；
     * 值越大，不同通道之间的排名差异会被抹平。由后台检索配置决定，最终被
     * RrfFusionService 消费用于计算融合分数。</p>
     */
    private final int rrfK;

    /**
     * 通道权重映射。
     *
     * <p>key 为通道名（如 fts、refkey、source、fact_card、graph、article_vector、chunk_vector 等），
     * value 为对应权重。权重参与加权 RRF 融合计算，值越大，该通道命中的结果越容易在最终排序中前移。
     * 权重为 0 的通道实际上不参与融合。</p>
     */
    private final Map<String, Double> channelWeights;

    /**
     * 启用的通道集合。
     *
     * <p>包含本轮检索实际参与的所有通道名。不在这个集合中的通道不会被检索调度器触发，
     * 即使通道权重映射中有对应键值。启用/禁用由 RetrievalStrategyResolver 根据查询意图、
     * 答案形态和后台配置综合决定。</p>
     */
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
