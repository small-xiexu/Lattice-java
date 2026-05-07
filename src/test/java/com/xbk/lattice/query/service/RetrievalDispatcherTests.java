package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RetrievalDispatcher 测试
 *
 * 职责：验证统一召回 dispatcher 的顺序执行与结果容器行为
 *
 * @author xiexu
 */
class RetrievalDispatcherTests {

    /**
     * 验证 dispatcher 按计划顺序执行启用通道，并为禁用通道保留空结果。
     */
    @Test
    void shouldDispatchEnabledChannelsSequentiallyAndKeepDisabledChannelKeys() {
        List<String> executionOrder = new ArrayList<String>();
        RetrievalDispatchPlan dispatchPlan = new RetrievalDispatchPlan(List.of(
                new RecordingChannel("first", executionOrder),
                new RecordingChannel("second", executionOrder),
                new RecordingChannel("third", executionOrder)
        ));
        RetrievalExecutionContext executionContext = new RetrievalExecutionContext(
                context(Set.of("first", "third")),
                3
        );

        RetrievalDispatchResult dispatchResult = new RetrievalDispatcher().dispatch(dispatchPlan, executionContext);

        Map<String, List<QueryArticleHit>> channelHits = dispatchResult.getChannelHits();
        assertThat(channelHits.keySet()).containsExactly("first", "second", "third");
        assertThat(channelHits.get("first")).hasSize(1);
        assertThat(channelHits.get("second")).isEmpty();
        assertThat(channelHits.get("third")).hasSize(1);
        assertThat(executionOrder).containsExactly("first", "third");
        assertThat(dispatchResult.getChannelRuns().get("first").getStatus())
                .isEqualTo(RetrievalChannelRunStatus.SUCCESS);
        assertThat(dispatchResult.getChannelRuns().get("second").getStatus())
                .isEqualTo(RetrievalChannelRunStatus.SKIPPED);
    }

    /**
     * 验证单个通道失败时 dispatcher 会保留空命中并继续执行后续通道。
     */
    @Test
    void shouldIsolateFailedChannelAndContinueDispatching() {
        List<String> executionOrder = new ArrayList<String>();
        RetrievalDispatchPlan dispatchPlan = new RetrievalDispatchPlan(List.of(
                new RecordingChannel("lexical", executionOrder),
                new FailingChannel("vector", executionOrder),
                new RecordingChannel("graph", executionOrder)
        ));
        RetrievalExecutionContext executionContext = new RetrievalExecutionContext(
                context(new LinkedHashSet<String>(List.of("lexical", "vector", "graph"))),
                3
        );

        RetrievalDispatchResult dispatchResult = new RetrievalDispatcher().dispatch(dispatchPlan, executionContext);

        assertThat(dispatchResult.getChannelHits().get("lexical")).hasSize(1);
        assertThat(dispatchResult.getChannelHits().get("vector")).isEmpty();
        assertThat(dispatchResult.getChannelHits().get("graph")).hasSize(1);
        assertThat(executionOrder).containsExactly("lexical", "vector", "graph");
        assertThat(dispatchResult.getChannelRuns().get("vector").getStatus())
                .isEqualTo(RetrievalChannelRunStatus.FAILED);
        assertThat(dispatchResult.getChannelRuns().get("vector").getErrorSummary())
                .contains("IllegalStateException");
    }

    /**
     * 验证同一次 dispatcher 执行内可共享 query embedding。
     */
    @Test
    void shouldShareQueryEmbeddingAcrossVectorChannels() {
        CountingConfiguredVectorEmbeddingService embeddingService =
                new CountingConfiguredVectorEmbeddingService();
        RetrievalDispatchPlan dispatchPlan = new RetrievalDispatchPlan(List.of(
                new EmbeddingAwareChannel(RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR, embeddingService),
                new EmbeddingAwareChannel(RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR, embeddingService),
                new EmbeddingAwareChannel(RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR, embeddingService)
        ));
        RetrievalExecutionContext executionContext = new RetrievalExecutionContext(
                context(new LinkedHashSet<String>(List.of(
                        RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR,
                        RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR,
                        RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR
                ))),
                3
        );

        RetrievalDispatchResult dispatchResult = new RetrievalDispatcher().dispatch(dispatchPlan, executionContext);

        assertThat(dispatchResult.getChannelHits().keySet()).containsExactly(
                RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR,
                RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR,
                RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR
        );
        assertThat(embeddingService.getCallCount()).isEqualTo(1);
    }

