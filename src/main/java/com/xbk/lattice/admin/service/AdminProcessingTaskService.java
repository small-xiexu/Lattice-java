package com.xbk.lattice.admin.service;

import com.xbk.lattice.api.admin.AdminKnowledgeHelpActionResponse;
import com.xbk.lattice.api.admin.AdminKnowledgeHelpStateResponse;
import com.xbk.lattice.api.admin.AdminProcessingTaskSummaryCardResponse;
import com.xbk.lattice.api.admin.AdminProcessingTaskItemResponse;
import com.xbk.lattice.api.admin.AdminProcessingTaskListResponse;
import com.xbk.lattice.api.admin.AdminProcessingTaskSummaryResponse;
import com.xbk.lattice.api.admin.AdminProcessingTaskActionResponse;
import com.xbk.lattice.compiler.service.CompileJobDerivedStatusResolver;
import com.xbk.lattice.compiler.service.CompileJobService;
import com.xbk.lattice.governance.StatusService;
import com.xbk.lattice.governance.StatusSnapshot;
import com.xbk.lattice.infra.persistence.CompileJobRecord;
import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.domain.SourceSyncRunDetail;
import com.xbk.lattice.source.service.SourceService;
import com.xbk.lattice.source.service.SourceUploadService;
import org.springframework.stereotype.Service;

import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 工作台当前处理任务聚合服务。
 *
 * 职责：统一聚合 source sync runs 与 standalone compile jobs，输出工作台展示所需读模型
 *
 * @author xiexu
 */
@Service
public class AdminProcessingTaskService {

    private final SourceUploadService sourceUploadService;

    private final CompileJobService compileJobService;

    private final CompileJobDerivedStatusResolver compileJobDerivedStatusResolver;

    private final SourceService sourceService;

    private final AdminUploadWorkspaceService adminUploadWorkspaceService;

    private final AdminProcessingTaskPresentationResolver presentationResolver;

    private final StatusService statusService;

    /**
     * 创建工作台当前处理任务聚合服务。
     *
     * @param sourceUploadService 统一上传服务
     * @param compileJobService 编译作业服务
     * @param compileJobDerivedStatusResolver 编译作业派生状态解析器
     * @param sourceService 资料源服务
     * @param adminUploadWorkspaceService 上传工作目录服务
     * @param presentationResolver 当前处理任务展示解析器
     * @param statusService 状态服务
     */
    public AdminProcessingTaskService(
            SourceUploadService sourceUploadService,
            CompileJobService compileJobService,
            CompileJobDerivedStatusResolver compileJobDerivedStatusResolver,
            SourceService sourceService,
            AdminUploadWorkspaceService adminUploadWorkspaceService,
            AdminProcessingTaskPresentationResolver presentationResolver,
            StatusService statusService
    ) {
        this.sourceUploadService = sourceUploadService;
        this.compileJobService = compileJobService;
        this.compileJobDerivedStatusResolver = compileJobDerivedStatusResolver;
        this.sourceService = sourceService;
        this.adminUploadWorkspaceService = adminUploadWorkspaceService;
        this.presentationResolver = presentationResolver;
        this.statusService = statusService;
    }

    /**
     * 查询当前处理任务列表。
     *
     * @param limit 返回数量
     * @return 当前处理任务列表响应
     */
    public AdminProcessingTaskListResponse listProcessingTasks(int limit) {
        int resolvedLimit = Math.max(limit, 1);
        List<AdminProcessingTaskItemResponse> mergedItems = new ArrayList<AdminProcessingTaskItemResponse>();
        List<SourceSyncRunDetail> recentRuns = collapseCurrentSourceRuns(
                sourceUploadService.listRecentRunDetails(resolvedLimit)
        );
        for (SourceSyncRunDetail recentRun : recentRuns) {
            mergedItems.add(toSourceSyncTask(recentRun));
        }
        List<CompileJobRecord> standaloneJobs = compileJobService.listRecentStandaloneJobs(resolvedLimit);
        for (CompileJobRecord standaloneJob : standaloneJobs) {
            mergedItems.add(toStandaloneCompileTask(standaloneJob));
        }
        mergedItems.sort(new ProcessingTaskComparator());
        AdminProcessingTaskSummaryResponse summary = buildSummary(mergedItems);
        List<AdminProcessingTaskItemResponse> visibleItems = limitItems(mergedItems, resolvedLimit);
        return new AdminProcessingTaskListResponse(summary, visibleItems);
    }

