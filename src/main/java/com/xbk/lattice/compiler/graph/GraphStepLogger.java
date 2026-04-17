package com.xbk.lattice.compiler.graph;

import com.xbk.lattice.infra.persistence.CompileJobStepJdbcRepository;
import com.xbk.lattice.infra.persistence.CompileJobStepRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Graph 步骤日志器
 *
 * 职责：把生命周期回调转换为 compile_job_steps 表中的步骤级执行日志
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class GraphStepLogger {

    private final CompileJobStepJdbcRepository compileJobStepJdbcRepository;

    private final Map<String, AtomicInteger> sequenceCounters = new ConcurrentHashMap<String, AtomicInteger>();

    /**
     * 创建 Graph 步骤日志器。
     *
     * @param compileJobStepJdbcRepository 编译步骤仓储
     */
    public GraphStepLogger(CompileJobStepJdbcRepository compileJobStepJdbcRepository) {
        this.compileJobStepJdbcRepository = compileJobStepJdbcRepository;
    }

    /**
     * 记录步骤开始。
     *
     * @param nodeId 节点名称
     * @param state 编译图状态
     * @param curTime 当前时间戳
     * @return 步骤执行句柄
     */
    public StepExecutionHandle beforeStep(String nodeId, CompileGraphState state, Long curTime) {
        if (state.getJobId() == null || state.getJobId().isBlank()) {
            return null;
        }
        int sequenceNo = sequenceCounters.computeIfAbsent(
                state.getJobId(),
                key -> new AtomicInteger(0)
        ).incrementAndGet();
        StepExecutionHandle handle = new StepExecutionHandle(UUID.randomUUID().toString(), sequenceNo);
        compileJobStepJdbcRepository.createRunningStep(new CompileJobStepRecord(
                state.getJobId(),
                handle.getStepExecutionId(),
                nodeId,
                resolveAgentRole(nodeId),
                resolveModelRoute(nodeId, state),
                sequenceNo,
                "running",
                buildSummary(nodeId, state),
                buildInputSummary(state),
                null,
                null,
                toOffsetDateTime(curTime),
                null
        ));
        return handle;
    }

    /**
     * 记录步骤成功。
     *
     * @param handle 步骤执行句柄
     * @param nodeId 节点名称
     * @param state 编译图状态
     * @param curTime 当前时间戳
     */
    public void afterStep(StepExecutionHandle handle, String nodeId, CompileGraphState state, Long curTime) {
        if (handle == null || state.getJobId() == null || state.getJobId().isBlank()) {
            return;
        }
        compileJobStepJdbcRepository.markSucceeded(
                handle.getStepExecutionId(),
                handle.getSequenceNo(),
                buildSummary(nodeId, state),
                buildOutputSummary(state),
                toOffsetDateTime(curTime)
        );
    }

    /**
     * 记录步骤失败。
     *
     * @param handle 步骤执行句柄
     * @param nodeId 节点名称
     * @param state 编译图状态
     * @param throwable 异常
     */
    public void failStep(StepExecutionHandle handle, String nodeId, CompileGraphState state, Throwable throwable) {
        if (handle == null || state.getJobId() == null || state.getJobId().isBlank()) {
            return;
        }
        compileJobStepJdbcRepository.markFailed(
                handle.getStepExecutionId(),
                handle.getSequenceNo(),
                buildSummary(nodeId, state),
                throwable == null ? null : throwable.getMessage(),
                OffsetDateTime.now()
        );
    }

    /**
     * 清理作业级运行态缓存。
     *
     * @param jobId 作业标识
     */
    public void clearJob(String jobId) {
        sequenceCounters.remove(jobId);
    }

    private String buildSummary(String nodeId, CompileGraphState state) {
        return nodeId + " conceptCount=" + state.getConceptCount()
                + ", pendingReviewCount=" + state.getPendingReviewCount()
                + ", acceptedCount=" + state.getAcceptedCount()
                + ", needsHumanReviewCount=" + state.getNeedsHumanReviewCount()
                + ", persistedCount=" + state.getPersistedCount()
                + ", fixAttemptCount=" + state.getFixAttemptCount()
                + ", nothingToDo=" + state.isNothingToDo();
    }

    private String buildInputSummary(CompileGraphState state) {
        return "mode=" + state.getCompileMode()
                + ", sourceDir=" + state.getSourceDir()
                + ", mergedRef=" + state.getMergedConceptsRef()
                + ", draftRef=" + state.getDraftArticlesRef()
                + ", reviewPartitionRef=" + state.getReviewPartitionRef()
                + ", acceptedRef=" + state.getAcceptedArticlesRef();
    }

    private String buildOutputSummary(CompileGraphState state) {
        return "persistedIds=" + state.getPersistedArticleIds()
                + ", reviewedRef=" + state.getReviewedArticlesRef()
                + ", pendingReviewCount=" + state.getPendingReviewCount()
                + ", acceptedCount=" + state.getAcceptedCount()
                + ", needsHumanReviewCount=" + state.getNeedsHumanReviewCount()
                + ", synthesisRequired=" + state.isSynthesisRequired()
                + ", snapshotRequired=" + state.isSnapshotRequired();
    }

    private String resolveAgentRole(String nodeId) {
        if ("compile_new_articles".equals(nodeId)) {
            return "WriterAgent";
        }
        if ("review_articles".equals(nodeId)) {
            return "ReviewerAgent";
        }
        if ("fix_review_issues".equals(nodeId)) {
            return "FixerAgent";
        }
        return null;
    }

    private String resolveModelRoute(String nodeId, CompileGraphState state) {
        if ("compile_new_articles".equals(nodeId)) {
            return state.getCompileRoute();
        }
        if ("review_articles".equals(nodeId)) {
            return state.getReviewRoute();
        }
        if ("fix_review_issues".equals(nodeId)) {
            return state.getFixRoute();
        }
        return null;
    }

    private OffsetDateTime toOffsetDateTime(Long curTime) {
        long timestamp = curTime == null ? System.currentTimeMillis() : curTime.longValue();
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC);
    }
}