    /**
     * 验证并发模式下慢通道超时不会阻塞其他通道返回。
     */
    @Test
    void shouldTimeoutSlowChannelAndKeepOtherParallelHits() {
        List<String> executionOrder = new ArrayList<String>();
        RetrievalDispatchPlan dispatchPlan = new RetrievalDispatchPlan(
                List.of(
                        new SlowChannel("slow_vector", "vector", 300L, executionOrder),
                        new RecordingChannel("lexical", "lexical", executionOrder),
                        new RecordingChannel("graph", "graph", executionOrder)
                ),
                true,
                3,
                2,
                80L,
                500L
        );
        RetrievalExecutionContext executionContext = new RetrievalExecutionContext(
                context(new LinkedHashSet<String>(List.of("slow_vector", "lexical", "graph")), true),
                3
        );

        RetrievalDispatchResult dispatchResult = new RetrievalDispatcher().dispatch(dispatchPlan, executionContext);

        assertThat(dispatchResult.getChannelHits().keySet()).containsExactly("slow_vector", "lexical", "graph");
        assertThat(dispatchResult.getChannelHits().get("slow_vector")).isEmpty();
        assertThat(dispatchResult.getChannelHits().get("lexical")).hasSize(1);
        assertThat(dispatchResult.getChannelHits().get("graph")).hasSize(1);
        assertThat(dispatchResult.getChannelRuns().get("slow_vector").getStatus())
                .isEqualTo(RetrievalChannelRunStatus.TIMEOUT);
        assertThat(dispatchResult.getChannelRuns().get("lexical").getStatus())
                .isEqualTo(RetrievalChannelRunStatus.SUCCESS);
        assertThat(dispatchResult.getChannelRuns().get("graph").getStatus())
                .isEqualTo(RetrievalChannelRunStatus.SUCCESS);
    }

    /**
     * 验证并发模式按通道分组限制同组并发。
     */
    @Test
    void shouldLimitParallelExecutionWithinChannelGroup() {
        AtomicInteger activeVectorCount = new AtomicInteger();
        AtomicInteger maxActiveVectorCount = new AtomicInteger();
        RetrievalDispatchPlan dispatchPlan = new RetrievalDispatchPlan(
                List.of(
                        new GroupCountingChannel("vector_one", activeVectorCount, maxActiveVectorCount),
                        new GroupCountingChannel("vector_two", activeVectorCount, maxActiveVectorCount),
                        new GroupCountingChannel("vector_three", activeVectorCount, maxActiveVectorCount)
                ),
                true,
                3,
                1,
                500L,
                1_000L
        );
        RetrievalExecutionContext executionContext = new RetrievalExecutionContext(
                context(new LinkedHashSet<String>(List.of("vector_one", "vector_two", "vector_three")), true),
                3
        );

        RetrievalDispatchResult dispatchResult = new RetrievalDispatcher().dispatch(dispatchPlan, executionContext);

        assertThat(dispatchResult.getChannelRuns().values())
                .extracting(RetrievalChannelRun::getStatus)
                .containsOnly(RetrievalChannelRunStatus.SUCCESS);
        assertThat(maxActiveVectorCount.get()).isEqualTo(1);
    }

    /**
     * 构建固定检索上下文。
     *
     * @param enabledChannels 启用通道
     * @return 检索上下文
     */
    private RetrievalQueryContext context(Set<String> enabledChannels) {
        return context(enabledChannels, false);
    }

    /**
     * 构建固定检索上下文。
     *
     * @param enabledChannels 启用通道
     * @param parallelEnabled 是否并行
     * @return 检索上下文
     */
    private RetrievalQueryContext context(Set<String> enabledChannels, boolean parallelEnabled) {
        Map<String, Double> weights = new LinkedHashMap<String, Double>();
        for (String enabledChannel : enabledChannels) {
            weights.put(enabledChannel, 1.0D);
        }
        RetrievalStrategy retrievalStrategy = new RetrievalStrategy(
                "payment timeout",
                QueryIntent.GENERAL,
                parallelEnabled,
                60,
                weights,
                new LinkedHashSet<String>(enabledChannels)
        );
        return new RetrievalQueryContext(
                "query-1",
                "payment timeout",
                "payment timeout",
                QueryRewriteResult.unchanged("payment timeout"),
                QueryIntent.GENERAL,
                retrievalStrategy
        );
    }