    /**
     * 查询指定资料源的处理历史。
     *
     * @param sourceId 资料源主键
     * @param limit 返回数量
     * @return 当前处理任务列表响应
     */
    public AdminProcessingTaskListResponse listProcessingTasksBySourceId(Long sourceId, int limit) {
        if (sourceId == null) {
            List<AdminProcessingTaskItemResponse> emptyItems = Collections.emptyList();
            AdminProcessingTaskSummaryResponse summary = buildSummary(emptyItems);
            return new AdminProcessingTaskListResponse(summary, emptyItems);
        }
        int resolvedLimit = Math.max(limit, 1);
        List<AdminProcessingTaskItemResponse> mergedItems = new ArrayList<AdminProcessingTaskItemResponse>();
        for (SourceSyncRunDetail sourceRun : sourceUploadService.listRunDetails(sourceId)) {
            mergedItems.add(toSourceSyncTask(sourceRun));
        }
        for (CompileJobRecord compileJobRecord : compileJobService.listRecentStandaloneJobsBySourceId(sourceId, resolvedLimit)) {
            mergedItems.add(toStandaloneCompileTask(compileJobRecord));
        }
        mergedItems.sort(new ProcessingTaskComparator());
        AdminProcessingTaskSummaryResponse summary = buildSummary(mergedItems);
        List<AdminProcessingTaskItemResponse> visibleItems = limitItems(mergedItems, resolvedLimit);
        return new AdminProcessingTaskListResponse(summary, visibleItems);
    }

    /**
     * 收敛当前处理任务视图中的资料源同步记录。
     *
     * 职责：同一资料源只保留最新一条同步 run，旧终态记录仍留在资料源处理历史中查看
     *
     * @param recentRuns 最近同步运行详情
     * @return 收敛后的同步运行详情
     */
    private List<SourceSyncRunDetail> collapseCurrentSourceRuns(List<SourceSyncRunDetail> recentRuns) {
        if (recentRuns == null || recentRuns.isEmpty()) {
            return Collections.emptyList();
        }
        List<SourceSyncRunDetail> collapsedRuns = new ArrayList<SourceSyncRunDetail>();
        Set<Long> seenSourceIds = new LinkedHashSet<Long>();
        for (SourceSyncRunDetail recentRun : recentRuns) {
            Long sourceId = recentRun.getSourceId();
            if (sourceId == null) {
                collapsedRuns.add(recentRun);
                continue;
            }
            if (seenSourceIds.add(sourceId)) {
                collapsedRuns.add(recentRun);
            }
        }
        return collapsedRuns;
    }

    /**
     * 将同步运行详情映射为统一任务。
     *
     * @param runDetail 同步运行详情
     * @return 统一任务
     */
    private AdminProcessingTaskItemResponse toSourceSyncTask(SourceSyncRunDetail runDetail) {
        String title = buildSourceSyncTitle(runDetail);
        List<String> sourceNames = safeList(runDetail.getSourceNames());
        List<AdminProcessingTaskActionResponse> actions = resolveSourceSyncActions(runDetail);
        String displayStatus = presentationResolver.resolveDisplayStatus(
                runDetail.getCompileDerivedStatus(),
                runDetail.getCompileJobStatus(),
                runDetail.getStatus()
        );
        AdminProcessingTaskPresentation presentation = presentationResolver.resolve(
                AdminProcessingTaskPresentationResolver.TASK_TYPE_SOURCE_SYNC,
                displayStatus,
                runDetail.getCompileCurrentStep(),
                runDetail.getCompileProgressCurrent(),
                runDetail.getCompileProgressTotal(),
                runDetail.getCompileProgressMessage(),
                runDetail.getCompileErrorCode(),
                runDetail.getMessage(),
                runDetail.getErrorMessage(),
                runDetail.getSourceId()
        );
        return new AdminProcessingTaskItemResponse(
                "source-run:" + String.valueOf(runDetail.getRunId()),
                AdminProcessingTaskPresentationResolver.TASK_TYPE_SOURCE_SYNC,
                title,
                runDetail.getRunId(),
                runDetail.getSourceId(),
                runDetail.getSourceName(),
                runDetail.getSourceType(),
                runDetail.getStatus(),
                runDetail.getResolverMode(),
                runDetail.getResolverDecision(),
                runDetail.getSyncAction(),
                runDetail.getMatchedSourceId(),
                runDetail.getCompileJobId(),
                runDetail.getCompileJobStatus(),
                runDetail.getCompileDerivedStatus(),
                runDetail.getCompileCurrentStep(),
                runDetail.getCompileProgressCurrent(),
                runDetail.getCompileProgressTotal(),
                runDetail.getCompileProgressMessage(),
                runDetail.getCompileLastHeartbeatAt(),
                runDetail.getCompileRunningExpiresAt(),
                runDetail.getCompileErrorCode(),
                runDetail.getManifestHash(),
                runDetail.getMessage(),
                runDetail.getErrorMessage(),
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
                runDetail.getEvidenceJson(),
                runDetail.getRequestedAt(),
                runDetail.getUpdatedAt(),
                runDetail.getStartedAt(),
                runDetail.getFinishedAt()
        );
    }

