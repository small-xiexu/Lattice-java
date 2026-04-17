package com.xbk.lattice.query.graph;

import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 问答图生命周期监听器
 *
 * 职责：为 Query Graph 输出节点级执行日志
 *
 * @author xiexu
 */
@Slf4j
@Component
@Profile("jdbc")
public class QueryGraphLifecycleListener implements GraphLifecycleListener {

    private final QueryGraphStateMapper queryGraphStateMapper;

    /**
     * 创建问答图生命周期监听器。
     *
     * @param queryGraphStateMapper 问答图状态映射器
     */
    public QueryGraphLifecycleListener(QueryGraphStateMapper queryGraphStateMapper) {
        this.queryGraphStateMapper = queryGraphStateMapper;
    }

    /**
     * 记录节点执行前事件。
     *
     * @param nodeId 节点标识
     * @param state 当前状态
     * @param config 运行配置
     * @param curTime 当前时间
     */
    @Override
    public void before(String nodeId, Map<String, Object> state, RunnableConfig config, Long curTime) {
        QueryGraphState queryGraphState = queryGraphStateMapper.fromMap(state);
        log.info(
                "Query graph step start queryId: {}, node: {}, question: {}",
                queryGraphState.getQueryId(),
                nodeId,
                queryGraphState.getNormalizedQuestion()
        );
    }

    /**
     * 记录节点执行后事件。
     *
     * @param nodeId 节点标识
     * @param state 当前状态
     * @param config 运行配置
     * @param curTime 当前时间
     */
    @Override
    public void after(String nodeId, Map<String, Object> state, RunnableConfig config, Long curTime) {
        QueryGraphState queryGraphState = queryGraphStateMapper.fromMap(state);
        log.info(
                "Query graph step finish queryId: {}, node: {}, reviewStatus: {}, rewriteAttemptCount: {}",
                queryGraphState.getQueryId(),
                nodeId,
                queryGraphState.getReviewStatus(),
                queryGraphState.getRewriteAttemptCount()
        );
    }

    /**
     * 记录节点异常事件。
     *
     * @param nodeId 节点标识
     * @param state 当前状态
     * @param ex 异常
     * @param config 运行配置
     */
    @Override
    public void onError(String nodeId, Map<String, Object> state, Throwable ex, RunnableConfig config) {
        QueryGraphState queryGraphState = queryGraphStateMapper.fromMap(state);
        log.warn(
                "Query graph step failed queryId: {}, node: {}, error: {}",
                queryGraphState.getQueryId(),
                nodeId,
                ex == null ? null : ex.getMessage()
        );
    }
}