    /**
     * 记录执行顺序的测试通道。
     *
     * @author xiexu
     */
    private static class RecordingChannel implements RetrievalChannel {

        private final String channelName;

        private final List<String> executionOrder;

        private final String channelGroup;

        /**
         * 创建记录通道。
         *
         * @param channelName 通道名
         * @param executionOrder 执行顺序
         */
        private RecordingChannel(String channelName, List<String> executionOrder) {
            this(channelName, "default", executionOrder);
        }

        /**
         * 创建记录通道。
         *
         * @param channelName 通道名
         * @param channelGroup 通道分组
         * @param executionOrder 执行顺序
         */
        private RecordingChannel(String channelName, String channelGroup, List<String> executionOrder) {
            this.channelName = channelName;
            this.channelGroup = channelGroup;
            this.executionOrder = executionOrder;
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
            return executionContext.getRetrievalStrategy().isChannelEnabled(channelName);
        }

        /**
         * 执行检索。
         *
         * @param executionContext 检索执行上下文
         * @return 通道命中
         */
        @Override
        public List<QueryArticleHit> search(RetrievalExecutionContext executionContext) {
            executionOrder.add(channelName);
            return List.of(new QueryArticleHit(
                    channelName,
                    channelName,
                    "content",
                    "{}",
                    List.of(channelName + ".md"),
                    1.0D
            ));
        }
    }

    /**
     * 固定慢通道。
     *
     * @author xiexu
     */
    private static class SlowChannel extends RecordingChannel {

        private final long sleepMillis;

        /**
         * 创建固定慢通道。
         *
         * @param channelName 通道名
         * @param channelGroup 通道分组
         * @param sleepMillis 睡眠毫秒
         * @param executionOrder 执行顺序
         */
        private SlowChannel(
                String channelName,
                String channelGroup,
                long sleepMillis,
                List<String> executionOrder
        ) {
            super(channelName, channelGroup, executionOrder);
            this.sleepMillis = sleepMillis;
        }

        /**
         * 睡眠后返回固定命中。
         *
         * @param executionContext 检索执行上下文
         * @return 固定命中
         */
        @Override
        public List<QueryArticleHit> search(RetrievalExecutionContext executionContext) {
            try {
                Thread.sleep(sleepMillis);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return List.of();
            }
            return super.search(executionContext);
        }
    }

    /**
     * 记录同组并发数的测试通道。
     *
     * @author xiexu
     */
    private static class GroupCountingChannel implements RetrievalChannel {

        private final String channelName;

        private final AtomicInteger activeCount;

        private final AtomicInteger maxActiveCount;

        /**
         * 创建同组并发计数通道。
         *
         * @param channelName 通道名称
         * @param activeCount 当前活跃数
         * @param maxActiveCount 最大活跃数
         */
        private GroupCountingChannel(
                String channelName,
                AtomicInteger activeCount,
                AtomicInteger maxActiveCount
        ) {
            this.channelName = channelName;
            this.activeCount = activeCount;
            this.maxActiveCount = maxActiveCount;
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
         * 返回向量通道分组。
         *
         * @return 向量分组
         */
        @Override
        public String getChannelGroup() {
            return "vector";
        }

        /**
         * 判断通道是否启用。
         *
         * @param executionContext 检索执行上下文
         * @return 是否启用
         */
        @Override
        public boolean isEnabled(RetrievalExecutionContext executionContext) {
            return executionContext.getRetrievalStrategy().isChannelEnabled(channelName);
        }

        /**
         * 记录同组并发数后返回固定命中。
         *
         * @param executionContext 检索执行上下文
         * @return 固定命中
         */
        @Override
        public List<QueryArticleHit> search(RetrievalExecutionContext executionContext) {
            int current = activeCount.incrementAndGet();
            maxActiveCount.updateAndGet(existing -> Math.max(existing, current));
            try {
                Thread.sleep(40L);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return List.of();
            }
            finally {
                activeCount.decrementAndGet();
            }
            return List.of(new QueryArticleHit(
                    channelName,
                    channelName,
                    "content",
                    "{}",
                    List.of(channelName + ".md"),
                    1.0D
            ));
        }
    }