    /**
     * 将独立编译作业映射为统一任务。
     *
     * @param compileJobRecord 编译作业记录
     * @return 统一任务
     */
    private AdminProcessingTaskItemResponse toStandaloneCompileTask(CompileJobRecord compileJobRecord) {
        KnowledgeSource source = resolveVisibleSource(compileJobRecord);
        List<String> uploadRelativeFileNames =
                adminUploadWorkspaceService.listRelativeFileNames(compileJobRecord.getSourceDir());
        String title = buildStandaloneCompileTitle(compileJobRecord, source, uploadRelativeFileNames);
        String sourceName = source == null ? null : source.getName();
        Long sourceId = source == null ? null : source.getId();
        String sourceType = source == null ? "DIRECT_COMPILE" : source.getSourceType();
        List<String> sourceNames = buildStandaloneSourceNames(title);
        String derivedStatus = compileJobDerivedStatusResolver.resolve(compileJobRecord);
        String message = buildStandaloneCompileMessage(compileJobRecord, derivedStatus);
        OffsetDateTime updatedAt = resolveStandaloneUpdatedAt(compileJobRecord);
        AdminProcessingTaskPresentation presentation = presentationResolver.resolve(
                AdminProcessingTaskPresentationResolver.TASK_TYPE_STANDALONE_COMPILE,
                derivedStatus,
                compileJobRecord.getCurrentStep(),
                Integer.valueOf(compileJobRecord.getProgressCurrent()),
                Integer.valueOf(compileJobRecord.getProgressTotal()),
                compileJobRecord.getProgressMessage(),
                compileJobRecord.getErrorCode(),
                message,
                compileJobRecord.getErrorMessage(),
                sourceId
        );
        return new AdminProcessingTaskItemResponse(
                "compile-job:" + compileJobRecord.getJobId(),
                AdminProcessingTaskPresentationResolver.TASK_TYPE_STANDALONE_COMPILE,
                title,
                null,
                sourceId,
                sourceName,
                sourceType,
                compileJobRecord.getStatus(),
                null,
                null,
                null,
                null,
                compileJobRecord.getJobId(),
                compileJobRecord.getStatus(),
                derivedStatus,
                compileJobRecord.getCurrentStep(),
                Integer.valueOf(compileJobRecord.getProgressCurrent()),
                Integer.valueOf(compileJobRecord.getProgressTotal()),
                compileJobRecord.getProgressMessage(),
                formatTime(compileJobRecord.getLastHeartbeatAt()),
                formatTime(compileJobRecord.getRunningExpiresAt()),
                compileJobRecord.getErrorCode(),
                null,
                message,
                compileJobRecord.getErrorMessage(),
                sourceNames,
                Collections.emptyList(),
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
                null,
                formatTime(compileJobRecord.getRequestedAt()),
                formatTime(updatedAt),
                formatTime(compileJobRecord.getStartedAt()),
                formatTime(compileJobRecord.getFinishedAt())
        );
    }

