package com.xbk.lattice.compiler.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import com.xbk.lattice.infra.persistence.CompileJobJdbcRepository;
import com.xbk.lattice.infra.persistence.CompileJobRecord;
import com.xbk.lattice.observability.StructuredEventLogger;
import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.infra.KnowledgeSourceJdbcRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
@Slf4j
public class CompileJobService {

    private static final String LEGACY_DEFAULT_SOURCE_CODE = "legacy-default";

    private final CompileJobJdbcRepository compileJobJdbcRepository;

    private final CompileOrchestratorRegistry compileOrchestratorRegistry;

    private final KnowledgeSourceJdbcRepository knowledgeSourceJdbcRepository;

    private final StructuredEventLogger structuredEventLogger;

    private final Tracer tracer;

    /**
     * 创建编译作业服务。
     *
     * @param compileJobJdbcRepository 编译作业仓储
     * @param compileOrchestratorRegistry 编排器注册表
     */
    public CompileJobService(
            CompileJobJdbcRepository compileJobJdbcRepository,
            CompileOrchestratorRegistry compileOrchestratorRegistry,
            KnowledgeSourceJdbcRepository knowledgeSourceJdbcRepository,
            StructuredEventLogger structuredEventLogger,
            ObjectProvider<Tracer> tracerProvider
    ) {
        this.compileJobJdbcRepository = compileJobJdbcRepository;
        this.compileOrchestratorRegistry = compileOrchestratorRegistry;
        this.knowledgeSourceJdbcRepository = knowledgeSourceJdbcRepository;
        this.structuredEventLogger = structuredEventLogger;
        this.tracer = tracerProvider.getIfAvailable();
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
        return submit(sourceDir, incremental, async, orchestrationMode, null, null);
    }

