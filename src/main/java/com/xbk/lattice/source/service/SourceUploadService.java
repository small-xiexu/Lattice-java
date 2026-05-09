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
public class SourceUploadService extends SourceUploadWorkflowSupport {

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
}