    /**
     * 构建任务汇总。
     *
     * @param items 聚合任务列表
     * @return 汇总响应
     */
    private AdminProcessingTaskSummaryResponse buildSummary(List<AdminProcessingTaskItemResponse> items) {
        StatusSnapshot statusSnapshot = statusService.snapshot();
        int runningCount = 0;
        int waitingCount = 0;
        int succeededCount = 0;
        int failedCount = 0;
        for (AdminProcessingTaskItemResponse item : items) {
            String displayStatus = resolveDisplayStatus(item);
            if (AdminProcessingTaskDisplayStatus.WAIT_CONFIRM.matches(displayStatus)) {
                waitingCount++;
            }
            else if (AdminProcessingTaskDisplayStatus.isSucceeded(displayStatus)) {
                succeededCount++;
            }
            else if (AdminProcessingTaskDisplayStatus.isFailed(displayStatus)) {
                failedCount++;
            }
            else if (isRunningLike(displayStatus)) {
                runningCount++;
            }
        }
        return new AdminProcessingTaskSummaryResponse(
                runningCount,
                waitingCount,
                0,
                succeededCount,
                failedCount,
                buildSummaryCards(runningCount, waitingCount, 0, succeededCount, failedCount),
                buildHelpState(statusSnapshot, runningCount, waitingCount, 0, succeededCount, failedCount)
        );
    }

    /**
     * 构建顶部概览卡片。
     *
     * @param runningCount 运行中数量
     * @param waitingCount 待确认数量
     * @param stalledCount 疑似卡住数量
     * @param succeededCount 已完成数量
     * @param failedCount 失败数量
     * @return 概览卡片
     */
    private List<AdminProcessingTaskSummaryCardResponse> buildSummaryCards(
            int runningCount,
            int waitingCount,
            int stalledCount,
            int succeededCount,
            int failedCount
    ) {
        List<AdminProcessingTaskSummaryCardResponse> cards = new ArrayList<AdminProcessingTaskSummaryCardResponse>();
        cards.add(new AdminProcessingTaskSummaryCardResponse(
                "运行中",
                runningCount,
                runningCount > 0 ? "系统仍在持续推进这些任务" : "当前没有正在推进的资料处理任务",
                runningCount > 0 ? "warning" : ""
        ));
        cards.add(new AdminProcessingTaskSummaryCardResponse(
                "待确认",
                waitingCount,
                waitingCount > 0 ? "请打开下方任务查看后端给出的确认动作" : "当前没有需要人工确认的任务",
                waitingCount > 0 ? "warning" : "success"
        ));
        cards.add(new AdminProcessingTaskSummaryCardResponse(
                "已完成",
                succeededCount,
                succeededCount > 0 ? "最近已经成功处理并写入知识库" : "最近还没有成功完成的任务",
                succeededCount > 0 ? "success" : ""
        ));
        cards.add(new AdminProcessingTaskSummaryCardResponse(
                "失败",
                failedCount,
                failedCount > 0 ? "建议打开下方卡片查看错误信息" : "最近没有失败任务",
                failedCount > 0 ? "danger" : "success"
        ));
        return cards;
    }

