package com.xbk.lattice.admin.service;

import com.xbk.lattice.api.admin.AdminProcessingTaskStepResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 当前处理任务展示解析器。
 *
 * 职责：统一将后端原始任务状态解析为前端直接可展示的展示态、步骤链与摘要文案
 *
 * @author xiexu
 */
@Component
public class AdminProcessingTaskPresentationResolver {

    public static final String TASK_TYPE_SOURCE_SYNC = "SOURCE_SYNC";

    public static final String TASK_TYPE_STANDALONE_COMPILE = "STANDALONE_COMPILE";

    /**
     * 解析统一任务展示结果。
     *
     * @param taskType 任务类型
     * @param displayStatus 展示状态
     * @param currentStep 当前步骤
     * @param progressCurrent 当前进度
     * @param progressTotal 总进度
     * @param progressMessage 进度提示
     * @param errorCode 错误码
     * @param message 提示信息
     * @param errorMessage 错误信息
     * @param sourceId 资料源标识
     * @return 展示结果
     */
    public AdminProcessingTaskPresentation resolve(
            String taskType,
            String displayStatus,
            String currentStep,
            Integer progressCurrent,
            Integer progressTotal,
            String progressMessage,
            String errorCode,
            String message,
            String errorMessage,
            Long sourceId
    ) {
        String normalizedDisplayStatus = normalizeStatus(displayStatus);
        String currentStepLabel = resolveCurrentStepLabel(taskType, normalizedDisplayStatus, currentStep);
        String nextStepHint = resolveNextStepHint(taskType, normalizedDisplayStatus, sourceId);
        String progressText = resolveProgressText(
                normalizedDisplayStatus,
                progressCurrent,
                progressTotal,
                progressMessage
        );
        String reasonSummary = buildReasonSummary(
                taskType,
                normalizedDisplayStatus,
                errorCode,
                message,
                errorMessage,
                progressMessage
        );
        String operationalNote = buildOperationalNote(
                normalizedDisplayStatus,
                currentStepLabel,
                nextStepHint,
                errorCode
        );
        List<AdminProcessingTaskStepResponse> progressSteps = buildProgressSteps(
                taskType,
                normalizedDisplayStatus,
                currentStep
        );
        String displayTone = resolveDisplayTone(normalizedDisplayStatus);
        boolean processingActive = isProcessingActive(normalizedDisplayStatus);
        boolean requiresManualAction = isRequiresManualAction(normalizedDisplayStatus);
        String noticeTone = resolveNoticeTone(normalizedDisplayStatus);
        String completionNotice = buildCompletionNotice(
                normalizedDisplayStatus,
                reasonSummary,
                taskType
        );
        return new AdminProcessingTaskPresentation(
                normalizedDisplayStatus,
                getStatusLabel(normalizedDisplayStatus),
                currentStepLabel,
                nextStepHint,
                progressText,
                reasonSummary,
                operationalNote,
                progressSteps,
                displayTone,
                processingActive,
                requiresManualAction,
                noticeTone,
                completionNotice
        );
    }

    /**
     * 规范化状态字符串。
     *
     * @param value 原始值
     * @return 规范化结果
     */
    public String normalizeStatus(String value) {
        return AdminProcessingTaskDisplayStatus.normalize(value);
    }

    /**
     * 按后端统一优先级解析展示状态。
     *
     * @param compileDerivedStatus 编译派生状态
     * @param compileJobStatus 编译作业状态
     * @param status 主状态
     * @return 展示状态
     */
    public String resolveDisplayStatus(String compileDerivedStatus, String compileJobStatus, String status) {
        String normalizedDerivedStatus = normalizeStatus(compileDerivedStatus);
        if (normalizedDerivedStatus != null) {
            return normalizedDerivedStatus;
        }
        String normalizedCompileJobStatus = normalizeStatus(compileJobStatus);
        if (normalizedCompileJobStatus != null) {
            return normalizedCompileJobStatus;
        }
        return normalizeStatus(status);
    }

