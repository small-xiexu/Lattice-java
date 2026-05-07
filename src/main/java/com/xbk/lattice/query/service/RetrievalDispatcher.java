package com.xbk.lattice.query.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 检索调度器
 *
 * 职责：按计划执行各检索通道并保留稳定通道顺序
 *
 * @author xiexu
 */
public class RetrievalDispatcher {

    private static final long DEFAULT_POLL_INTERVAL_MILLIS = 25L;

    /**
     * 顺序执行检索计划。
     *
     * @param dispatchPlan 检索计划
     * @param executionContext 执行上下文
     * @return 调度结果
     */
    public RetrievalDispatchResult dispatch(
            RetrievalDispatchPlan dispatchPlan,
            RetrievalExecutionContext executionContext
    ) {
        Map<String, List<QueryArticleHit>> channelHits = new LinkedHashMap<String, List<QueryArticleHit>>();
        Map<String, RetrievalChannelRun> channelRuns = new LinkedHashMap<String, RetrievalChannelRun>();
        if (dispatchPlan == null) {
            return new RetrievalDispatchResult(channelHits, channelRuns);
        }
        if (dispatchPlan.isParallelEnabled()) {
            return dispatchParallel(dispatchPlan, executionContext);
        }
        for (RetrievalChannel channel : dispatchPlan.getChannels()) {
            if (channel == null) {
                continue;
            }
            String channelName = channel.getChannelName();
            if (!channel.isEnabled(executionContext)) {
                channelHits.put(channelName, List.of());
                channelRuns.put(channelName, RetrievalChannelRun.skipped(channelName, "channel_disabled"));
                continue;
            }
            long startedAt = System.currentTimeMillis();
            try {
                List<QueryArticleHit> hits = channel.search(executionContext);
                List<QueryArticleHit> safeHits = hits == null ? List.of() : hits;
                channelHits.put(channelName, safeHits);
                channelRuns.put(
                        channelName,
                        RetrievalChannelRun.success(channelName, elapsedMillis(startedAt), safeHits.size())
                );
            }
            catch (RuntimeException ex) {
                channelHits.put(channelName, List.of());
                channelRuns.put(
                        channelName,
                        RetrievalChannelRun.failed(channelName, elapsedMillis(startedAt), summarizeError(ex))
                );
            }
        }
        return new RetrievalDispatchResult(channelHits, channelRuns);
    }

    /**
     * 受控并发执行检索计划。
     *
     * @param dispatchPlan 检索计划
     * @param executionContext 执行上下文
     * @return 调度结果
     */
    private RetrievalDispatchResult dispatchParallel(
            RetrievalDispatchPlan dispatchPlan,
            RetrievalExecutionContext executionContext
    ) {
        List<RetrievalChannel> channels = dispatchPlan.getChannels();
        Map<String, List<QueryArticleHit>> channelHits = initializedChannelHits(channels);
        Map<String, RetrievalChannelRun> channelRuns = new LinkedHashMap<String, RetrievalChannelRun>();
        Map<String, ChannelExecutionTask> runningTasks = new LinkedHashMap<String, ChannelExecutionTask>();
        if (channels.isEmpty()) {
            return new RetrievalDispatchResult(channelHits, channelRuns);
        }
        int maxConcurrency = Math.min(dispatchPlan.getMaxConcurrency(), Math.max(channels.size(), 1));
        ExecutorService executorService = Executors.newFixedThreadPool(
                maxConcurrency,
                new RetrievalThreadFactory()
        );
        CompletionService<ChannelExecutionResult> completionService =
                new ExecutorCompletionService<ChannelExecutionResult>(executorService);
        Map<String, Semaphore> groupSemaphores = new LinkedHashMap<String, Semaphore>();
        long startedAt = System.currentTimeMillis();
        long deadlineAt = resolveDeadlineAt(startedAt, dispatchPlan.getTotalDeadlineMillis());
        try {
            for (RetrievalChannel channel : channels) {
                if (channel == null) {
                    continue;
                }
                String channelName = channel.getChannelName();
                if (!channel.isEnabled(executionContext)) {
                    channelRuns.put(channelName, RetrievalChannelRun.skipped(channelName, "channel_disabled"));
                    continue;
                }
                Semaphore groupSemaphore = resolveGroupSemaphore(
                        groupSemaphores,
                        channel.getChannelGroup(),
                        dispatchPlan.getMaxConcurrencyPerGroup()
                );
                ChannelExecutionTask executionTask = new ChannelExecutionTask(
                        channel,
                        executionContext,
                        groupSemaphore,
                        System.currentTimeMillis(),
                        dispatchPlan.getChannelTimeoutMillis()
                );
                Future<ChannelExecutionResult> future = completionService.submit(executionTask);
                executionTask.setFuture(future);
                runningTasks.put(channelName, executionTask);
            }
            waitForRunningTasks(
                    completionService,
                    runningTasks,
                    channelHits,
                    channelRuns,
                    deadlineAt
            );
            return new RetrievalDispatchResult(channelHits, channelRuns);
        }
        finally {
            cancelUnfinishedTasks(runningTasks, channelRuns, channelHits, "dispatcher_shutdown");
            executorService.shutdownNow();
        }
    }