    /**
     * 构建帮助卡状态。
     *
     * @param runningCount 运行中数量
     * @param waitingCount 待确认数量
     * @param stalledCount 疑似卡住数量
     * @param succeededCount 已完成数量
     * @param failedCount 失败数量
     * @return 帮助卡状态
     */
    private AdminKnowledgeHelpStateResponse buildHelpState(
            StatusSnapshot statusSnapshot,
            int runningCount,
            int waitingCount,
            int stalledCount,
            int succeededCount,
            int failedCount
    ) {
        int articleCount = statusSnapshot == null ? 0 : statusSnapshot.getArticleCount();
        int sourceFileCount = statusSnapshot == null ? 0 : statusSnapshot.getSourceFileCount();
        if (stalledCount > 0) {
            return new AdminKnowledgeHelpStateResponse(
                    "danger",
                    "有一批资料疑似卡住了",
                    "先去当前处理任务查看后端返回的当前步骤、原因摘要和可执行动作。",
                    "upload-delay",
                    List.of(
                            new AdminKnowledgeHelpActionResponse("查看当前处理任务", "knowledge-runs", "primary-btn"),
                            new AdminKnowledgeHelpActionResponse("回资料导入", "knowledge-upload", "ghost-btn")
                    )
            );
        }
        if (failedCount > 0) {
            return new AdminKnowledgeHelpStateResponse(
                    "danger",
                    "最近一次入库失败了",
                    "先去当前处理任务查看后端返回的失败摘要、当前步骤和下一步建议。",
                    "upload-delay",
                    List.of(
                            new AdminKnowledgeHelpActionResponse("查看当前处理任务", "knowledge-runs", "primary-btn"),
                            new AdminKnowledgeHelpActionResponse("回资料导入", "knowledge-upload", "ghost-btn")
                    )
            );
        }
        if (waitingCount > 0) {
            return new AdminKnowledgeHelpStateResponse(
                    "warning",
                    "有一批资料还在等待人工确认",
                    "请去当前处理任务查看后端返回的待确认项和可执行动作。",
                    "upload-delay",
                    List.of(
                            new AdminKnowledgeHelpActionResponse("去当前处理任务", "knowledge-runs", "primary-btn"),
                            new AdminKnowledgeHelpActionResponse("看已入库内容", "knowledge-articles", "ghost-btn")
                    )
            );
        }
        if (runningCount > 0) {
            return new AdminKnowledgeHelpStateResponse(
                    "warning",
                    "这批资料还在处理中",
                    "当前处理任务已经展示后端返回的真实步骤、进度与原因摘要，先去那里继续观察。",
                    "upload-delay",
                    List.of(
                            new AdminKnowledgeHelpActionResponse("去当前处理任务", "knowledge-runs", "primary-btn"),
                            new AdminKnowledgeHelpActionResponse("看已入库内容", "knowledge-articles", "ghost-btn")
                    )
            );
        }
        if (articleCount <= 0 && sourceFileCount <= 0) {
            return new AdminKnowledgeHelpStateResponse(
                    "info",
                    "当前还没有可用资料",
                    "先上传一批文件或接入 Git 仓库。只有资料真正入库后，知识问答页才会稳定返回结果。",
                    "first-steps",
                    List.of(
                            new AdminKnowledgeHelpActionResponse("上传资料", "knowledge-upload", "primary-btn"),
                            new AdminKnowledgeHelpActionResponse("回到首屏状态", "workbench-top", "ghost-btn")
                    )
            );
        }
        if (sourceFileCount > 0 && articleCount <= 0) {
            return new AdminKnowledgeHelpStateResponse(
                    "warning",
                    "资料已经处理过，但还没有进入可问答状态",
                    "请先查看已入库内容；如果这里仍为空，再回到当前处理任务查看后端返回的状态与原因摘要。",
                    "cannot-answer",
                    List.of(
                            new AdminKnowledgeHelpActionResponse("去已入库内容", "knowledge-articles", "primary-btn"),
                            new AdminKnowledgeHelpActionResponse("回资料导入", "knowledge-upload", "ghost-btn")
                    )
            );
        }
        if (succeededCount > 0) {
            return new AdminKnowledgeHelpStateResponse(
                    "success",
                    "知识库已经可以使用",
                    "资料已经进入知识库。现在可以直接提问；如果结果不准，再回到这里核对已入库内容和当前处理任务。",
                    "cannot-answer",
                    List.of(
                            new AdminKnowledgeHelpActionResponse("去知识问答", "go-ask", "primary-btn"),
                            new AdminKnowledgeHelpActionResponse("去已入库内容", "knowledge-articles", "ghost-btn")
                    )
            );
        }
        return new AdminKnowledgeHelpStateResponse(
                "success",
                "知识库已经可以使用",
                "资料已经进入知识库。现在可以直接提问；如果结果不准，再回到这里核对已入库内容和当前处理任务。",
                "cannot-answer",
                List.of(
                        new AdminKnowledgeHelpActionResponse("去知识问答", "go-ask", "primary-btn"),
                        new AdminKnowledgeHelpActionResponse("去已入库内容", "knowledge-articles", "ghost-btn")
                )
        );
    }

    /**
     * 截断任务列表。
     *
     * @param items 原始任务列表
     * @param limit 限制数量
     * @return 截断后的任务列表
     */
    private List<AdminProcessingTaskItemResponse> limitItems(List<AdminProcessingTaskItemResponse> items, int limit) {
        if (items.size() <= limit) {
            return items;
        }
        return new ArrayList<AdminProcessingTaskItemResponse>(items.subList(0, limit));
    }