    /**
     * 固定失败的测试通道。
     *
     * @author xiexu
     */
    private static class FailingChannel implements RetrievalChannel {

        private final String channelName;

        private final List<String> executionOrder;

        /**
         * 创建固定失败通道。
         *
         * @param channelName 通道名称
         * @param executionOrder 执行顺序记录
         */
        private FailingChannel(String channelName, List<String> executionOrder) {
            this.channelName = channelName;
            this.executionOrder = executionOrder;
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
         * 判断通道是否启用。
         *
         * @param executionContext 检索执行上下文
         * @return 是否启用
         */
        @Override
        public boolean isEnabled(RetrievalExecutionContext executionContext) {
            return executionContext.getRetrievalStrategy().isChannelEnabled(channelName);
        }

        /**
         * 记录执行顺序后抛出固定异常。
         *
         * @param executionContext 检索执行上下文
         * @return 不返回
         */
        @Override
        public List<QueryArticleHit> search(RetrievalExecutionContext executionContext) {
            executionOrder.add(channelName);
            throw new IllegalStateException("vector provider timeout");
        }
    }

    /**
     * 会读取共享 query embedding 的测试通道。
     *
     * @author xiexu
     */
    private static class EmbeddingAwareChannel implements RetrievalChannel {

        private final String channelName;

        private final CountingConfiguredVectorEmbeddingService embeddingService;

        /**
         * 创建测试通道。
         *
         * @param channelName 通道名称
         * @param embeddingService embedding 服务
         */
        private EmbeddingAwareChannel(
                String channelName,
                CountingConfiguredVectorEmbeddingService embeddingService
        ) {
            this.channelName = channelName;
            this.embeddingService = embeddingService;
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
         * 判断通道是否启用。
         *
         * @param executionContext 检索执行上下文
         * @return 是否启用
         */
        @Override
        public boolean isEnabled(RetrievalExecutionContext executionContext) {
            return executionContext.getRetrievalStrategy().isChannelEnabled(channelName);
        }

        /**
         * 读取共享 embedding 并返回固定命中。
         *
         * @param executionContext 检索执行上下文
         * @return 固定命中
         */
        @Override
        public List<QueryArticleHit> search(RetrievalExecutionContext executionContext) {
            float[] embedding = executionContext.getOrCreateQueryEmbedding(embeddingService);
            return List.of(new QueryArticleHit(
                    channelName,
                    channelName,
                    "embedding-dimensions=" + embedding.length,
                    "{}",
                    List.of(channelName + ".md"),
                    1.0D
            ));
        }
    }

    /**
     * 计数 embedding 服务替身。
     *
     * @author xiexu
     */
    private static class CountingConfiguredVectorEmbeddingService extends ConfiguredVectorEmbeddingService {

        private int callCount;

        /**
         * 创建计数 embedding 服务。
         */
        private CountingConfiguredVectorEmbeddingService() {
            super(new QuerySearchProperties(), null);
        }

        /**
         * 返回 embedding 能力可用。
         *
         * @return true
         */
        @Override
        public boolean isAvailable() {
            return true;
        }

        /**
         * 返回固定模型名。
         *
         * @return 模型名
         */
        @Override
        public String getConfiguredModelName() {
            return "test-shared-embedding";
        }

        /**
         * 返回固定维度。
         *
         * @return 维度
         */
        @Override
        public int getConfiguredExpectedDimensions() {
            return 3;
        }

        /**
         * 生成固定 embedding 并计数。
         *
         * @param text 原始文本
         * @return 固定 embedding
         */
        @Override
        public float[] embed(String text) {
            callCount++;
            return new float[]{0.1F, 0.2F, 0.3F};
        }

        /**
         * 返回 embedding 调用次数。
         *
         * @return 调用次数
         */
        private int getCallCount() {
            return callCount;
        }
    }
}
