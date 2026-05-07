package com.xbk.lattice.query.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索调度结果
 *
 * 职责：保存各通道按调度顺序产生的命中集合
 *
 * @author xiexu
 */
public class RetrievalDispatchResult {

    private final Map<String, List<QueryArticleHit>> channelHits;

    private final Map<String, RetrievalChannelRun> channelRuns;

    /**
     * 创建检索调度结果。
     *
     * @param channelHits 分通道命中
     */
    public RetrievalDispatchResult(Map<String, List<QueryArticleHit>> channelHits) {
        this(channelHits, null);
    }

    /**
     * 创建检索调度结果。
     *
     * @param channelHits 分通道命中
     * @param channelRuns 分通道运行摘要
     */
    public RetrievalDispatchResult(
            Map<String, List<QueryArticleHit>> channelHits,
            Map<String, RetrievalChannelRun> channelRuns
    ) {
        this.channelHits = new LinkedHashMap<String, List<QueryArticleHit>>();
        if (channelHits == null) {
            this.channelRuns = copyChannelRuns(channelRuns);
            return;
        }
        for (Map.Entry<String, List<QueryArticleHit>> entry : channelHits.entrySet()) {
            List<QueryArticleHit> hits = entry.getValue() == null ? List.of() : List.copyOf(entry.getValue());
            this.channelHits.put(entry.getKey(), hits);
        }
        this.channelRuns = copyChannelRuns(channelRuns);
    }

    /**
     * 返回分通道命中。
     *
     * @return 分通道命中
     */
    public Map<String, List<QueryArticleHit>> getChannelHits() {
        return new LinkedHashMap<String, List<QueryArticleHit>>(channelHits);
    }

    /**
     * 返回分通道运行摘要。
     *
     * @return 分通道运行摘要
     */
    public Map<String, RetrievalChannelRun> getChannelRuns() {
        return new LinkedHashMap<String, RetrievalChannelRun>(channelRuns);
    }

    /**
     * 复制运行摘要。
     *
     * @param source 原始运行摘要
     * @return 复制后的运行摘要
     */
    private Map<String, RetrievalChannelRun> copyChannelRuns(Map<String, RetrievalChannelRun> source) {
        Map<String, RetrievalChannelRun> copiedChannelRuns =
                new LinkedHashMap<String, RetrievalChannelRun>();
        if (source == null) {
            return copiedChannelRuns;
        }
        copiedChannelRuns.putAll(source);
        return copiedChannelRuns;
    }
}