    /**
     * 构建同步任务标题。
     *
     * @param runDetail 同步运行详情
     * @return 任务标题
     */
    private String buildSourceSyncTitle(SourceSyncRunDetail runDetail) {
        List<String> sourceNames = safeList(runDetail.getSourceNames());
        String sourceFileTitle = buildFileTitle(sourceNames);
        if (sourceFileTitle != null) {
            return sourceFileTitle;
        }
        if (runDetail.getSourceName() != null && !runDetail.getSourceName().isBlank()) {
            return runDetail.getSourceName();
        }
        return "资料处理任务 #" + String.valueOf(runDetail.getRunId());
    }

    /**
     * 构建独立编译任务标题。
     *
     * @param compileJobRecord 编译作业记录
     * @param source 可见资料源
     * @param uploadRelativeFileNames 上传工作目录里的相对文件名
     * @return 任务标题
     */
    private String buildStandaloneCompileTitle(
            CompileJobRecord compileJobRecord,
            KnowledgeSource source,
            List<String> uploadRelativeFileNames
    ) {
        String uploadTitle = buildFileTitle(uploadRelativeFileNames);
        if (uploadTitle != null) {
            return uploadTitle;
        }
        if (source != null && source.getName() != null && !source.getName().isBlank()) {
            return source.getName();
        }
        String sourceDir = compileJobRecord.getSourceDir();
        if (sourceDir == null || sourceDir.isBlank()) {
            return "处理任务 #" + compileJobRecord.getJobId();
        }
        try {
            Path sourcePath = Path.of(sourceDir);
            String sourceDirectoryTitle = buildFileTitle(listRelativeFileNames(sourcePath));
            if (sourceDirectoryTitle != null) {
                return sourceDirectoryTitle;
            }
            Path fileName = sourcePath.getFileName();
            if (fileName != null && !fileName.toString().isBlank()) {
                return fileName.toString();
            }
        }
        catch (InvalidPathException exception) {
            return sourceDir;
        }
        return sourceDir;
    }

    /**
     * 读取普通目录下的相对文件名。
     *
     * @param sourcePath 资料目录
     * @return 相对文件名列表
     */
    private List<String> listRelativeFileNames(Path sourcePath) {
        if (!Files.isDirectory(sourcePath)) {
            return Collections.emptyList();
        }
        List<String> relativeFileNames = new ArrayList<String>();
        try (Stream<Path> pathStream = Files.walk(sourcePath)) {
            pathStream
                    .filter(Files::isRegularFile)
                    .forEach(path -> relativeFileNames.add(sourcePath.relativize(path).toString().replace("\\", "/")));
        }
        catch (Exception exception) {
            return Collections.emptyList();
        }
        relativeFileNames.sort(Comparator.naturalOrder());
        return relativeFileNames;
    }

    /**
     * 构建来源文件标题。
     *
     * @param sourceNames 来源文件名
     * @return 文件优先标题
     */
    private String buildFileTitle(List<String> sourceNames) {
        List<String> fileNames = normalizeSourceFileNames(sourceNames);
        if (fileNames.isEmpty()) {
            return null;
        }
        if (fileNames.size() == 1) {
            return fileNames.get(0);
        }
        return fileNames.get(0) + " 等 " + String.valueOf(fileNames.size()) + " 个文件";
    }

    /**
     * 规范化来源文件名。
     *
     * @param sourceNames 来源文件名
     * @return 去重后的末级文件名
     */
    private List<String> normalizeSourceFileNames(List<String> sourceNames) {
        Set<String> fileNames = new LinkedHashSet<String>();
        for (String sourceName : safeList(sourceNames)) {
            if (sourceName == null || sourceName.isBlank()) {
                continue;
            }
            String fileName = extractLeafName(sourceName.trim());
            if (!fileName.isBlank()) {
                fileNames.add(fileName);
            }
        }
        return new ArrayList<String>(fileNames);
    }