    /**
     * 等待运行中的通道完成。
     *
     * @param completionService 完成队列
     * @param runningTasks 运行中任务
     * @param channelHits 通道命中
     * @param channelRuns 通道运行摘要
     * @param deadlineAt 总截止时间戳
     */
    private void waitForRunningTasks(
            CompletionService<ChannelExecutionResult> completionService,
            Map<String, ChannelExecutionTask> runningTasks,
            Map<String, List<QueryArticleHit>> channelHits,
            Map<String, RetrievalChannelRun> channelRuns,
            long deadlineAt
    ) {
        while (!runningTasks.isEmpty()) {
            markTimedOutTasks(runningTasks, channelHits, channelRuns);
            if (deadlineReached(deadlineAt)) {
                cancelUnfinishedTasks(runningTasks, channelRuns, channelHits, "total_deadline_reached");
                return;
            }
            Future<ChannelExecutionResult> completedFuture = null;
            try {
                completedFuture = pollCompletedFuture(completionService, deadlineAt);
                if (completedFuture == null) {
                    continue;
                }
                if (runningTasksByFuture(completedFuture, runningTasks) == null) {
                    continue;
                }
                ChannelExecutionResult executionResult = completedFuture.get();
                runningTasks.remove(executionResult.getChannelName());
                channelHits.put(executionResult.getChannelName(), executionResult.getHits());
                channelRuns.put(executionResult.getChannelName(), executionResult.toChannelRun());
            }
            catch (CancellationException ex) {
                removeCancelledTask(completedFuture, runningTasks);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                cancelUnfinishedTasks(runningTasks, channelRuns, channelHits, "dispatcher_interrupted");
                return;
            }
            catch (Exception ex) {
                removeFailedFuture(completedFuture, runningTasks, channelRuns, channelHits, ex);
            }
        }
    }

    /**
     * 标记已超过单通道超时的任务。
     *
     * @param runningTasks 运行中任务
     * @param channelHits 通道命中
     * @param channelRuns 通道运行摘要
     */
    private void markTimedOutTasks(
            Map<String, ChannelExecutionTask> runningTasks,
            Map<String, List<QueryArticleHit>> channelHits,
            Map<String, RetrievalChannelRun> channelRuns
    ) {
        List<String> timedOutChannels = new java.util.ArrayList<String>();
        for (ChannelExecutionTask executionTask : runningTasks.values()) {
            if (!executionTask.isTimedOut()) {
                continue;
            }
            Future<ChannelExecutionResult> future = executionTask.getFuture();
            if (future != null) {
                future.cancel(true);
            }
            String channelName = executionTask.getChannelName();
            channelHits.put(channelName, List.of());
            channelRuns.put(
                    channelName,
                    RetrievalChannelRun.timeout(
                            channelName,
                            elapsedMillis(executionTask.getStartedAt()),
                            executionTask.getTimeoutMillis()
                    )
            );
            timedOutChannels.add(channelName);
        }
        for (String timedOutChannel : timedOutChannels) {
            runningTasks.remove(timedOutChannel);
        }
    }

    /**
     * 取消未完成任务。
     *
     * @param runningTasks 运行中任务
     * @param channelRuns 通道运行摘要
     * @param channelHits 通道命中
     * @param reason 取消原因
     */
    private void cancelUnfinishedTasks(
            Map<String, ChannelExecutionTask> runningTasks,
            Map<String, RetrievalChannelRun> channelRuns,
            Map<String, List<QueryArticleHit>> channelHits,
            String reason
    ) {
        List<String> cancelledChannels = new java.util.ArrayList<String>();
        for (ChannelExecutionTask executionTask : runningTasks.values()) {
            Future<ChannelExecutionResult> future = executionTask.getFuture();
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
            String channelName = executionTask.getChannelName();
            channelHits.put(channelName, List.of());
            if (!channelRuns.containsKey(channelName)) {
                RetrievalChannelRun channelRun = createCancelledRun(channelName, executionTask, reason);
                channelRuns.put(channelName, channelRun);
            }
            cancelledChannels.add(channelName);
        }
        for (String cancelledChannel : cancelledChannels) {
            runningTasks.remove(cancelledChannel);
        }
    }