    /**
     * 提交编译作业。
     *
     * @param sourceDir 源目录
     * @param incremental 是否增量编译
     * @param async 是否异步执行
     * @param orchestrationMode 编排模式
     * @param sourceId 资料源主键
     * @param sourceSyncRunId 资料源同步运行主键
     * @return 编译作业记录
     */
    public CompileJobRecord submit(
            String sourceDir,
            boolean incremental,
            boolean async,
            String orchestrationMode,
            Long sourceId,
            Long sourceSyncRunId
    ) {
        OffsetDateTime requestedAt = OffsetDateTime.now();
        Long effectiveSourceId = resolveEffectiveSourceId(sourceId);
        String rootTraceId = resolveCurrentRootTraceId();
        CompileJobRecord compileJobRecord = new CompileJobRecord(
                UUID.randomUUID().toString(),
                sourceDir,
                effectiveSourceId,
                sourceSyncRunId,
                rootTraceId,
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
        logCompileSubmitted(compileJobRecord);
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
     * 同步重试指定失败作业。
     *
     * @param jobId 作业标识
     * @return 更新后的作业
     */
    public CompileJobRecord retryNow(String jobId) {
        retry(jobId);
        return executeJob(jobId);
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
        Map<String, String> previousTraceContext = bindTraceContext(compileJobRecord.getRootTraceId());
        logCompileStarted(compileJobRecord);
        try {
            KnowledgeSource knowledgeSource = resolveKnowledgeSource(compileJobRecord.getSourceId());
            CompileExecutionRequest executionRequest = new CompileExecutionRequest(
                    compileJobRecord.getJobId(),
                    Path.of(compileJobRecord.getSourceDir()),
                    compileJobRecord.isIncremental(),
                    compileJobRecord.getOrchestrationMode(),
                    compileJobRecord.getSourceId(),
                    knowledgeSource == null ? null : knowledgeSource.getSourceCode(),
                    compileJobRecord.getSourceSyncRunId()
            );
            CompileResult compileResult = compileOrchestratorRegistry.execute(executionRequest);
            compileJobJdbcRepository.markSucceeded(jobId, compileResult.getPersistedCount(), OffsetDateTime.now());
            CompileJobRecord completedJob = getRequiredJob(jobId);
            logCompileCompleted(completedJob, null);
            return completedJob;
        }
        catch (IOException | RuntimeException ex) {
            log.error("Compile job execution failed jobId: {}, sourceDir: {}", jobId, compileJobRecord.getSourceDir(), ex);
            compileJobJdbcRepository.markFailed(jobId, ex.getMessage(), OffsetDateTime.now());
            CompileJobRecord failedJob = getRequiredJob(jobId);
            logCompileCompleted(failedJob, ex);
            return failedJob;
        }
        finally {
            restoreTraceContext(previousTraceContext);
        }
    }

    private Long resolveEffectiveSourceId(Long sourceId) {
        if (sourceId != null) {
            return sourceId;
        }
        KnowledgeSource legacyDefault = resolveKnowledgeSourceByCode(LEGACY_DEFAULT_SOURCE_CODE);
        return legacyDefault == null ? null : legacyDefault.getId();
    }

    private KnowledgeSource resolveKnowledgeSource(Long sourceId) {
        if (sourceId == null) {
            return resolveKnowledgeSourceByCode(LEGACY_DEFAULT_SOURCE_CODE);
        }
        return knowledgeSourceJdbcRepository.findById(sourceId).orElse(null);
    }

    private KnowledgeSource resolveKnowledgeSourceByCode(String sourceCode) {
        return knowledgeSourceJdbcRepository.findBySourceCode(sourceCode).orElse(null);
    }

    private void logCompileSubmitted(CompileJobRecord compileJobRecord) {
        if (structuredEventLogger == null || compileJobRecord == null) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("scene", "compile");
        fields.put("status", compileJobRecord.getStatus());
        fields.put("compileJobId", compileJobRecord.getJobId());
        fields.put("sourceId", compileJobRecord.getSourceId());
        fields.put("sourceSyncRunId", compileJobRecord.getSourceSyncRunId());
        fields.put("rootTraceId", compileJobRecord.getRootTraceId());
        fields.put("sourceDir", compileJobRecord.getSourceDir());
        fields.put("incremental", compileJobRecord.isIncremental());
        fields.put("orchestrationMode", compileJobRecord.getOrchestrationMode());
        structuredEventLogger.info("compile_submitted", fields);
    }

    /**
     * 记录编译作业开始执行事件。
     *
     * @param compileJobRecord 编译作业记录
     */
    private void logCompileStarted(CompileJobRecord compileJobRecord) {
        if (structuredEventLogger == null || compileJobRecord == null) {
            return;
        }
        Map<String, Object> fields = buildCompileEventFields(compileJobRecord);
        fields.put("status", CompileJobStatuses.RUNNING);
        structuredEventLogger.info("compile_started", fields);
    }

    /**
     * 记录编译作业完成事件。
     *
     * @param compileJobRecord 编译作业记录
     * @param throwable 执行异常
     */
    private void logCompileCompleted(CompileJobRecord compileJobRecord, Throwable throwable) {
        if (structuredEventLogger == null || compileJobRecord == null) {
            return;
        }
        Map<String, Object> fields = buildCompileEventFields(compileJobRecord);
        fields.put("status", compileJobRecord.getStatus());
        fields.put("persistedCount", compileJobRecord.getPersistedCount());
        fields.put("attemptCount", compileJobRecord.getAttemptCount());
        if (compileJobRecord.getErrorMessage() != null && !compileJobRecord.getErrorMessage().isBlank()) {
            fields.put("error", compileJobRecord.getErrorMessage());
        }
        if (throwable != null) {
            structuredEventLogger.error("compile_completed", fields, throwable);
            return;
        }
        structuredEventLogger.info("compile_completed", fields);
    }

    /**
     * 构建编译作业事件字段。
     *
     * @param compileJobRecord 编译作业记录
     * @return 事件字段
     */
    private Map<String, Object> buildCompileEventFields(CompileJobRecord compileJobRecord) {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("scene", "compile");
        fields.put("compileJobId", compileJobRecord.getJobId());
        fields.put("sourceId", compileJobRecord.getSourceId());
        fields.put("sourceSyncRunId", compileJobRecord.getSourceSyncRunId());
        fields.put("rootTraceId", compileJobRecord.getRootTraceId());
        fields.put("sourceDir", compileJobRecord.getSourceDir());
        fields.put("incremental", compileJobRecord.isIncremental());
        fields.put("orchestrationMode", compileJobRecord.getOrchestrationMode());
        return fields;
    }

    /**
     * 解析当前链路的根追踪标识。
     *
     * @return 根追踪标识
     */
    private String resolveCurrentRootTraceId() {
        String rootTraceId = trimToNull(MDC.get("rootTraceId"));
        if (rootTraceId != null) {
            return rootTraceId;
        }
        Tracer currentTracer = this.tracer;
        if (currentTracer != null) {
            Span currentSpan = currentTracer.currentSpan();
            if (currentSpan != null && currentSpan.context() != null) {
                String traceId = trimToNull(currentSpan.context().traceId());
                if (traceId != null) {
                    return traceId;
                }
            }
        }
        return trimToNull(MDC.get("traceId"));
    }

    /**
     * 绑定编译作业执行期的追踪上下文。
     *
     * @param rootTraceId 根追踪标识
     * @return 绑定前的上下文快照
     */
    private Map<String, String> bindTraceContext(String rootTraceId) {
        Map<String, String> previousValues = new LinkedHashMap<String, String>();
        rememberTraceValue(previousValues, "traceId");
        rememberTraceValue(previousValues, "rootTraceId");
        rememberTraceValue(previousValues, "spanId");
        if (rootTraceId == null || rootTraceId.isBlank()) {
            return previousValues;
        }
        MDC.put("traceId", rootTraceId);
        MDC.put("rootTraceId", rootTraceId);
        MDC.remove("spanId");
        return previousValues;
    }

    /**
     * 恢复编译作业执行前的追踪上下文。
     *
     * @param previousValues 旧上下文
     */
    private void restoreTraceContext(Map<String, String> previousValues) {
        if (previousValues == null || previousValues.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : previousValues.entrySet()) {
            if (entry.getValue() == null) {
                MDC.remove(entry.getKey());
            }
            else {
                MDC.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 记录旧的追踪上下文字段。
     *
     * @param previousValues 旧值 Map
     * @param key 字段键
     */
    private void rememberTraceValue(Map<String, String> previousValues, String key) {
        previousValues.put(key, MDC.get(key));
    }

    /**
     * 去掉空白字符串。
     *
     * @param value 原始值
     * @return 标准化后的值
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) {
            return null;
        }
        return trimmedValue;
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