    /**
     * 提取路径末级名称。
     *
     * @param relativeFileName 相对文件名
     * @return 末级名称
     */
    private String extractLeafName(String relativeFileName) {
        int slashIndex = relativeFileName.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex + 1 < relativeFileName.length()) {
            return relativeFileName.substring(slashIndex + 1);
        }
        return relativeFileName;
    }

    /**
     * 生成独立编译任务的来源预览。
     *
     * @param title 任务标题
     * @return 来源预览列表
     */
    private List<String> buildStandaloneSourceNames(String title) {
        if (title == null || title.isBlank()) {
            return Collections.emptyList();
        }
        List<String> sourceNames = new ArrayList<String>();
        sourceNames.add(title);
        return sourceNames;
    }

    /**
     * 解析独立编译任务的提示文案。
     *
     * @param compileJobRecord 编译作业记录
     * @param derivedStatus 派生状态
     * @return 提示文案
     */
    private String buildStandaloneCompileMessage(CompileJobRecord compileJobRecord, String derivedStatus) {
        String normalizedStatus = normalizeStatus(derivedStatus);
        if (AdminProcessingTaskDisplayStatus.FAILED.matches(normalizedStatus)) {
            return "处理失败";
        }
        if (AdminProcessingTaskDisplayStatus.SUCCEEDED.matches(normalizedStatus)) {
            return "处理成功，资料已写入知识库";
        }
        if (AdminProcessingTaskDisplayStatus.STALLED.matches(normalizedStatus)) {
            return "任务长时间没有新的推进";
        }
        if (AdminProcessingTaskDisplayStatus.RUNNING.matches(normalizedStatus)) {
            String progressMessage = compileJobRecord.getProgressMessage();
            if (progressMessage != null && !progressMessage.isBlank()) {
                return progressMessage;
            }
            return "任务执行中，页面会持续刷新当前进度";
        }
        if (AdminProcessingTaskDisplayStatus.QUEUED.matches(normalizedStatus)) {
            return "已提交处理任务，等待后台执行";
        }
        return "任务状态已更新";
    }

    /**
     * 解析独立编译任务的更新时间。
     *
     * @param compileJobRecord 编译作业记录
     * @return 更新时间
     */
    private OffsetDateTime resolveStandaloneUpdatedAt(CompileJobRecord compileJobRecord) {
        if (compileJobRecord.getProgressUpdatedAt() != null) {
            return compileJobRecord.getProgressUpdatedAt();
        }
        if (compileJobRecord.getLastHeartbeatAt() != null) {
            return compileJobRecord.getLastHeartbeatAt();
        }
        if (compileJobRecord.getStartedAt() != null) {
            return compileJobRecord.getStartedAt();
        }
        return compileJobRecord.getRequestedAt();
    }

    /**
     * 解析 source sync 任务可用动作。
     *
     * @param runDetail 同步运行详情
     * @return 可用动作编码列表
     */
    private List<AdminProcessingTaskActionResponse> resolveSourceSyncActions(SourceSyncRunDetail runDetail) {
        List<AdminProcessingTaskActionResponse> actions = new ArrayList<AdminProcessingTaskActionResponse>();
        String displayStatus = resolveDisplayStatus(runDetail);
        if (AdminProcessingTaskDisplayStatus.WAIT_CONFIRM.matches(displayStatus)) {
            actions.add(new AdminProcessingTaskActionResponse(
                    "CONFIRM_NEW_SOURCE",
                    "确认为新资料源",
                    "ghost-btn",
                    runDetail.getRunId(),
                    runDetail.getSourceId(),
                    "NEW_SOURCE",
                    null,
                    false
            ));
            if (runDetail.getMatchedSourceId() != null) {
                actions.add(new AdminProcessingTaskActionResponse(
                        "CONFIRM_APPEND_SOURCE",
                        "追加到候选资料源",
                        "secondary-btn",
                        runDetail.getRunId(),
                        runDetail.getSourceId(),
                        "EXISTING_SOURCE_APPEND",
                        runDetail.getMatchedSourceId(),
                        false
                ));
                actions.add(new AdminProcessingTaskActionResponse(
                        "CONFIRM_UPDATE_SOURCE",
                        "按更新覆盖候选资料源",
                        "ghost-btn",
                        runDetail.getRunId(),
                        runDetail.getSourceId(),
                        "EXISTING_SOURCE_UPDATE",
                        runDetail.getMatchedSourceId(),
                        false
                ));
            }
        }
        if (runDetail.getSourceId() != null
                && !"UPLOAD".equalsIgnoreCase(runDetail.getSourceType())
                && (AdminProcessingTaskDisplayStatus.isFailed(displayStatus)
                || AdminProcessingTaskDisplayStatus.isSucceeded(displayStatus))) {
            boolean failed = AdminProcessingTaskDisplayStatus.isFailed(displayStatus);
            actions.add(new AdminProcessingTaskActionResponse(
                    "RESYNC_SOURCE",
                    failed ? "重新同步当前资料源" : "再次同步当前资料源",
                    failed ? "secondary-btn" : "ghost-btn",
                    runDetail.getRunId(),
                    runDetail.getSourceId(),
                    null,
                    null,
                    false
            ));
        }
        return actions;
    }

    /**
     * 解析可见资料源。
     *
     * @param compileJobRecord 编译作业记录
     * @return 可见资料源；若仅为 legacy-default 则返回 null
     */
    private KnowledgeSource resolveVisibleSource(CompileJobRecord compileJobRecord) {
        Long sourceId = compileJobRecord.getSourceId();
        if (sourceId == null) {
            return null;
        }
        KnowledgeSource source = sourceService.findById(sourceId).orElse(null);
        if (source == null) {
            return null;
        }
        if ("legacy-default".equals(source.getSourceCode())) {
            return null;
        }
        return source;
    }

    /**
     * 解析统一展示状态。
     *
     * @param item 统一任务
     * @return 展示状态
     */
    private String resolveDisplayStatus(AdminProcessingTaskItemResponse item) {
        return presentationResolver.resolveDisplayStatus(
                item.getCompileDerivedStatus(),
                item.getCompileJobStatus(),
                item.getStatus()
        );
    }

    /**
     * 解析同步运行展示状态。
     *
     * @param runDetail 同步运行详情
     * @return 展示状态
     */
    private String resolveDisplayStatus(SourceSyncRunDetail runDetail) {
        return presentationResolver.resolveDisplayStatus(
                runDetail.getCompileDerivedStatus(),
                runDetail.getCompileJobStatus(),
                runDetail.getStatus()
        );
    }

    /**
     * 判断状态是否属于运行中集合。
     *
     * @param displayStatus 展示状态
     * @return 是否运行中
     */
    private boolean isRunningLike(String displayStatus) {
        return AdminProcessingTaskDisplayStatus.isRunningLike(displayStatus);
    }

    /**
     * 规范化状态字符串。
     *
     * @param value 原始值
     * @return 规范化结果
     */
    private String normalizeStatus(String value) {
        return presentationResolver.normalizeStatus(value);
    }

    /**
     * 安全返回来源文件列表。
     *
     * @param sourceNames 原始来源文件列表
     * @return 安全列表
     */
    private List<String> safeList(List<String> sourceNames) {
        if (sourceNames == null || sourceNames.isEmpty()) {
            return Collections.emptyList();
        }
        return sourceNames;
    }

    /**
     * 格式化时间。
     *
     * @param value 时间值
     * @return ISO 字符串
     */
    private String formatTime(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    /**
     * 当前处理任务排序器。
     *
     * 职责：优先展示最近有推进的活跃任务
     *
     * @author xiexu
     */
    private static class ProcessingTaskComparator implements Comparator<AdminProcessingTaskItemResponse> {

        /**
         * 比较两个任务的排序优先级。
         *
         * @param left 左侧任务
         * @param right 右侧任务
         * @return 比较结果
         */
        @Override
        public int compare(AdminProcessingTaskItemResponse left, AdminProcessingTaskItemResponse right) {
            OffsetDateTime rightTime = resolveSortTime(right);
            OffsetDateTime leftTime = resolveSortTime(left);
            if (rightTime == null && leftTime == null) {
                return 0;
            }
            if (rightTime == null) {
                return -1;
            }
            if (leftTime == null) {
                return 1;
            }
            return rightTime.compareTo(leftTime);
        }

        /**
         * 解析任务排序时间。
         *
         * @param item 任务条目
         * @return 排序时间
         */
        private OffsetDateTime resolveSortTime(AdminProcessingTaskItemResponse item) {
            OffsetDateTime lastHeartbeatAt = parseTime(item.getCompileLastHeartbeatAt());
            if (lastHeartbeatAt != null) {
                return lastHeartbeatAt;
            }
            OffsetDateTime updatedAt = parseTime(item.getUpdatedAt());
            if (updatedAt != null) {
                return updatedAt;
            }
            return parseTime(item.getRequestedAt());
        }

        /**
         * 解析 ISO 时间字符串。
         *
         * @param value 时间字符串
         * @return 时间对象
         */
        private OffsetDateTime parseTime(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return OffsetDateTime.parse(value);
            }
            catch (Exception exception) {
                return null;
            }
        }
    }
}