    /**
     * 压缩展示文案。
     *
     * @param value 原始文案
     * @return 压缩后的文案
     */
    public String compactDisplayMessage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.isBlank() ? null : compact;
    }

    /**
     * 解析任务展示态文案。
     *
     * @param displayStatus 展示状态
     * @return 展示态文案
     */
    public String getStatusLabel(String displayStatus) {
        AdminProcessingTaskDisplayStatus status = AdminProcessingTaskDisplayStatus.fromCode(displayStatus);
        if (status != null) {
            return status.getLabel();
        }
        return displayStatus == null ? "未知" : displayStatus;
    }

    /**
     * 解析展示色调。
     *
     * @param displayStatus 展示状态
     * @return 展示色调
     */
    public String resolveDisplayTone(String displayStatus) {
        AdminProcessingTaskDisplayStatus status = AdminProcessingTaskDisplayStatus.fromCode(displayStatus);
        return status == null ? "warning" : status.getTone();
    }

    /**
     * 判断任务是否仍需继续轮询。
     *
     * @param displayStatus 展示状态
     * @return 是否仍处于处理中
     */
    public boolean isProcessingActive(String displayStatus) {
        return AdminProcessingTaskDisplayStatus.isRunningLike(displayStatus);
    }

    /**
     * 判断任务是否需要人工处理。
     *
     * @param displayStatus 展示状态
     * @return 是否需要人工处理
     */
    public boolean isRequiresManualAction(String displayStatus) {
        return AdminProcessingTaskDisplayStatus.WAIT_CONFIRM.matches(displayStatus);
    }

    /**
     * 解析通知色调。
     *
     * @param displayStatus 展示状态
     * @return 通知色调
     */
    public String resolveNoticeTone(String displayStatus) {
        AdminProcessingTaskDisplayStatus status = AdminProcessingTaskDisplayStatus.fromCode(displayStatus);
        return status == null ? "success" : status.getNoticeTone();
    }

    /**
     * 构建完成通知文案。
     *
     * @param displayStatus 展示状态
     * @param reasonSummary 原因摘要
     * @param taskType 任务类型
     * @return 完成通知文案
     */
    public String buildCompletionNotice(
            String displayStatus,
            String reasonSummary,
            String taskType
    ) {
        if (reasonSummary != null && !reasonSummary.isBlank()) {
            return reasonSummary;
        }
        if (AdminProcessingTaskDisplayStatus.WAIT_CONFIRM.matches(displayStatus)) {
            return "资料包需要人工确认归并方式，请在“当前处理任务”卡片中处理。";
        }
        if (AdminProcessingTaskDisplayStatus.SUCCEEDED.matches(displayStatus)) {
            return TASK_TYPE_STANDALONE_COMPILE.equals(taskType) ? "任务已完成，资料已写入知识库。" : "资料已经完成解析并写入知识库。";
        }
        if (AdminProcessingTaskDisplayStatus.SKIPPED_NO_CHANGE.matches(displayStatus)) {
            return "资料包与最近一次成功快照一致，本次无需重新处理。";
        }
        String statusLabel = getStatusLabel(displayStatus);
        return statusLabel == null || statusLabel.isBlank() ? "任务状态已更新。" : "当前状态：" + statusLabel + "。";
    }

    /**
     * 解析当前步骤文案。
     *
     * @param taskType 任务类型
     * @param displayStatus 展示状态
     * @param currentStep 当前真实步骤
     * @return 当前步骤文案
     */
    public String resolveCurrentStepLabel(String taskType, String displayStatus, String currentStep) {
        String normalizedStep = normalizeStatus(currentStep);
        if (normalizedStep != null) {
            return resolveStepLabel(taskType, normalizedStep);
        }
        if (AdminProcessingTaskDisplayStatus.RUNNING.matches(displayStatus)) {
            return "处理中";
        }
        if (AdminProcessingTaskDisplayStatus.COMPILE_QUEUED.matches(displayStatus)) {
            return "等待内容生成";
        }
        if (AdminProcessingTaskDisplayStatus.WAIT_CONFIRM.matches(displayStatus)) {
            return "等待人工确认";
        }
        if (AdminProcessingTaskDisplayStatus.MATCHING.matches(displayStatus)) {
            return "自动识别中";
        }
        if (AdminProcessingTaskDisplayStatus.MATERIALIZING.matches(displayStatus)) {
            return "资料整理中";
        }
        if (AdminProcessingTaskDisplayStatus.SKIPPED_NO_CHANGE.matches(displayStatus)) {
            return "已完成";
        }
        if (AdminProcessingTaskDisplayStatus.SUCCEEDED.matches(displayStatus)) {
            return "已完成";
        }
        if (AdminProcessingTaskDisplayStatus.FAILED.matches(displayStatus)) {
            return "处理失败";
        }
        if (AdminProcessingTaskDisplayStatus.STALLED.matches(displayStatus)) {
            return "处理失败";
        }
        if (AdminProcessingTaskDisplayStatus.QUEUED.matches(displayStatus)) {
            return "处理中";
        }
        return getStatusLabel(displayStatus);
    }

    /**
     * 解析下一步提示。
     *
     * @param taskType 任务类型
     * @param displayStatus 展示状态
     * @param sourceId 资料源标识
     * @return 下一步提示
     */
    public String resolveNextStepHint(String taskType, String displayStatus, Long sourceId) {
        if (AdminProcessingTaskDisplayStatus.MATCHING.matches(displayStatus)) {
            return "等待识别结果判断新建、更新还是追加";
        }
        if (AdminProcessingTaskDisplayStatus.WAIT_CONFIRM.matches(displayStatus)) {
            return "选择新建或合并方式";
        }
        if (AdminProcessingTaskDisplayStatus.MATERIALIZING.matches(displayStatus)) {
            return "继续等待资料复制与整理完成";
        }
        if (AdminProcessingTaskDisplayStatus.COMPILE_QUEUED.matches(displayStatus)
                || AdminProcessingTaskDisplayStatus.QUEUED.matches(displayStatus)) {
            return "等待后台 worker 开始执行";
        }
        if (AdminProcessingTaskDisplayStatus.RUNNING.matches(displayStatus)) {
            return "继续等待当前真实步骤推进";
        }
        if (AdminProcessingTaskDisplayStatus.STALLED.matches(displayStatus)) {
            return sourceId != null ? "查看原因摘要后重新同步资料源" : "查看原因摘要后重新提交任务";
        }
        if (AdminProcessingTaskDisplayStatus.FAILED.matches(displayStatus)) {
            return sourceId != null ? "检查原因摘要后重新同步资料源" : "检查资料后重新提交任务";
        }
        if (AdminProcessingTaskDisplayStatus.SUCCEEDED.matches(displayStatus)) {
            return "可以查看已入库内容或继续问答";
        }
        if (AdminProcessingTaskDisplayStatus.SKIPPED_NO_CHANGE.matches(displayStatus)) {
            return "本次无需重新处理";
        }
        return "等待系统继续处理";
    }

    /**
     * 解析当前进度文案。
     *
     * @param displayStatus 展示状态
     * @param progressCurrent 当前进度
     * @param progressTotal 总进度
     * @param progressMessage 进度提示
     * @return 当前进度文案
     */
    public String resolveProgressText(
            String displayStatus,
            Integer progressCurrent,
            Integer progressTotal,
            String progressMessage
    ) {
        if (progressCurrent != null && progressTotal != null && progressCurrent.intValue() > 0 && progressTotal.intValue() > 0) {
            return String.valueOf(progressCurrent) + " / " + String.valueOf(progressTotal)
                    + (progressMessage == null || progressMessage.isBlank() ? "" : " · " + compactDisplayMessage(progressMessage));
        }
        if (progressMessage != null && !progressMessage.isBlank()) {
            return compactDisplayMessage(progressMessage);
        }
        if (AdminProcessingTaskDisplayStatus.WAIT_CONFIRM.matches(displayStatus)) {
            return "等待人工确认";
        }
        if (AdminProcessingTaskDisplayStatus.COMPILE_QUEUED.matches(displayStatus)) {
            return "等待后台 worker 领取";
        }
        if (AdminProcessingTaskDisplayStatus.SKIPPED_NO_CHANGE.matches(displayStatus)) {
            return "无需重新处理";
        }
        return "等待下一步刷新";
    }

    /**
     * 构建原因摘要。
     *
     * @param taskType 任务类型
     * @param displayStatus 展示状态
     * @param errorCode 错误码
     * @param message 提示信息
     * @param errorMessage 错误信息
     * @param progressMessage 进度提示
     * @return 原因摘要
     */
    public String buildReasonSummary(
            String taskType,
            String displayStatus,
            String errorCode,
            String message,
            String errorMessage,
            String progressMessage
    ) {
        if (AdminProcessingTaskDisplayStatus.WAIT_CONFIRM.matches(displayStatus)) {
            String compactMessage = compactDisplayMessage(message);
            return compactMessage == null ? "系统无法自动判断该资料包应新建还是合并，需要人工确认。" : compactMessage;
        }
        if (AdminProcessingTaskDisplayStatus.SKIPPED_NO_CHANGE.matches(displayStatus)) {
            return "资料包与最近一次成功快照一致，本次无需重新处理。";
        }
        if (errorCode != null && !errorCode.isBlank()) {
            return resolveErrorCodeSummary(errorCode);
        }
        if (AdminProcessingTaskDisplayStatus.STALLED.matches(displayStatus)) {
            return "任务长时间没有新的心跳或进度更新，建议重新同步资料源。";
        }
        if (AdminProcessingTaskDisplayStatus.FAILED.matches(displayStatus)) {
            String compactErrorMessage = compactDisplayMessage(errorMessage);
            return compactErrorMessage == null ? "任务处理失败，请检查资料源配置、网络链路或上传内容。" : compactErrorMessage;
        }
        if (AdminProcessingTaskDisplayStatus.RUNNING.matches(displayStatus)) {
            String compactProgressMessage = compactDisplayMessage(progressMessage);
            return compactProgressMessage == null ? "系统仍在推进当前步骤。" : compactProgressMessage;
        }
        if (AdminProcessingTaskDisplayStatus.COMPILE_QUEUED.matches(displayStatus)) {
            return "识别与整理已完成，正在等待内容生成任务开始执行。";
        }
        if (AdminProcessingTaskDisplayStatus.SUCCEEDED.matches(displayStatus)) {
            String compactMessage = compactDisplayMessage(message);
            return compactMessage == null
                    ? (TASK_TYPE_STANDALONE_COMPILE.equals(taskType) ? "任务已完成，资料已写入知识库。" : "资料已经完成解析并写入知识库。")
                    : compactMessage;
        }
        String compactMessage = compactDisplayMessage(message);
        return compactMessage == null ? "同步状态已更新。" : compactMessage;
    }

    /**
     * 构建任务线索文案。
     *
     * @param displayStatus 展示状态
     * @param currentStepLabel 当前步骤文案
     * @param nextStepHint 下一步提示
     * @param errorCode 错误码
     * @return 任务线索文案
     */
    public String buildOperationalNote(
            String displayStatus,
            String currentStepLabel,
            String nextStepHint,
            String errorCode
    ) {
        List<String> parts = new ArrayList<String>();
        if (displayStatus != null) {
            parts.add("运行态：" + getStatusLabel(displayStatus));
        }
        if (currentStepLabel != null && !currentStepLabel.isBlank()) {
            parts.add("当前步骤：" + currentStepLabel);
        }
        if (nextStepHint != null && !nextStepHint.isBlank()) {
            parts.add("下一步：" + nextStepHint);
        }
        if (errorCode != null && !errorCode.isBlank()) {
            parts.add("错误码：" + normalizeStatus(errorCode));
        }
        return parts.isEmpty() ? "系统正在继续处理当前任务。" : String.join(" · ", parts);
    }

    /**
     * 构建统一任务的完整展示步骤链。
     *
     * @param taskType 任务类型
     * @param displayStatus 展示状态
     * @param currentStep 当前真实步骤
     * @return 完整步骤链
     */
    public List<AdminProcessingTaskStepResponse> buildProgressSteps(
            String taskType,
            String displayStatus,
            String currentStep
    ) {
        List<String> stepKeys = TASK_TYPE_STANDALONE_COMPILE.equals(taskType)
                ? Arrays.asList(
                        AdminProcessingTaskStep.TASK_SUBMITTED.getCode(),
                        AdminProcessingTaskStep.COMPILE_NEW_ARTICLES.getCode(),
                        AdminProcessingTaskStep.REVIEW_ARTICLES.getCode(),
                        AdminProcessingTaskStep.FINALIZE_JOB.getCode()
                )
                : Arrays.asList(
                        AdminProcessingTaskStep.TASK_RECEIVED.getCode(),
                        AdminProcessingTaskStep.COMPILE_NEW_ARTICLES.getCode(),
                        AdminProcessingTaskStep.REVIEW_ARTICLES.getCode(),
                        AdminProcessingTaskStep.FINALIZE_JOB.getCode()
                );
        List<AdminProcessingTaskStepResponse> steps = new ArrayList<AdminProcessingTaskStepResponse>();
        int activeIndex = resolveActiveStepIndex(taskType, displayStatus, currentStep, stepKeys);
        for (int index = 0; index < stepKeys.size(); index++) {
            String stepKey = stepKeys.get(index);
            String stepStatus = resolveStepStatus(displayStatus, activeIndex, index);
            steps.add(new AdminProcessingTaskStepResponse(
                    stepKey,
                    resolveStepLabel(taskType, stepKey),
                    stepStatus,
                    resolveStepDetail(taskType, stepKey, displayStatus, currentStep, activeIndex == index)
            ));
        }
        return steps;
    }

    private int resolveActiveStepIndex(String taskType, String displayStatus, String currentStep, List<String> stepKeys) {
        String normalizedStep = normalizeStatus(currentStep);
        if (normalizedStep != null) {
            int stepIndex = stepKeys.indexOf(normalizedStep);
            if (stepIndex >= 0) {
                return stepIndex;
            }
        }
        if (TASK_TYPE_STANDALONE_COMPILE.equals(taskType)) {
            if (AdminProcessingTaskDisplayStatus.QUEUED.matches(displayStatus)) {
                return 0;
            }
            if (AdminProcessingTaskDisplayStatus.COMPILE_QUEUED.matches(displayStatus)) {
                return stepKeys.indexOf(AdminProcessingTaskStep.COMPILE_NEW_ARTICLES.getCode());
            }
            if (AdminProcessingTaskDisplayStatus.RUNNING.matches(displayStatus)
                    || AdminProcessingTaskDisplayStatus.STALLED.matches(displayStatus)
                    || AdminProcessingTaskDisplayStatus.FAILED.matches(displayStatus)) {
                String normalizedCurrentStep = normalizeStatus(currentStep);
                if (AdminProcessingTaskStep.isQualityStep(normalizedCurrentStep)) {
                    return stepKeys.indexOf(AdminProcessingTaskStep.REVIEW_ARTICLES.getCode());
                }
                if (AdminProcessingTaskStep.isKnowledgeWriteStep(normalizedCurrentStep)) {
                    return stepKeys.indexOf(AdminProcessingTaskStep.FINALIZE_JOB.getCode());
                }
                return stepKeys.indexOf(AdminProcessingTaskStep.COMPILE_NEW_ARTICLES.getCode());
            }
            if (AdminProcessingTaskDisplayStatus.isSucceeded(displayStatus)) {
                return stepKeys.size() - 1;
            }
            return 0;
        }
        if (AdminProcessingTaskDisplayStatus.QUEUED.matches(displayStatus)) {
            return 0;
        }
        if (AdminProcessingTaskDisplayStatus.MATCHING.matches(displayStatus)) {
            return 0;
        }
        if (AdminProcessingTaskDisplayStatus.WAIT_CONFIRM.matches(displayStatus)) {
            return stepKeys.indexOf(AdminProcessingTaskStep.TASK_RECEIVED.getCode());
        }
        if (AdminProcessingTaskDisplayStatus.MATERIALIZING.matches(displayStatus)) {
            return stepKeys.indexOf(AdminProcessingTaskStep.TASK_RECEIVED.getCode());
        }
        if (AdminProcessingTaskDisplayStatus.COMPILE_QUEUED.matches(displayStatus)) {
            return stepKeys.indexOf(AdminProcessingTaskStep.COMPILE_NEW_ARTICLES.getCode());
        }
        if (AdminProcessingTaskDisplayStatus.RUNNING.matches(displayStatus)
                || AdminProcessingTaskDisplayStatus.STALLED.matches(displayStatus)
                || AdminProcessingTaskDisplayStatus.FAILED.matches(displayStatus)) {
            String normalizedCurrentStep = normalizeStatus(currentStep);
            if (AdminProcessingTaskStep.isQualityStep(normalizedCurrentStep)) {
                return stepKeys.indexOf(AdminProcessingTaskStep.REVIEW_ARTICLES.getCode());
            }
            if (AdminProcessingTaskStep.isKnowledgeWriteStep(normalizedCurrentStep)) {
                return stepKeys.indexOf(AdminProcessingTaskStep.FINALIZE_JOB.getCode());
            }
            return stepKeys.indexOf(AdminProcessingTaskStep.COMPILE_NEW_ARTICLES.getCode());
        }
        if (AdminProcessingTaskDisplayStatus.isSucceeded(displayStatus)) {
            return stepKeys.size() - 1;
        }
        return 0;
    }

    private String resolveStepStatus(String displayStatus, int activeIndex, int currentIndex) {
        if (AdminProcessingTaskDisplayStatus.isSucceeded(displayStatus)) {
            return AdminProcessingTaskStepStatus.COMPLETED.getCode();
        }
        if (currentIndex < activeIndex) {
            return AdminProcessingTaskStepStatus.COMPLETED.getCode();
        }
        if (currentIndex == activeIndex) {
            return AdminProcessingTaskDisplayStatus.isFailed(displayStatus)
                    ? AdminProcessingTaskStepStatus.FAILED.getCode()
                    : AdminProcessingTaskStepStatus.ACTIVE.getCode();
        }
        return AdminProcessingTaskStepStatus.PENDING.getCode();
    }

    private String resolveStepLabel(String taskType, String stepKey) {
        String normalizedStepKey = normalizeStatus(stepKey);
        AdminProcessingTaskStep step = AdminProcessingTaskStep.fromCode(normalizedStepKey);
        if (step != null) {
            return step.getLabel();
        }
        return normalizedStepKey == null ? "未知步骤" : normalizedStepKey;
    }

    private String resolveStepDetail(
            String taskType,
            String stepKey,
            String displayStatus,
            String currentStep,
            boolean active
    ) {
        String normalizedStepKey = normalizeStatus(stepKey);
        String normalizedCurrentStep = normalizeStatus(currentStep);
        if (active && normalizedCurrentStep != null && normalizedCurrentStep.equals(normalizedStepKey)) {
            return resolveSpecificStateLabel(taskType, normalizedCurrentStep, displayStatus);
        }
        if (AdminProcessingTaskStep.WAIT_CONFIRM.getCode().equals(normalizedStepKey)
                && AdminProcessingTaskDisplayStatus.WAIT_CONFIRM.matches(displayStatus)) {
            return "等待人工决策";
        }
        if (AdminProcessingTaskStep.MATCHING.getCode().equals(normalizedStepKey)
                && AdminProcessingTaskDisplayStatus.MATCHING.matches(displayStatus)) {
            return "正在识别新建、更新或追加";
        }
        if (AdminProcessingTaskStep.TASK_RECEIVED.getCode().equals(normalizedStepKey)
                && AdminProcessingTaskDisplayStatus.WAIT_CONFIRM.matches(displayStatus)) {
            return "等待人工确认";
        }
        if (AdminProcessingTaskStep.COMPILE_NEW_ARTICLES.getCode().equals(normalizedStepKey)
                && AdminProcessingTaskDisplayStatus.COMPILE_QUEUED.matches(displayStatus)) {
            return "等待后台 worker 领取";
        }
        if (AdminProcessingTaskStep.COMPILE_NEW_ARTICLES.getCode().equals(normalizedStepKey)
                && isRunningFailedOrStalled(displayStatus)) {
            if (AdminProcessingTaskStep.COMPILE_NEW_ARTICLES.getCode().equals(normalizedCurrentStep)) {
                return "正在生成文章草稿";
            }
        }
        if (AdminProcessingTaskStep.REVIEW_ARTICLES.getCode().equals(normalizedStepKey)
                && isRunningFailedOrStalled(displayStatus)) {
            if (AdminProcessingTaskStep.isQualityStep(normalizedCurrentStep)) {
                return resolveSpecificStateLabel(taskType, normalizedCurrentStep, displayStatus);
            }
        }
        if (AdminProcessingTaskStep.FINALIZE_JOB.getCode().equals(normalizedStepKey)
                && isRunningFailedOrStalled(displayStatus)) {
            if (AdminProcessingTaskStep.isKnowledgeWriteStep(normalizedCurrentStep)) {
                return resolveSpecificStateLabel(taskType, normalizedCurrentStep, displayStatus);
            }
        }
        if (TASK_TYPE_STANDALONE_COMPILE.equals(taskType)
                && AdminProcessingTaskStep.REVIEW_ARTICLES.getCode().equals(normalizedStepKey)
                && isRunningFailedOrStalled(displayStatus)
                && AdminProcessingTaskStep.FIX_REVIEW_ISSUES.getCode().equals(normalizedCurrentStep)) {
            return resolveSpecificStateLabel(taskType, normalizedCurrentStep, displayStatus);
        }
        if (AdminProcessingTaskStep.FINALIZE_JOB.getCode().equals(normalizedStepKey)
                && AdminProcessingTaskDisplayStatus.SKIPPED_NO_CHANGE.matches(displayStatus)) {
            return "无变化跳过";
        }
        return "";
    }

    private String resolveSpecificStateLabel(String taskType, String currentStep, String displayStatus) {
        String normalizedStep = normalizeStatus(currentStep);
        if (AdminProcessingTaskStep.COMPILE_NEW_ARTICLES.getCode().equals(normalizedStep)) {
            return "正在生成文章草稿";
        }
        if (AdminProcessingTaskStep.REVIEW_ARTICLES.getCode().equals(normalizedStep)) {
            return "正在审查文章草稿";
        }
        if (AdminProcessingTaskStep.FIX_REVIEW_ISSUES.getCode().equals(normalizedStep)) {
            return "正在修复审查问题";
        }
        if (AdminProcessingTaskStep.PERSIST_ARTICLES.getCode().equals(normalizedStep)) {
            return "正在写入知识库";
        }
        if (AdminProcessingTaskStep.REBUILD_ARTICLE_CHUNKS.getCode().equals(normalizedStep)) {
            return "正在重建知识切片";
        }
        if (AdminProcessingTaskStep.REFRESH_VECTOR_INDEX.getCode().equals(normalizedStep)
                || AdminProcessingTaskStep.REBUILD_ARTICLE_VECTORS.getCode().equals(normalizedStep)
                || AdminProcessingTaskStep.REBUILD_SOURCE_VECTORS.getCode().equals(normalizedStep)) {
            return "正在刷新向量索引";
        }
        if (AdminProcessingTaskStep.GENERATE_SYNTHESIS_ARTIFACTS.getCode().equals(normalizedStep)) {
            return "正在生成综合产物";
        }
        if (AdminProcessingTaskStep.CAPTURE_REPO_SNAPSHOT.getCode().equals(normalizedStep)
                || AdminProcessingTaskStep.FINALIZE_JOB.getCode().equals(normalizedStep)) {
            return "入库完成";
        }
        if (AdminProcessingTaskDisplayStatus.SKIPPED_NO_CHANGE.matches(displayStatus)) {
            return "无变化跳过";
        }
        if (AdminProcessingTaskDisplayStatus.WAIT_CONFIRM.matches(displayStatus)) {
            return "等待人工确认";
        }
        return resolveStepLabel(taskType, currentStep);
    }

    private boolean isRunningFailedOrStalled(String displayStatus) {
        return AdminProcessingTaskDisplayStatus.RUNNING.matches(displayStatus)
                || AdminProcessingTaskDisplayStatus.FAILED.matches(displayStatus)
                || AdminProcessingTaskDisplayStatus.STALLED.matches(displayStatus);
    }

    /**
     * 解析稳定错误码摘要。
     *
     * @param errorCode 错误码
     * @return 错误码摘要
     */
    public String resolveErrorCodeSummary(String errorCode) {
        String normalizedErrorCode = normalizeStatus(errorCode);
        if ("LLM_TRANSPORT_ERROR".equals(normalizedErrorCode)) {
            return "调用模型时发生链路异常，请检查网络、路由配置或模型服务可用性。";
        }
        if ("COMPILE_EXECUTION_FAILED".equals(normalizedErrorCode)) {
            return "编译执行过程中出现异常，请结合当前步骤和错误信息排查。";
        }
        if ("SOURCE_SYNC_CONFLICT".equals(normalizedErrorCode)) {
            return "当前资料源已有运行中的同步任务，请等待完成后再试。";
        }
        return normalizedErrorCode == null ? "任务执行失败。" : "任务执行失败，错误码：" + normalizedErrorCode;
    }
}