    /**
     * 等待一个已完成 Future。
     *
     * @param completionService 完成队列
     * @param deadlineAt 总截止时间戳
     * @return 完成 Future
     */
    private Future<ChannelExecutionResult> pollCompletedFuture(
            CompletionService<ChannelExecutionResult> completionService,
            long deadlineAt
    ) throws InterruptedException {
        long waitMillis = resolvePollWaitMillis(deadlineAt);
        return completionService.poll(waitMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 移除被取消的任务。
     *
     * @param completedFuture 已完成 Future
     * @param runningTasks 运行中任务
     */
    private void removeCancelledTask(
            Future<ChannelExecutionResult> completedFuture,
            Map<String, ChannelExecutionTask> runningTasks
    ) {
        ChannelExecutionTask executionTask = runningTasksByFuture(completedFuture, runningTasks);
        if (executionTask != null) {
            runningTasks.remove(executionTask.getChannelName());
        }
    }

    /**
     * 记录 Future 执行失败。
     *
     * @param completedFuture 已完成 Future
     * @param runningTasks 运行中任务
     * @param channelRuns 通道运行摘要
     * @param channelHits 通道命中
     * @param ex 异常
     */
    private void removeFailedFuture(
            Future<ChannelExecutionResult> completedFuture,
            Map<String, ChannelExecutionTask> runningTasks,
            Map<String, RetrievalChannelRun> channelRuns,
            Map<String, List<QueryArticleHit>> channelHits,
            Exception ex
    ) {
        ChannelExecutionTask executionTask = runningTasksByFuture(completedFuture, runningTasks);
        if (executionTask == null) {
            return;
        }
        String channelName = executionTask.getChannelName();
        runningTasks.remove(channelName);
        channelHits.put(channelName, List.of());
        channelRuns.put(
                channelName,
                RetrievalChannelRun.failed(channelName, elapsedMillis(executionTask.getStartedAt()), summarizeError(ex))
        );
    }

    /**
     * 初始化通道命中容器。
     *
     * @param channels 通道列表
     * @return 通道命中容器
     */
    private Map<String, List<QueryArticleHit>> initializedChannelHits(List<RetrievalChannel> channels) {
        Map<String, List<QueryArticleHit>> channelHits = new LinkedHashMap<String, List<QueryArticleHit>>();
        for (RetrievalChannel channel : channels) {
            if (channel != null) {
                channelHits.put(channel.getChannelName(), List.of());
            }
        }
        return channelHits;
    }

    /**
     * 创建被取消任务的运行摘要。
     *
     * @param channelName 通道名称
     * @param executionTask 执行任务
     * @param reason 原因
     * @return 运行摘要
     */
    private RetrievalChannelRun createCancelledRun(
            String channelName,
            ChannelExecutionTask executionTask,
            String reason
    ) {
        long durationMillis = elapsedMillis(executionTask.getStartedAt());
        if ("total_deadline_reached".equals(reason)) {
            return RetrievalChannelRun.timeout(
                    channelName,
                    durationMillis,
                    durationMillis,
                    "total_deadline"
            );
        }
        return RetrievalChannelRun.failed(channelName, durationMillis, reason);
    }

    /**
     * 解析分组信号量。
     *
     * @param groupSemaphores 分组信号量
     * @param channelGroup 通道分组
     * @param maxConcurrencyPerGroup 单组最大并发数
     * @return 分组信号量
     */
    private Semaphore resolveGroupSemaphore(
            Map<String, Semaphore> groupSemaphores,
            String channelGroup,
            int maxConcurrencyPerGroup
    ) {
        String safeGroup = channelGroup == null || channelGroup.isBlank() ? "default" : channelGroup.trim();
        if (!groupSemaphores.containsKey(safeGroup)) {
            groupSemaphores.put(safeGroup, new Semaphore(Math.max(maxConcurrencyPerGroup, 1)));
        }
        return groupSemaphores.get(safeGroup);
    }

    /**
     * 解析总截止时间戳。
     *
     * @param startedAt 开始时间
     * @param totalDeadlineMillis 总截止毫秒
     * @return 总截止时间戳
     */
    private long resolveDeadlineAt(long startedAt, long totalDeadlineMillis) {
        if (totalDeadlineMillis <= 0L) {
            return Long.MAX_VALUE;
        }
        return startedAt + totalDeadlineMillis;
    }

    /**
     * 判断是否已到总截止时间。
     *
     * @param deadlineAt 总截止时间戳
     * @return 已到截止时间返回 true
     */
    private boolean deadlineReached(long deadlineAt) {
        return deadlineAt != Long.MAX_VALUE && System.currentTimeMillis() >= deadlineAt;
    }

    /**
     * 计算 poll 等待时间。
     *
     * @param deadlineAt 总截止时间戳
     * @return 等待毫秒
     */
    private long resolvePollWaitMillis(long deadlineAt) {
        if (deadlineAt == Long.MAX_VALUE) {
            return DEFAULT_POLL_INTERVAL_MILLIS;
        }
        long remainingMillis = deadlineAt - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            return 0L;
        }
        return Math.min(DEFAULT_POLL_INTERVAL_MILLIS, remainingMillis);
    }

    /**
     * 按 Future 反查通道名。
     *
     * @param future Future
     * @param runningTasks 运行中任务
     * @return 通道名
     */
    private ChannelExecutionTask runningTasksByFuture(
            Future<ChannelExecutionResult> future,
            Map<String, ChannelExecutionTask> runningTasks
    ) {
        if (future == null) {
            return null;
        }
        for (ChannelExecutionTask executionTask : runningTasks.values()) {
            if (executionTask.getFuture() == future) {
                return executionTask;
            }
        }
        return null;
    }

    /**
     * 计算已耗时毫秒。
     *
     * @param startedAt 开始时间
     * @return 已耗时毫秒
     */
    private long elapsedMillis(long startedAt) {
        return Math.max(System.currentTimeMillis() - startedAt, 0L);
    }

    /**
     * 生成异常摘要。
     *
     * @param ex 异常
     * @return 异常摘要
     */
    private String summarizeError(RuntimeException ex) {
        if (ex == null) {
            return "";
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        String summary = ex.getClass().getSimpleName() + ": " + message;
        if (summary.length() <= 300) {
            return summary;
        }
        return summary.substring(0, 300);
    }

    /**
     * 生成异常摘要。
     *
     * @param ex 异常
     * @return 异常摘要
     */
    private String summarizeError(Exception ex) {
        if (ex instanceof RuntimeException) {
            return summarizeError((RuntimeException) ex);
        }
        if (ex == null) {
            return "";
        }
        Throwable cause = ex.getCause();
        String message = cause == null ? ex.getMessage() : cause.getMessage();
        String summary = ex.getClass().getSimpleName() + ": " + (message == null ? "" : message);
        if (summary.length() <= 300) {
            return summary;
        }
        return summary.substring(0, 300);
    }

    /**
     * 检索线程工厂。
     *
     * @author xiexu
     */
    private static class RetrievalThreadFactory implements ThreadFactory {

        private int nextIndex = 1;

        /**
         * 创建检索线程。
         *
         * @param runnable 执行逻辑
         * @return 检索线程
         */
        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "retrieval-dispatcher-" + nextIndex);
            nextIndex++;
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * 通道执行任务。
     *
     * @author xiexu
     */
    private static class ChannelExecutionTask implements Callable<ChannelExecutionResult> {

        private final RetrievalChannel channel;

        private final RetrievalExecutionContext executionContext;

        private final Semaphore groupSemaphore;

        private final long startedAt;

        private final long timeoutMillis;

        private Future<ChannelExecutionResult> future;

        /**
         * 创建通道执行任务。
         *
         * @param channel 通道
         * @param executionContext 执行上下文
         * @param groupSemaphore 分组信号量
         * @param startedAt 开始时间
         * @param timeoutMillis 超时毫秒
         */
        private ChannelExecutionTask(
                RetrievalChannel channel,
                RetrievalExecutionContext executionContext,
                Semaphore groupSemaphore,
                long startedAt,
                long timeoutMillis
        ) {
            this.channel = channel;
            this.executionContext = executionContext;
            this.groupSemaphore = groupSemaphore;
            this.startedAt = startedAt;
            this.timeoutMillis = timeoutMillis;
        }

        /**
         * 执行通道检索。
         *
         * @return 通道执行结果
         */
        @Override
        public ChannelExecutionResult call() {
            boolean acquired = false;
            try {
                groupSemaphore.acquire();
                acquired = true;
                long actualStartedAt = System.currentTimeMillis();
                List<QueryArticleHit> hits = channel.search(executionContext);
                List<QueryArticleHit> safeHits = hits == null ? List.of() : hits;
                return ChannelExecutionResult.success(
                        channel.getChannelName(),
                        safeHits,
                        Math.max(System.currentTimeMillis() - actualStartedAt, 0L)
                );
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return ChannelExecutionResult.failed(
                        channel.getChannelName(),
                        Math.max(System.currentTimeMillis() - startedAt, 0L),
                        "InterruptedException: " + nullToEmpty(ex.getMessage())
                );
            }
            catch (RuntimeException ex) {
                return ChannelExecutionResult.failed(
                        channel.getChannelName(),
                        Math.max(System.currentTimeMillis() - startedAt, 0L),
                        ex.getClass().getSimpleName() + ": " + nullToEmpty(ex.getMessage())
                );
            }
            finally {
                if (acquired) {
                    groupSemaphore.release();
                }
            }
        }

        /**
         * 设置 Future。
         *
         * @param future Future
         */
        private void setFuture(Future<ChannelExecutionResult> future) {
            this.future = future;
        }

        /**
         * 返回 Future。
         *
         * @return Future
         */
        private Future<ChannelExecutionResult> getFuture() {
            return future;
        }

        /**
         * 返回通道名称。
         *
         * @return 通道名称
         */
        private String getChannelName() {
            return channel.getChannelName();
        }

        /**
         * 返回开始时间。
         *
         * @return 开始时间
         */
        private long getStartedAt() {
            return startedAt;
        }

        /**
         * 返回超时毫秒。
         *
         * @return 超时毫秒
         */
        private long getTimeoutMillis() {
            return timeoutMillis;
        }

        /**
         * 判断任务是否已超时。
         *
         * @return 已超时返回 true
         */
        private boolean isTimedOut() {
            return timeoutMillis > 0L && System.currentTimeMillis() - startedAt >= timeoutMillis;
        }

        /**
         * 返回非空字符串。
         *
         * @param value 原始值
         * @return 非空字符串
         */
        private String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }

    /**
     * 通道执行结果。
     *
     * @author xiexu
     */
    private static class ChannelExecutionResult {

        private final String channelName;

        private final List<QueryArticleHit> hits;

        private final RetrievalChannelRun run;

        /**
         * 创建通道执行结果。
         *
         * @param channelName 通道名称
         * @param hits 命中
         * @param run 运行摘要
         */
        private ChannelExecutionResult(String channelName, List<QueryArticleHit> hits, RetrievalChannelRun run) {
            this.channelName = channelName;
            this.hits = hits == null ? List.of() : hits;
            this.run = run;
        }

        /**
         * 创建成功结果。
         *
         * @param channelName 通道名称
         * @param hits 命中
         * @param durationMillis 耗时毫秒
         * @return 执行结果
         */
        private static ChannelExecutionResult success(
                String channelName,
                List<QueryArticleHit> hits,
                long durationMillis
        ) {
            List<QueryArticleHit> safeHits = hits == null ? List.of() : hits;
            return new ChannelExecutionResult(
                    channelName,
                    safeHits,
                    RetrievalChannelRun.success(channelName, durationMillis, safeHits.size())
            );
        }

        /**
         * 创建失败结果。
         *
         * @param channelName 通道名称
         * @param durationMillis 耗时毫秒
         * @param errorSummary 错误摘要
         * @return 执行结果
         */
        private static ChannelExecutionResult failed(
                String channelName,
                long durationMillis,
                String errorSummary
        ) {
            return new ChannelExecutionResult(
                    channelName,
                    List.of(),
                    RetrievalChannelRun.failed(channelName, durationMillis, errorSummary)
            );
        }

        /**
         * 返回通道名称。
         *
         * @return 通道名称
         */
        private String getChannelName() {
            return channelName;
        }

        /**
         * 返回命中。
         *
         * @return 命中
         */
        private List<QueryArticleHit> getHits() {
            return hits;
        }

        /**
         * 返回运行摘要。
         *
         * @return 运行摘要
         */
        private RetrievalChannelRun toChannelRun() {
            return run;
        }
    }
}
