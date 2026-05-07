package com.xbk.lattice.query.service;

import java.util.List;

/**
 * 检索通道
 *
 * 职责：把单个召回通道抽象为可被 dispatcher 顺序或并发调度的执行单元
 *
 * @author xiexu
 */
public interface RetrievalChannel {

    /**
     * 返回通道名称。
     *
     * @return 通道名称
     */
    String getChannelName();

    /**
     * 判断当前通道是否启用。
     *
     * @param executionContext 检索执行上下文
     * @return 是否启用
     */
    boolean isEnabled(RetrievalExecutionContext executionContext);

    /**
     * 返回通道分组。
     *
     * @return 通道分组
     */
    default String getChannelGroup() {
        return "default";
    }

    /**
     * 执行检索。
     *
     * @param executionContext 检索执行上下文
     * @return 通道命中
     */
    List<QueryArticleHit> search(RetrievalExecutionContext executionContext);
}
