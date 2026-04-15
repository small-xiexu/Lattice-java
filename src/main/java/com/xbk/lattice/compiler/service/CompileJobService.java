package com.xbk.lattice.compiler.service;

import com.xbk.lattice.infra.persistence.CompileJobJdbcRepository;
import com.xbk.lattice.infra.persistence.CompileJobRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 编译作业服务
 *
 * 职责：负责后台编译作业的提交、执行、查询与重试
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class CompileJobService {

    private final CompileJobJdbcRepository compileJobJdbcRepository;

    private final CompileOrchestratorRegistry compileOrchestratorRegistry;

    /**
     * 创建编译作业服务。
     *
     * @param compileJobJdbcRepository 编译作业仓储
     * @param compileOrchestratorRegistry 编排器注册表
     */
    public CompileJobService(
            CompileJobJdbcRepository compileJobJdbcRepository,
            CompileOrchestratorRegistry compileOrchestratorRegistry
    ) {
        this.compileJobJdbcRepository = compileJobJdbcRepository;
        this.compileOrchestratorRegistry = compileOrchestratorRegistry;
    }

    /**
     * 提交编译作业。
     *
     * @param sourceDir 源目录
     * @param incremental 是否增量编译
     * @param async 是否异步执行
     * @param orchestrationMode 编排模式
     * @return 编译作业记录
     */
    public CompileJobRecord submit(
            String sourceDir,
            boolean incremental,
            boolean async,
            String orchestrationMode
    ) {
        OffsetDateTime requestedAt = OffsetDateTime.now();
        CompileJobRecord compileJobRecord = new CompileJobRecord(
                UUID.randomUUID().toString(),
                sourceDir,
                incremental,
                CompileOrchestrationModes.normalize(orchestrationMode),
                CompileJobStatuses.QUEUED,
                0,
                null,
                0,
                requestedAt,
                null,
                null
        );
        compileJobJdbcRepository.save(compileJobRecord);
        if (async) {
            return getRequiredJob(compileJobRecord.getJobId());
        }
        return executeJob(compileJobRecord.getJobId());
    }

    /**
     * 读取全部编译作业。
     *
     * @return 编译作业列表
     */
    public List<CompileJobRecord> listJobs() {
        return compileJobJdbcRepository.findAll();
    }

    /**
     * 读取单个编译作业。
     *
     * @param jobId 作业标识
     * @return 编译作业
     */
    public CompileJobRecord getJob(String jobId) {
        return getRequiredJob(jobId);
    }

    /**
     * 处理下一条排队中的作业。
     *
     * @return 执行后的作业；无可执行任务时返回空
     */
    public Optional<CompileJobRecord> processNextQueuedJob() {
        Optional<CompileJobRecord> nextQueuedJob = compileJobJdbcRepository.findNextQueued();
        if (nextQueuedJob.isEmpty()) {
            return Optional.empty();
        }
        String jobId = nextQueuedJob.orElseThrow().getJobId();
        OffsetDateTime startedAt = OffsetDateTime.now();
        if (!compileJobJdbcRepository.markRunning(jobId, startedAt)) {
            return Optional.empty();
        }
        return Optional.of(executeRunningJob(jobId));
    }

    /**
     * 重试指定作业。
     *
     * @param jobId 作业标识
     * @return 更新后的作业
     */
    public CompileJobRecord retry(String jobId) {
        CompileJobRecord compileJobRecord = getRequiredJob(jobId);
        if (!CompileJobStatuses.FAILED.equalsIgnoreCase(compileJobRecord.getStatus())) {
            throw new IllegalStateException("only failed job can retry: " + jobId);
        }
        compileJobJdbcRepository.retry(jobId);
        return getRequiredJob(jobId);
    }

    /**
     * 执行指定作业。
     *
     * @param jobId 作业标识
     * @return 执行后的作业
     */
    private CompileJobRecord executeJob(String jobId) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        if (!compileJobJdbcRepository.markRunning(jobId, startedAt)) {
            return getRequiredJob(jobId);
        }
        return executeRunningJob(jobId);
    }

    /**
     * 执行已标记为运行中的作业。
     *
     * @param jobId 作业标识
     * @return 执行后的作业
     */
    private CompileJobRecord executeRunningJob(String jobId) {
        CompileJobRecord compileJobRecord = getRequiredJob(jobId);
        try {
            CompileResult compileResult = compileOrchestratorRegistry.execute(
                    compileJobRecord.getOrchestrationMode(),
                    Path.of(compileJobRecord.getSourceDir()),
                    compileJobRecord.isIncremental()
            );
            compileJobJdbcRepository.markSucceeded(jobId, compileResult.getPersistedCount(), OffsetDateTime.now());
        }
        catch (IOException | RuntimeException ex) {
            compileJobJdbcRepository.markFailed(jobId, ex.getMessage(), OffsetDateTime.now());
        }
        return getRequiredJob(jobId);
    }

    /**
     * 读取必需的作业记录。
     *
     * @param jobId 作业标识
     * @return 作业记录
     */
    private CompileJobRecord getRequiredJob(String jobId) {
        return compileJobJdbcRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("compile job not found: " + jobId));
    }
}
