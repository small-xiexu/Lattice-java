package com.xbk.lattice.admin.service;

import com.xbk.lattice.api.admin.AdminProcessingTaskItemResponse;
import com.xbk.lattice.api.admin.AdminProcessingTaskListResponse;
import com.xbk.lattice.api.admin.AdminProcessingTaskSummaryResponse;
import com.xbk.lattice.compiler.service.CompileJobDerivedStatusResolver;
import com.xbk.lattice.compiler.service.CompileJobService;
import com.xbk.lattice.infra.persistence.CompileJobRecord;
import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.domain.SourceSyncRunDetail;
import com.xbk.lattice.source.service.SourceService;
import com.xbk.lattice.source.service.SourceUploadService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 工作台当前处理任务聚合服务。
 *
 * 职责：统一聚合 source sync runs 与 standalone compile jobs，输出工作台展示所需读模型
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class AdminProcessingTaskService {

    private static final String TASK_TYPE_SOURCE_SYNC = "SOURCE_SYNC";

    private static final String TASK_TYPE_STANDALONE_COMPILE = "STANDALONE_COMPILE";

    private final SourceUploadService sourceUploadService;

    private final CompileJobService compileJobService;

    private final CompileJobDerivedStatusResolver compileJobDerivedStatusResolver;

    private final SourceService sourceService;

    /**
     * 创建工作台当前处理任务聚合服务。
     *
     * @param sourceUploadService 统一上传服务
     * @param compileJobService 编译作业服务
     * @param compileJobDerivedStatusResolver 编译作业派生状态解析器
     * @param sourceService 资料源服务
     */
    public AdminProcessingTaskService(
            SourceUploadService sourceUploadService,
            CompileJobService compileJobService,
            CompileJobDerivedStatusResolver compileJobDerivedStatusResolver,
            SourceService sourceService
    ) {
        this.sourceUploadService = sourceUploadService;
        this.compileJobService = compileJobService;
        this.compileJobDerivedStatusResolver = compileJobDerivedStatusResolver;
        this.sourceService = sourceService;
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
        List<SourceSyncRunDetail> recentRuns = sourceUploadService.listRecentRunDetails(resolvedLimit);
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
     * 将同步运行详情映射为统一任务。
     *
     * @param runDetail 同步运行详情
     * @return 统一任务
     */
    private AdminProcessingTaskItemResponse toSourceSyncTask(SourceSyncRunDetail runDetail) {
        String title = buildSourceSyncTitle(runDetail);
        List<String> sourceNames = safeList(runDetail.getSourceNames());
        List<String> actions = resolveSourceSyncActions(runDetail);
        return new AdminProcessingTaskItemResponse(
                "source-run:" + String.valueOf(runDetail.getRunId()),
                TASK_TYPE_SOURCE_SYNC,
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
        String title = buildStandaloneCompileTitle(compileJobRecord, source);
        String sourceName = source == null ? null : source.getName();
        Long sourceId = source == null ? null : source.getId();
        String sourceType = source == null ? "DIRECT_COMPILE" : source.getSourceType();
        List<String> sourceNames = buildStandaloneSourceNames(title);
        String derivedStatus = compileJobDerivedStatusResolver.resolve(compileJobRecord);
        String message = buildStandaloneCompileMessage(compileJobRecord, derivedStatus);
        OffsetDateTime updatedAt = resolveStandaloneUpdatedAt(compileJobRecord);
        return new AdminProcessingTaskItemResponse(
                "compile-job:" + compileJobRecord.getJobId(),
                TASK_TYPE_STANDALONE_COMPILE,
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
        int runningCount = 0;
        int waitingCount = 0;
        int stalledCount = 0;
        int succeededCount = 0;
        int failedCount = 0;
        for (AdminProcessingTaskItemResponse item : items) {
            String displayStatus = resolveDisplayStatus(item);
            if ("WAIT_CONFIRM".equals(displayStatus)) {
                waitingCount++;
            }
            else if ("STALLED".equals(displayStatus)) {
                stalledCount++;
            }
            else if ("SUCCEEDED".equals(displayStatus) || "SKIPPED_NO_CHANGE".equals(displayStatus)) {
                succeededCount++;
            }
            else if ("FAILED".equals(displayStatus)) {
                failedCount++;
            }
            else if (isRunningLike(displayStatus)) {
                runningCount++;
            }
        }
        return new AdminProcessingTaskSummaryResponse(
                runningCount,
                waitingCount,
                stalledCount,
                succeededCount,
                failedCount
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
        if (runDetail.getSourceName() != null && !runDetail.getSourceName().isBlank()) {
            return runDetail.getSourceName();
        }
        List<String> sourceNames = safeList(runDetail.getSourceNames());
        if (!sourceNames.isEmpty()) {
            if (sourceNames.size() == 1) {
                return sourceNames.get(0);
            }
            return sourceNames.get(0) + " 等 " + String.valueOf(sourceNames.size()) + " 个文件";
        }
        return "资料处理任务 #" + String.valueOf(runDetail.getRunId());
    }

    /**
     * 构建独立编译任务标题。
     *
     * @param compileJobRecord 编译作业记录
     * @param source 可见资料源
     * @return 任务标题
     */
    private String buildStandaloneCompileTitle(CompileJobRecord compileJobRecord, KnowledgeSource source) {
        if (source != null && source.getName() != null && !source.getName().isBlank()) {
            return source.getName();
        }
        String sourceDir = compileJobRecord.getSourceDir();
        if (sourceDir == null || sourceDir.isBlank()) {
            return "编译任务 #" + compileJobRecord.getJobId();
        }
        try {
            Path sourcePath = Path.of(sourceDir);
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
        if ("FAILED".equals(normalizedStatus)) {
            return "编译失败";
        }
        if ("SUCCEEDED".equals(normalizedStatus)) {
            return "编译成功，资料已完成入库";
        }
        if ("STALLED".equals(normalizedStatus)) {
            return "编译任务长时间没有新的推进";
        }
        if ("RUNNING".equals(normalizedStatus)) {
            String progressMessage = compileJobRecord.getProgressMessage();
            if (progressMessage != null && !progressMessage.isBlank()) {
                return progressMessage;
            }
            return "编译任务执行中，页面会持续刷新当前进度";
        }
        if ("QUEUED".equals(normalizedStatus)) {
            return "已提交编译任务，等待后台执行";
        }
        return "编译状态已更新";
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
    private List<String> resolveSourceSyncActions(SourceSyncRunDetail runDetail) {
        List<String> actions = new ArrayList<String>();
        String displayStatus = resolveDisplayStatus(runDetail);
        if ("WAIT_CONFIRM".equals(displayStatus)) {
            actions.add("CONFIRM_NEW_SOURCE");
            if (runDetail.getMatchedSourceId() != null) {
                actions.add("CONFIRM_APPEND_SOURCE");
                actions.add("CONFIRM_UPDATE_SOURCE");
            }
        }
        if (runDetail.getSourceId() != null
                && ("FAILED".equals(displayStatus)
                || "STALLED".equals(displayStatus)
                || "SUCCEEDED".equals(displayStatus)
                || "SKIPPED_NO_CHANGE".equals(displayStatus))) {
            actions.add("RESYNC_SOURCE");
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
        String derivedStatus = normalizeStatus(item.getCompileDerivedStatus());
        if (derivedStatus != null) {
            return derivedStatus;
        }
        String compileStatus = normalizeStatus(item.getCompileJobStatus());
        if (compileStatus != null) {
            return compileStatus;
        }
        return normalizeStatus(item.getStatus());
    }

    /**
     * 解析同步运行展示状态。
     *
     * @param runDetail 同步运行详情
     * @return 展示状态
     */
    private String resolveDisplayStatus(SourceSyncRunDetail runDetail) {
        String derivedStatus = normalizeStatus(runDetail.getCompileDerivedStatus());
        if (derivedStatus != null) {
            return derivedStatus;
        }
        String compileStatus = normalizeStatus(runDetail.getCompileJobStatus());
        if (compileStatus != null) {
            return compileStatus;
        }
        return normalizeStatus(runDetail.getStatus());
    }

    /**
     * 判断状态是否属于运行中集合。
     *
     * @param displayStatus 展示状态
     * @return 是否运行中
     */
    private boolean isRunningLike(String displayStatus) {
        return "QUEUED".equals(displayStatus)
                || "MATCHING".equals(displayStatus)
                || "MATERIALIZING".equals(displayStatus)
                || "COMPILE_QUEUED".equals(displayStatus)
                || "RUNNING".equals(displayStatus);
    }

    /**
     * 规范化状态字符串。
     *
     * @param value 原始值
     * @return 规范化结果
     */
    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
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
