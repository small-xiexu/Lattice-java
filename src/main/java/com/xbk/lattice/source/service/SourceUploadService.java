package com.xbk.lattice.source.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.admin.service.AdminProcessingTaskPresentation;
import com.xbk.lattice.admin.service.AdminProcessingTaskPresentationResolver;
import com.xbk.lattice.api.admin.AdminProcessingTaskActionResponse;
import com.xbk.lattice.compiler.service.CompileJobService;
import com.xbk.lattice.compiler.service.CompileJobDerivedStatusResolver;
import com.xbk.lattice.compiler.service.CompileJobStatuses;
import com.xbk.lattice.compiler.service.CompileOrchestrationModes;
import com.xbk.lattice.infra.persistence.CompileJobRecord;
import com.xbk.lattice.source.domain.BundleSummary;
import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.domain.SourceDecisionResult;
import com.xbk.lattice.source.domain.SourceSyncRun;
import com.xbk.lattice.source.domain.SourceSyncRunDetail;
import com.xbk.lattice.source.infra.SourceSnapshotJdbcRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 统一上传与自动归并服务。
 *
 * 职责：承载 uploads -> SourceSyncRun -> compile 的 Phase E 主闭环
 *
 * @author xiexu
 */
@Service
public class SourceUploadService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Duration WAIT_CONFIRM_TIMEOUT = Duration.ofDays(7);

    private final BundleFeatureExtractor bundleFeatureExtractor;

    private final SourceDecisionPolicy sourceDecisionPolicy;

    private final SourceService sourceService;

    private final SourceSyncService sourceSyncService;

    private final CompileJobService compileJobService;

    private final CompileJobDerivedStatusResolver compileJobDerivedStatusResolver;

    private final SourceSnapshotJdbcRepository sourceSnapshotJdbcRepository;

    private final AdminProcessingTaskPresentationResolver presentationResolver;

    /**
     * 创建统一上传服务。
     *
     * @param bundleFeatureExtractor bundle 特征提取器
     * @param sourceDecisionPolicy 自动识别策略
     * @param sourceService 资料源服务
     * @param sourceSyncService 同步运行服务
     * @param compileJobService 编译作业服务
     * @param compileJobDerivedStatusResolver 编译作业派生状态解析器
     * @param sourceSnapshotJdbcRepository 资料源快照仓储
     * @param presentationResolver 当前处理任务展示解析器
     */
    public SourceUploadService(
            BundleFeatureExtractor bundleFeatureExtractor,
            SourceDecisionPolicy sourceDecisionPolicy,
            SourceService sourceService,
            SourceSyncService sourceSyncService,
            CompileJobService compileJobService,
            CompileJobDerivedStatusResolver compileJobDerivedStatusResolver,
            SourceSnapshotJdbcRepository sourceSnapshotJdbcRepository,
            AdminProcessingTaskPresentationResolver presentationResolver
    ) {
        this.bundleFeatureExtractor = bundleFeatureExtractor;
        this.sourceDecisionPolicy = sourceDecisionPolicy;
        this.sourceService = sourceService;
        this.sourceSyncService = sourceSyncService;
        this.compileJobService = compileJobService;
        this.compileJobDerivedStatusResolver = compileJobDerivedStatusResolver;
        this.sourceSnapshotJdbcRepository = sourceSnapshotJdbcRepository;
        this.presentationResolver = presentationResolver;
    }

    /**
     * 接收新的上传资料包。
     *
     * @param stagingDir staging 目录
     * @param requestedSourceId 可选的目标资料源主键
     * @return 同步运行详情
     * @throws IOException IO 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public SourceSyncRunDetail acceptUpload(Path stagingDir, Long requestedSourceId) throws IOException {
        BundleFeatureExtractor.UploadBundleSnapshot bundleSnapshot = bundleFeatureExtractor.extract(stagingDir);
        KnowledgeSource requestedSource = requestedSourceId == null ? null : sourceService.findById(requestedSourceId)
                .orElseThrow(() -> new IllegalArgumentException("source not found: " + requestedSourceId));
        if (requestedSource != null) {
            rejectWhenSourceStatusBlocksSync(requestedSource);
            rejectWhenSourceHasActiveRun(requestedSource.getId(), null);
        }
        if (requestedSource == null) {
            java.util.Optional<SourceSyncRun> activeRun = sourceSyncService.findActivePrelockByManifestHash(bundleSnapshot.getManifestHash());
            if (activeRun.isPresent()) {
                return getRunDetail(activeRun.orElseThrow().getId());
            }
        }

        SourceSyncRun run = createInitialRun(bundleSnapshot, requestedSourceId, "UPLOAD", null);
        SourceSyncRun acceptedRun = requestedSource == null
                ? routeAutomaticDecision(run, bundleSnapshot)
                : routeExplicitSource(run, requestedSource, bundleSnapshot, "RULE_ONLY");
        return getRunDetail(acceptedRun.getId());
    }

    /**
     * 接收已物化的资料源目录。
     *
     * @param stagingDir staging 目录
     * @param sourceId 资料源主键
     * @param sourceType 资料源类型
     * @param materializationNode 物化元数据
     * @return 同步运行详情
     * @throws IOException IO 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public SourceSyncRunDetail acceptMaterializedSource(
            Path stagingDir,
            Long sourceId,
            String sourceType,
            JsonNode materializationNode
    ) throws IOException {
        KnowledgeSource source = sourceService.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("source not found: " + sourceId));
        rejectWhenSourceStatusBlocksSync(source);
        rejectWhenSourceHasActiveRun(sourceId, null);
        BundleFeatureExtractor.UploadBundleSnapshot bundleSnapshot = bundleFeatureExtractor.extract(stagingDir);
        SourceSyncRun run = createInitialRun(bundleSnapshot, sourceId, sourceType, materializationNode);
        return getRunDetail(routeExplicitSource(run, source, bundleSnapshot, "RULE_ONLY").getId());
    }

    /**
     * 返回同步运行详情。
     *
     * @param runId 运行主键
     * @return 运行详情
     */
    @Transactional(rollbackFor = Exception.class)
    public SourceSyncRunDetail getRunDetail(Long runId) {
        SourceSyncRun run = sourceSyncService.findById(runId).orElseThrow(() -> new IllegalArgumentException("run not found: " + runId));
        SourceSyncRun refreshedRun = refreshRunFromCompileJob(run);
        return toDetail(refreshedRun);
    }

    /**
     * 列出资料源的同步历史。
     *
     * @param sourceId 资料源主键
     * @return 同步历史
     */
    @Transactional(rollbackFor = Exception.class)
    public List<SourceSyncRunDetail> listRunDetails(Long sourceId) {
        List<SourceSyncRunDetail> details = new ArrayList<SourceSyncRunDetail>();
        for (SourceSyncRun run : sourceSyncService.listRuns(sourceId)) {
            details.add(getRunDetail(run.getId()));
        }
        return details;
    }

    /**
     * 查询最近的同步运行详情。
     *
     * @param limit 返回数量
     * @return 最近同步运行详情
     */
    @Transactional(rollbackFor = Exception.class)
    public List<SourceSyncRunDetail> listRecentRunDetails(int limit) {
        List<SourceSyncRunDetail> details = new ArrayList<SourceSyncRunDetail>();
        for (SourceSyncRun run : sourceSyncService.listRecentRuns(limit)) {
            details.add(getRunDetail(run.getId()));
        }
        return details;
    }

    /**
     * 对 WAIT_CONFIRM 运行执行人工确认。
     *
     * @param runId 运行主键
     * @param resolverDecision 人工确认决策
     * @param sourceId 目标资料源主键
     * @return 运行详情
     */
    @Transactional(rollbackFor = Exception.class)
    public SourceSyncRunDetail confirmRun(Long runId, String resolverDecision, Long sourceId) {
        SourceSyncRun existingRun = sourceSyncService.findById(runId).orElseThrow(() -> new IllegalArgumentException("run not found: " + runId));
        SourceSyncRun currentRun = refreshRunFromCompileJob(existingRun);
        if (!"WAIT_CONFIRM".equals(currentRun.getStatus())) {
            throw new IllegalStateException("run is not waiting for confirmation: " + runId);
        }

        BundleFeatureExtractor.UploadBundleSnapshot bundleSnapshot = loadBundleSnapshot(currentRun);
        String normalizedDecision = normalizeDecision(resolverDecision);
        if ("NEW_SOURCE".equals(normalizedDecision)) {
            KnowledgeSource createdSource = createUploadSource(bundleSnapshot.getBundleSummary());
            SourceSyncRun requeuedRun = submitCompile(
                    currentRun,
                    createdSource,
                    bundleSnapshot,
                    "MANUAL_OVERRIDE",
                    normalizedDecision,
                    "CREATE"
            );
            return getRunDetail(requeuedRun.getId());
        }

        if (!"EXISTING_SOURCE_UPDATE".equals(normalizedDecision) && !"EXISTING_SOURCE_APPEND".equals(normalizedDecision)) {
            throw new IllegalArgumentException("unsupported decision: " + resolverDecision);
        }
        if (sourceId == null) {
            throw new IllegalArgumentException("sourceId is required for existing-source confirmation");
        }

        KnowledgeSource targetSource = sourceService.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("source not found: " + sourceId));
        SourceSyncRun acceptedRun = routeExplicitSource(currentRun, targetSource, bundleSnapshot, "MANUAL_OVERRIDE", normalizedDecision);
        return getRunDetail(acceptedRun.getId());
    }

    /**
     * 重试失败的上传型同步运行。
     *
     * <p>当上传型资料在 compile 阶段失败时，优先复用原 compile job 与 stagingDir，
     * 避免要求用户重新上传大文件。</p>
     *
     * @param runId 运行主键
     * @return 最新同步运行详情
     */
    @Transactional(rollbackFor = Exception.class)
    public SourceSyncRunDetail retryRun(Long runId) {
        SourceSyncRun existingRun = sourceSyncService.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("run not found: " + runId));
        SourceSyncRun currentRun = refreshRunFromCompileJob(existingRun);
        if (!"UPLOAD".equals(currentRun.getSourceType())) {
            throw new IllegalArgumentException("only upload runs support retry: " + runId);
        }
        if (!"FAILED".equals(currentRun.getStatus())) {
            throw new IllegalStateException("run is not failed: " + runId);
        }

        BundleFeatureExtractor.UploadBundleSnapshot bundleSnapshot = loadBundleSnapshot(currentRun);
        ensureStagingDirAvailable(bundleSnapshot.getStagingDir());
        if (currentRun.getSourceId() != null) {
            KnowledgeSource targetSource = sourceService.findById(currentRun.getSourceId())
                    .orElseThrow(() -> new IllegalArgumentException("source not found: " + currentRun.getSourceId()));
            rejectWhenSourceStatusBlocksSync(targetSource);
            rejectWhenSourceHasActiveRun(targetSource.getId(), currentRun.getId());
        }

        if (currentRun.getCompileJobId() != null && !currentRun.getCompileJobId().isBlank()) {
            CompileJobRecord retriedCompileJob = compileJobService.retry(currentRun.getCompileJobId());
            SourceSyncRun retriedRun = sourceSyncService.saveRun(rebuildRunForRetry(
                    currentRun,
                    currentRun.getCompileJobId(),
                    buildEvidenceJson(
                            bundleSnapshot,
                            currentRun.getResolverDecision(),
                            currentRun.getCompileJobId(),
                            "已重新提交处理任务，等待后台执行",
                            readMaterializationNode(currentRun.getEvidenceJson())
                    )
            ));
            return toDetail(retriedRun, retriedCompileJob);
        }

        if (currentRun.getSourceId() == null) {
            throw new IllegalStateException("failed upload run cannot retry without sourceId or compileJobId");
        }
        KnowledgeSource targetSource = sourceService.findById(currentRun.getSourceId())
                .orElseThrow(() -> new IllegalArgumentException("source not found: " + currentRun.getSourceId()));
        SourceSyncRun requeuedRun = submitCompile(
                currentRun,
                targetSource,
                bundleSnapshot,
                currentRun.getResolverMode(),
                currentRun.getResolverDecision(),
                currentRun.getSyncAction()
        );
        return getRunDetail(requeuedRun.getId());
    }

    private SourceSyncRun createInitialRun(
            BundleFeatureExtractor.UploadBundleSnapshot bundleSnapshot,
            Long requestedSourceId,
            String sourceType,
            JsonNode materializationNode
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        String evidenceJson = buildEvidenceJson(bundleSnapshot, null, null, "资料包已进入 staging，等待识别", materializationNode);
        SourceSyncRun run = new SourceSyncRun(
                null,
                requestedSourceId,
                sourceType,
                bundleSnapshot.getManifestHash(),
                "MANUAL",
                "RULE_ONLY",
                null,
                null,
                "MATCHING",
                null,
                null,
                evidenceJson,
                null,
                now,
                now,
                null,
                null
        );
        try {
            return sourceSyncService.requestRun(run);
        }
        catch (DataIntegrityViolationException ex) {
            if (requestedSourceId == null) {
                return sourceSyncService.findActivePrelockByManifestHash(bundleSnapshot.getManifestHash())
                        .orElseThrow(() -> ex);
            }
            throw ex;
        }
    }

    private SourceSyncRun routeAutomaticDecision(
            SourceSyncRun run,
            BundleFeatureExtractor.UploadBundleSnapshot bundleSnapshot
    ) {
        SourceDecisionResult decisionResult = sourceDecisionPolicy.decide(
                bundleSnapshot.getBundleSummary(),
                bundleSnapshot.getManifestHash(),
                sourceService.listSources()
        );
        if (decisionResult.isSkippedNoChange()) {
            KnowledgeSource matchedSource = sourceService.findById(decisionResult.getMatchedSourceId()).orElseThrow();
            return markSkippedNoChange(run, matchedSource, bundleSnapshot, decisionResult.getResolverMode(), decisionResult.getResolverDecision(), decisionResult.getSyncAction(), decisionResult.getMessage());
        }
        if (decisionResult.isWaitConfirm()) {
            return sourceSyncService.saveRun(replaceRun(
                    run,
                    null,
                    decisionResult.getResolverMode(),
                    decisionResult.getResolverDecision(),
                    null,
                    "WAIT_CONFIRM",
                    decisionResult.getMatchedSourceId(),
                    null,
                    buildEvidenceJson(
                            bundleSnapshot,
                            decisionResult.getResolverDecision(),
                            null,
                            decisionResult.getMessage(),
                            readMaterializationNode(run.getEvidenceJson())
                    ),
                    null,
                    null,
                    null
            ));
        }
        if ("NEW_SOURCE".equals(decisionResult.getResolverDecision())) {
            KnowledgeSource createdSource = createUploadSource(bundleSnapshot.getBundleSummary());
            return submitCompile(
                    run,
                    createdSource,
                    bundleSnapshot,
                    decisionResult.getResolverMode(),
                    decisionResult.getResolverDecision(),
                    decisionResult.getSyncAction()
            );
        }
        KnowledgeSource matchedSource = sourceService.findById(decisionResult.getMatchedSourceId()).orElseThrow();
        return routeExplicitSource(
                run,
                matchedSource,
                bundleSnapshot,
                decisionResult.getResolverMode(),
                decisionResult.getResolverDecision()
        );
    }

    private SourceSyncRun routeExplicitSource(
            SourceSyncRun run,
            KnowledgeSource targetSource,
            BundleFeatureExtractor.UploadBundleSnapshot bundleSnapshot,
            String resolverMode
    ) {
        boolean updateLike = isUpdateLike(targetSource, bundleSnapshot.getBundleSummary());
        String resolverDecision = updateLike ? "EXISTING_SOURCE_UPDATE" : "EXISTING_SOURCE_APPEND";
        return routeExplicitSource(run, targetSource, bundleSnapshot, resolverMode, resolverDecision);
    }

    private SourceSyncRun routeExplicitSource(
            SourceSyncRun run,
            KnowledgeSource targetSource,
            BundleFeatureExtractor.UploadBundleSnapshot bundleSnapshot,
            String resolverMode,
            String resolverDecision
    ) {
        rejectWhenSourceStatusBlocksSync(targetSource);
        rejectWhenSourceHasActiveRun(targetSource.getId(), run.getId());
        String syncAction = "EXISTING_SOURCE_UPDATE".equals(resolverDecision) ? "UPDATE" : "APPEND";
        if (bundleSnapshot.getManifestHash().equals(targetSource.getLatestManifestHash())) {
            return markSkippedNoChange(
                    run,
                    targetSource,
                    bundleSnapshot,
                    resolverMode,
                    resolverDecision,
                    syncAction,
                    "资料包与最近一次成功快照一致，跳过本次同步"
            );
        }
        return submitCompile(run, targetSource, bundleSnapshot, resolverMode, resolverDecision, syncAction);
    }

    private SourceSyncRun markSkippedNoChange(
            SourceSyncRun run,
            KnowledgeSource targetSource,
            BundleFeatureExtractor.UploadBundleSnapshot bundleSnapshot,
            String resolverMode,
            String resolverDecision,
            String syncAction,
            String message
    ) {
        SourceSyncRun skippedRun = sourceSyncService.saveRun(replaceRun(
                run,
                targetSource.getId(),
                resolverMode,
                resolverDecision,
                syncAction,
                "SKIPPED_NO_CHANGE",
                targetSource.getId(),
                null,
                buildEvidenceJson(
                        bundleSnapshot,
                        resolverDecision,
                        null,
                        message,
                        readMaterializationNode(run.getEvidenceJson())
                ),
                null,
                null,
                OffsetDateTime.now()
        ));
        updateSourceAfterSuccess(targetSource, bundleSnapshot, skippedRun);
        return skippedRun;
    }

    private SourceSyncRun submitCompile(
            SourceSyncRun run,
            KnowledgeSource targetSource,
            BundleFeatureExtractor.UploadBundleSnapshot bundleSnapshot,
            String resolverMode,
            String resolverDecision,
            String syncAction
    ) {
        CompileJobRecord compileJobRecord = compileJobService.submit(
                bundleSnapshot.getStagingDir().toString(),
                false,
                true,
                CompileOrchestrationModes.STATE_GRAPH,
                targetSource.getId(),
                run.getId()
        );
        return sourceSyncService.saveRun(replaceRun(
                run,
                targetSource.getId(),
                resolverMode,
                resolverDecision,
                syncAction,
                "COMPILE_QUEUED",
                targetSource.getId(),
                compileJobRecord.getJobId(),
                buildEvidenceJson(
                        bundleSnapshot,
                        resolverDecision,
                        compileJobRecord.getJobId(),
                        "已提交处理任务，等待后台执行",
                        readMaterializationNode(run.getEvidenceJson())
                ),
                null,
                null,
                null
        ));
    }

    private SourceSyncRun refreshRunFromCompileJob(SourceSyncRun run) {
        SourceSyncRun expiredWaitConfirmRun = expireWaitConfirmRunIfNecessary(run);
        if (expiredWaitConfirmRun != run) {
            return expiredWaitConfirmRun;
        }
        if (run.getCompileJobId() == null || isTerminal(run.getStatus())) {
            return run;
        }
        CompileJobRecord compileJobRecord = compileJobService.getJob(run.getCompileJobId());
        if (CompileJobStatuses.RUNNING.equals(compileJobRecord.getStatus()) && !"RUNNING".equals(run.getStatus())) {
            return sourceSyncService.saveRun(replaceRun(
                    run,
                    run.getSourceId(),
                    run.getResolverMode(),
                    run.getResolverDecision(),
                    run.getSyncAction(),
                    "RUNNING",
                    run.getMatchedSourceId(),
                    run.getCompileJobId(),
                    run.getEvidenceJson(),
                    null,
                    compileJobRecord.getStartedAt(),
                    null
            ));
        }
        if (CompileJobStatuses.QUEUED.equals(compileJobRecord.getStatus())
                && !"COMPILE_QUEUED".equals(run.getStatus())) {
            return sourceSyncService.saveRun(replaceRun(
                    run,
                    run.getSourceId(),
                    run.getResolverMode(),
                    run.getResolverDecision(),
                    run.getSyncAction(),
                    "COMPILE_QUEUED",
                    run.getMatchedSourceId(),
                    run.getCompileJobId(),
                    updateEvidenceMessage(run.getEvidenceJson(), "已回收运行中任务，等待后台重新执行"),
                    null,
                    null,
                    null
            ));
        }
        if (CompileJobStatuses.SUCCEEDED.equals(compileJobRecord.getStatus())) {
            SourceSyncRun succeededRun = sourceSyncService.saveRun(replaceRun(
                    run,
                    run.getSourceId(),
                    run.getResolverMode(),
                    run.getResolverDecision(),
                    run.getSyncAction(),
                    "SUCCEEDED",
                    run.getMatchedSourceId(),
                    run.getCompileJobId(),
                    updateEvidenceMessage(run.getEvidenceJson(), "处理成功，资料已写入知识库"),
                    null,
                    compileJobRecord.getStartedAt(),
                    compileJobRecord.getFinishedAt()
            ));
            if (succeededRun.getSourceId() != null) {
                KnowledgeSource source = sourceService.findById(succeededRun.getSourceId()).orElseThrow();
                updateSourceAfterSuccess(source, loadBundleSnapshot(succeededRun), succeededRun);
            }
            return succeededRun;
        }
        if (CompileJobStatuses.FAILED.equals(compileJobRecord.getStatus())) {
            return sourceSyncService.saveRun(replaceRun(
                    run,
                    run.getSourceId(),
                    run.getResolverMode(),
                    run.getResolverDecision(),
                    run.getSyncAction(),
                    "FAILED",
                    run.getMatchedSourceId(),
                    run.getCompileJobId(),
                    updateEvidenceMessage(run.getEvidenceJson(), "处理失败"),
                    compileJobRecord.getErrorMessage(),
                    compileJobRecord.getStartedAt(),
                    compileJobRecord.getFinishedAt()
            ));
        }
        return run;
    }

    private SourceSyncRun expireWaitConfirmRunIfNecessary(SourceSyncRun run) {
        if (!"WAIT_CONFIRM".equals(run.getStatus())) {
            return run;
        }
        OffsetDateTime requestedAt = run.getRequestedAt();
        if (requestedAt == null) {
            return run;
        }
        OffsetDateTime deadline = requestedAt.plus(WAIT_CONFIRM_TIMEOUT);
        OffsetDateTime now = OffsetDateTime.now();
        if (deadline.isAfter(now)) {
            return run;
        }
        return sourceSyncService.saveRun(replaceRun(
                run,
                run.getSourceId(),
                run.getResolverMode(),
                run.getResolverDecision(),
                run.getSyncAction(),
                "FAILED",
                run.getMatchedSourceId(),
                run.getCompileJobId(),
                updateEvidenceMessage(run.getEvidenceJson(), "WAIT_CONFIRM 超时，已自动关闭"),
                "WAIT_CONFIRM timed out after 7 days",
                run.getStartedAt(),
                now
        ));
    }

    private SourceSyncRunDetail toDetail(SourceSyncRun run) {
        JsonNode evidenceNode = readEvidence(run.getEvidenceJson());
        List<String> sourceNames = readStringArray(evidenceNode.path("sourceNames"));
        KnowledgeSource source = run.getSourceId() == null ? null : sourceService.findById(run.getSourceId()).orElse(null);
        CompileJobRecord compileJobRecord = run.getCompileJobId() == null ? null : compileJobService.getJob(run.getCompileJobId());
        return toDetail(run, compileJobRecord, source, sourceNames, evidenceNode);
    }

    private SourceSyncRunDetail toDetail(SourceSyncRun run, CompileJobRecord compileJobRecord) {
        JsonNode evidenceNode = readEvidence(run.getEvidenceJson());
        List<String> sourceNames = readStringArray(evidenceNode.path("sourceNames"));
        KnowledgeSource source = run.getSourceId() == null ? null : sourceService.findById(run.getSourceId()).orElse(null);
        return toDetail(run, compileJobRecord, source, sourceNames, evidenceNode);
    }

    private SourceSyncRunDetail toDetail(
            SourceSyncRun run,
            CompileJobRecord compileJobRecord,
            KnowledgeSource source,
            List<String> sourceNames,
            JsonNode evidenceNode
    ) {
        String compileJobStatus = compileJobRecord == null ? null : compileJobRecord.getStatus();
        String compileDerivedStatus = compileJobRecord == null ? null : compileJobDerivedStatusResolver.resolve(compileJobRecord);
        String compileCurrentStep = compileJobRecord == null ? null : compileJobRecord.getCurrentStep();
        Integer compileProgressCurrent = compileJobRecord == null ? null : Integer.valueOf(compileJobRecord.getProgressCurrent());
        Integer compileProgressTotal = compileJobRecord == null ? null : Integer.valueOf(compileJobRecord.getProgressTotal());
        String compileProgressMessage = compileJobRecord == null ? null : compileJobRecord.getProgressMessage();
        String compileLastHeartbeatAt = compileJobRecord == null ? null : formatTime(compileJobRecord.getLastHeartbeatAt());
        String compileRunningExpiresAt = compileJobRecord == null ? null : formatTime(compileJobRecord.getRunningExpiresAt());
        String compileErrorCode = compileJobRecord == null ? null : compileJobRecord.getErrorCode();
        String message = evidenceNode.path("message").asText(defaultMessage(run));
        String displayStatus = presentationResolver.resolveDisplayStatus(
                compileDerivedStatus,
                compileJobStatus,
                run.getStatus()
        );
        AdminProcessingTaskPresentation presentation = presentationResolver.resolve(
                AdminProcessingTaskPresentationResolver.TASK_TYPE_SOURCE_SYNC,
                displayStatus,
                compileCurrentStep,
                compileProgressCurrent,
                compileProgressTotal,
                compileProgressMessage,
                compileErrorCode,
                message,
                run.getErrorMessage(),
                run.getSourceId()
        );
        List<AdminProcessingTaskActionResponse> actions = buildSourceSyncActions(run, displayStatus);
        return new SourceSyncRunDetail(
                run.getId(),
                run.getSourceId(),
                source == null ? null : source.getName(),
                run.getSourceType(),
                run.getStatus(),
                run.getResolverMode(),
                run.getResolverDecision(),
                run.getSyncAction(),
                run.getMatchedSourceId(),
                run.getCompileJobId(),
                compileJobStatus,
                compileDerivedStatus,
                compileCurrentStep,
                compileProgressCurrent,
                compileProgressTotal,
                compileProgressMessage,
                compileLastHeartbeatAt,
                compileRunningExpiresAt,
                compileErrorCode,
                run.getManifestHash(),
                message,
                run.getErrorMessage(),
                sourceNames,
                actions,
                presentation.getDisplayStatus(),
                presentation.getDisplayStatusLabel(),
                presentation.getCurrentStepLabel(),
                presentation.getNextStepHint(),
                presentation.getProgressText(),
                presentation.getReasonSummary(),
                presentation.getOperationalNote(),
                presentation.getProgressSteps(),
                presentation.getDisplayTone(),
                presentation.isProcessingActive(),
                presentation.isRequiresManualAction(),
                presentation.getNoticeTone(),
                presentation.getCompletionNotice(),
                run.getEvidenceJson(),
                formatTime(run.getRequestedAt()),
                formatTime(run.getUpdatedAt()),
                formatTime(run.getStartedAt()),
                formatTime(run.getFinishedAt())
        );
    }

    private List<AdminProcessingTaskActionResponse> buildSourceSyncActions(SourceSyncRun run, String displayStatus) {
        List<AdminProcessingTaskActionResponse> actions = new ArrayList<AdminProcessingTaskActionResponse>();
        if ("WAIT_CONFIRM".equals(displayStatus)) {
            actions.add(new AdminProcessingTaskActionResponse(
                    "CONFIRM_NEW_SOURCE",
                    "确认为新资料源",
                    "ghost-btn",
                    run.getId(),
                    run.getSourceId(),
                    "NEW_SOURCE",
                    null,
                    false
            ));
            if (run.getMatchedSourceId() != null) {
                actions.add(new AdminProcessingTaskActionResponse(
                        "CONFIRM_APPEND_SOURCE",
                        "追加到候选资料源",
                        "secondary-btn",
                        run.getId(),
                        run.getSourceId(),
                        "EXISTING_SOURCE_APPEND",
                        run.getMatchedSourceId(),
                        false
                ));
                actions.add(new AdminProcessingTaskActionResponse(
                        "CONFIRM_UPDATE_SOURCE",
                        "按更新覆盖候选资料源",
                        "ghost-btn",
                        run.getId(),
                        run.getSourceId(),
                        "EXISTING_SOURCE_UPDATE",
                        run.getMatchedSourceId(),
                        false
                ));
            }
        }
        if (run.getSourceId() != null
                && !"UPLOAD".equalsIgnoreCase(run.getSourceType())
                && ("FAILED".equals(displayStatus)
                || "STALLED".equals(displayStatus)
                || "SUCCEEDED".equals(displayStatus)
                || "SKIPPED_NO_CHANGE".equals(displayStatus))) {
            actions.add(new AdminProcessingTaskActionResponse(
                    "RESYNC_SOURCE",
                    "FAILED".equals(displayStatus) || "STALLED".equals(displayStatus)
                            ? "重新同步当前资料源"
                            : "再次同步当前资料源",
                    "FAILED".equals(displayStatus) || "STALLED".equals(displayStatus)
                            ? "secondary-btn"
                            : "ghost-btn",
                    run.getId(),
                    run.getSourceId(),
                    null,
                    null,
                    false
            ));
        }
        return actions;
    }

    private void ensureStagingDirAvailable(Path stagingDir) {
        if (stagingDir == null || !Files.isDirectory(stagingDir)) {
            throw new IllegalStateException("stagingDir no longer exists, please upload again");
        }
    }

    private String defaultMessage(SourceSyncRun run) {
        if ("WAIT_CONFIRM".equals(run.getStatus())) {
            return "检测到可能重复，等待人工确认";
        }
        if ("SKIPPED_NO_CHANGE".equals(run.getStatus())) {
            return "资料包无变化，已跳过同步";
        }
        if ("FAILED".equals(run.getStatus())) {
            return "同步失败";
        }
        if ("SUCCEEDED".equals(run.getStatus())) {
            return "资料同步成功";
        }
        return "上传已受理";
    }

    private BundleFeatureExtractor.UploadBundleSnapshot loadBundleSnapshot(SourceSyncRun run) {
        JsonNode evidenceNode = readEvidence(run.getEvidenceJson());
        String stagingDir = evidenceNode.path("stagingDir").asText();
        if (stagingDir.isBlank()) {
            throw new IllegalStateException("stagingDir missing in evidence");
        }
        List<String> sourceNames = readStringArray(evidenceNode.path("sourceNames"));
        BundleSummary bundleSummary = readBundleSummary(evidenceNode.path("bundleSummary"));
        return new BundleFeatureExtractor.UploadBundleSnapshot(
                Path.of(stagingDir),
                run.getManifestHash(),
                sourceNames,
                bundleSummary
        );
    }

    private BundleSummary readBundleSummary(JsonNode bundleNode) {
        return new BundleSummary(
                bundleNode.path("displayName").asText(),
                bundleNode.path("fileCount").asInt(),
                bundleNode.path("dirCount").asInt(),
                readStringArray(bundleNode.path("topLevelNames")),
                readIntegerMap(bundleNode.path("extensionDistribution")),
                readStringArray(bundleNode.path("relativePathsSample")),
                readStringArray(bundleNode.path("signatureFiles")),
                bundleNode.path("contentProfile").asText("DOCUMENT"),
                readStringArray(bundleNode.path("keywords")),
                readStringArray(bundleNode.path("titleHints")),
                bundleNode.path("pathFingerprint").asText(),
                bundleNode.path("contentFingerprint").asText(),
                bundleNode.path("summaryText").asText()
        );
    }

    private List<String> readStringArray(JsonNode arrayNode) {
        List<String> values = new ArrayList<String>();
        if (!arrayNode.isArray()) {
            return values;
        }
        for (JsonNode itemNode : arrayNode) {
            values.add(itemNode.asText());
        }
        return values;
    }

    private java.util.Map<String, Integer> readIntegerMap(JsonNode objectNode) {
        java.util.Map<String, Integer> values = new java.util.LinkedHashMap<String, Integer>();
        if (!objectNode.isObject()) {
            return values;
        }
        java.util.Iterator<String> fieldNames = objectNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            values.put(fieldName, objectNode.path(fieldName).asInt());
        }
        return values;
    }

    private String buildEvidenceJson(
            BundleFeatureExtractor.UploadBundleSnapshot bundleSnapshot,
            String acceptedDecision,
            String compileJobId,
            String message,
            JsonNode materializationNode
    ) {
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        rootNode.put("stagingDir", bundleSnapshot.getStagingDir().toString());
        rootNode.put("message", message);
        if (acceptedDecision != null) {
            rootNode.put("acceptedDecision", acceptedDecision);
        }
        if (compileJobId != null) {
            rootNode.put("compileJobId", compileJobId);
        }
        ArrayNode sourceNamesNode = rootNode.putArray("sourceNames");
        for (String sourceName : bundleSnapshot.getSourceNames()) {
            sourceNamesNode.add(sourceName);
        }
        rootNode.set("bundleSummary", OBJECT_MAPPER.valueToTree(bundleSnapshot.getBundleSummary()));
        if (materializationNode != null && !materializationNode.isMissingNode() && !materializationNode.isNull()) {
            rootNode.set("materialization", materializationNode);
        }
        return rootNode.toString();
    }

    private String updateEvidenceMessage(String evidenceJson, String message) {
        JsonNode evidenceNode = readEvidence(evidenceJson);
        ObjectNode objectNode = evidenceNode.isObject() ? (ObjectNode) evidenceNode : OBJECT_MAPPER.createObjectNode();
        objectNode.put("message", message);
        return objectNode.toString();
    }

    private JsonNode readEvidence(String evidenceJson) {
        if (evidenceJson == null || evidenceJson.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            return OBJECT_MAPPER.readTree(evidenceJson);
        }
        catch (Exception ex) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private JsonNode readMaterializationNode(String evidenceJson) {
        return readEvidence(evidenceJson).path("materialization");
    }

    private KnowledgeSource createUploadSource(BundleSummary bundleSummary) {
        String sourceCode = nextSourceCode(bundleSummary);
        ObjectNode metadataNode = OBJECT_MAPPER.createObjectNode();
        metadataNode.set("bundleSummary", OBJECT_MAPPER.valueToTree(bundleSummary));
        return sourceService.save(new KnowledgeSource(
                null,
                sourceCode,
                bundleSummary.getDisplayName(),
                "UPLOAD",
                bundleSummary.getContentProfile(),
                "ACTIVE",
                "NORMAL",
                "AUTO",
                "{}",
                metadataNode.toString(),
                null,
                null,
                null,
                null,
                null,
                null
        ));
    }

    /**
     * 生成下一个可用资料源编码。
     *
     * @param bundleSummary 资料包摘要
     * @return 资料源编码
     */
    private String nextSourceCode(BundleSummary bundleSummary) {
        String baseCode = normalizeSourceCode(bundleSummary.getDisplayName());
        if ("upload-source".equals(baseCode)) {
            for (String titleHint : bundleSummary.getTitleHints()) {
                baseCode = normalizeSourceCode(titleHint);
                if (!"upload-source".equals(baseCode)) {
                    break;
                }
            }
        }
        List<KnowledgeSource> existingSources = sourceService.listSources();
        String candidate = baseCode;
        int index = 2;
        while (containsSourceCode(existingSources, candidate)) {
            candidate = baseCode + "-" + index;
            index++;
        }
        return candidate;
    }

    private boolean containsSourceCode(List<KnowledgeSource> existingSources, String sourceCode) {
        for (KnowledgeSource source : existingSources) {
            if (sourceCode.equals(source.getSourceCode())) {
                return true;
            }
        }
        return false;
    }

    private String normalizeSourceCode(String value) {
        String normalized = value == null ? "" : value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (normalized.isBlank()) {
            return "upload-source";
        }
        return normalized;
    }

    private boolean isUpdateLike(KnowledgeSource source, BundleSummary bundleSummary) {
        JsonNode metadataNode = readEvidence(source.getMetadataJson());
        JsonNode storedBundleNode = metadataNode.path("bundleSummary");
        String storedPathFingerprint = storedBundleNode.path("pathFingerprint").asText("");
        if (!storedPathFingerprint.isBlank() && storedPathFingerprint.equals(bundleSummary.getPathFingerprint())) {
            return true;
        }
        List<String> storedTopLevels = readStringArray(storedBundleNode.path("topLevelNames"));
        return !storedTopLevels.isEmpty() && storedTopLevels.equals(bundleSummary.getTopLevelNames());
    }

    private void updateSourceAfterSuccess(
            KnowledgeSource source,
            BundleFeatureExtractor.UploadBundleSnapshot bundleSnapshot,
            SourceSyncRun run
    ) {
        ObjectNode metadataNode = readEvidence(source.getMetadataJson()).isObject()
                ? (ObjectNode) readEvidence(source.getMetadataJson())
                : OBJECT_MAPPER.createObjectNode();
        metadataNode.set("bundleSummary", OBJECT_MAPPER.valueToTree(bundleSnapshot.getBundleSummary()));
        metadataNode.put("lastManifestHash", bundleSnapshot.getManifestHash());
        KnowledgeSource updatedSource = new KnowledgeSource(
                source.getId(),
                source.getSourceCode(),
                source.getName(),
                source.getSourceType(),
                source.getContentProfile(),
                source.getStatus(),
                source.getVisibility(),
                source.getDefaultSyncMode(),
                source.getConfigJson(),
                metadataNode.toString(),
                bundleSnapshot.getManifestHash(),
                run.getId(),
                run.getStatus(),
                OffsetDateTime.now(),
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
        sourceService.save(updatedSource);
        sourceSnapshotJdbcRepository.save(
                source.getId(),
                run.getId(),
                bundleSnapshot.getManifestHash(),
                metadataNode.toString()
        );
    }

    private void rejectWhenSourceHasActiveRun(Long sourceId, Long currentRunId) {
        List<SourceSyncRun> activeRuns = sourceSyncService.listActiveRuns(sourceId);
        for (SourceSyncRun activeRun : activeRuns) {
            if (currentRunId != null && currentRunId.equals(activeRun.getId())) {
                continue;
            }
            throw new SourceSyncConflictException("active source sync run already exists: " + activeRun.getId());
        }
    }

    private void rejectWhenSourceStatusBlocksSync(KnowledgeSource source) {
        if ("DISABLED".equals(source.getStatus())) {
            throw new IllegalStateException("source is disabled: " + source.getId());
        }
        if ("ARCHIVED".equals(source.getStatus())) {
            throw new IllegalStateException("source is archived: " + source.getId());
        }
    }

    private SourceSyncRun replaceRun(
            SourceSyncRun existingRun,
            Long sourceId,
            String resolverMode,
            String resolverDecision,
            String syncAction,
            String status,
            Long matchedSourceId,
            String compileJobId,
            String evidenceJson,
            String errorMessage,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt
    ) {
        return new SourceSyncRun(
                existingRun.getId(),
                sourceId,
                existingRun.getSourceType(),
                existingRun.getManifestHash(),
                existingRun.getTriggerType(),
                resolverMode,
                resolverDecision,
                syncAction,
                status,
                matchedSourceId,
                compileJobId,
                evidenceJson,
                errorMessage,
                existingRun.getRequestedAt(),
                OffsetDateTime.now(),
                startedAt == null ? existingRun.getStartedAt() : startedAt,
                finishedAt == null ? existingRun.getFinishedAt() : finishedAt
        );
    }

    private SourceSyncRun rebuildRunForRetry(
            SourceSyncRun existingRun,
            String compileJobId,
            String evidenceJson
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        return new SourceSyncRun(
                existingRun.getId(),
                existingRun.getSourceId(),
                existingRun.getSourceType(),
                existingRun.getManifestHash(),
                existingRun.getTriggerType(),
                existingRun.getResolverMode(),
                existingRun.getResolverDecision(),
                existingRun.getSyncAction(),
                "COMPILE_QUEUED",
                existingRun.getMatchedSourceId(),
                compileJobId,
                evidenceJson,
                null,
                existingRun.getRequestedAt(),
                now,
                null,
                null
        );
    }

    private boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status)
                || "FAILED".equals(status)
                || "SKIPPED_NO_CHANGE".equals(status);
    }

    private String normalizeDecision(String resolverDecision) {
        if (resolverDecision == null || resolverDecision.isBlank()) {
            throw new IllegalArgumentException("decision is required");
        }
        return resolverDecision.trim().toUpperCase(Locale.ROOT);
    }

    private String formatTime(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }
}
