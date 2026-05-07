package com.xbk.lattice.query.service;

import java.util.List;
import java.util.function.Function;

/**
 * 函数式检索通道
 *
 * 职责：把已有检索服务适配为统一 RetrievalChannel
 *
 * @author xiexu
 */
public class SupplierRetrievalChannel implements RetrievalChannel {

    private final String channelName;

    private final String channelGroup;

    private final Function<RetrievalExecutionContext, List<QueryArticleHit>> searchFunction;

    /**
     * 创建函数式检索通道。
     *
     * @param channelName 通道名称
     * @param searchFunction 检索函数
     */
    public SupplierRetrievalChannel(
            String channelName,
            Function<RetrievalExecutionContext, List<QueryArticleHit>> searchFunction
    ) {
        this(channelName, "default", searchFunction);
    }

    /**
     * 创建函数式检索通道。
     *
     * @param channelName 通道名称
     * @param channelGroup 通道分组
     * @param searchFunction 检索函数
     */
    public SupplierRetrievalChannel(
            String channelName,
            String channelGroup,
            Function<RetrievalExecutionContext, List<QueryArticleHit>> searchFunction
    ) {
        this.channelName = channelName;
        this.channelGroup = channelGroup == null || channelGroup.isBlank()
                ? "default"
                : channelGroup.trim();
        this.searchFunction = searchFunction;
    }

    /**
     * 返回通道名称。
     *
     * @return 通道名称
     */
    @Override
    public String getChannelName() {
        return channelName;
    }

    /**
     * 返回通道分组。
     *
     * @return 通道分组
     */
    @Override
    public String getChannelGroup() {
        return channelGroup;
    }

    /**
     * 判断通道是否启用。
     *
     * @param executionContext 检索执行上下文
     * @return 是否启用
     */
    @Override
    public boolean isEnabled(RetrievalExecutionContext executionContext) {
        RetrievalStrategy retrievalStrategy = executionContext == null
                ? null
                : executionContext.getRetrievalStrategy();
        return retrievalStrategy != null && retrievalStrategy.isChannelEnabled(channelName);
    }

    /**
     * 执行检索。
     *
     * @param executionContext 检索执行上下文
     * @return 通道命中
     */
    @Override
    public List<QueryArticleHit> search(RetrievalExecutionContext executionContext) {
        if (searchFunction == null) {
            return List.of();
        }
        List<QueryArticleHit> hits = searchFunction.apply(executionContext);
        if (hits == null) {
            return List.of();
        }
        RetrievalStrategy retrievalStrategy = executionContext == null
                ? null
                : executionContext.getRetrievalStrategy();
        if (retrievalStrategy == null) {
            return hits;
        }
        return QueryHitIntentReranker.rerank(
                retrievalStrategy.getRetrievalQuestion(),
                retrievalStrategy.getQueryIntent(),
                hits
        );
    }
}
