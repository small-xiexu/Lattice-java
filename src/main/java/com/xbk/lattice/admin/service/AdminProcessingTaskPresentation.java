package com.xbk.lattice.admin.service;

import com.xbk.lattice.api.admin.AdminProcessingTaskStepResponse;

import java.util.List;

/**
 * 当前处理任务展示聚合结果。
 *
 * 职责：承载后端统一计算后的展示状态、步骤链与说明文案
 *
 * @author xiexu
 */
public class AdminProcessingTaskPresentation {

    private final String displayStatus;

    private final String displayStatusLabel;

    private final String currentStepLabel;

    private final String nextStepHint;

    private final String progressText;

    private final String reasonSummary;

    private final String operationalNote;

    private final List<AdminProcessingTaskStepResponse> progressSteps;

    private final String displayTone;

    private final boolean processingActive;

    private final boolean requiresManualAction;

    private final String noticeTone;

    private final String completionNotice;

    /**
     * 创建当前处理任务展示聚合结果。
     *
     * @param displayStatus 展示状态
     * @param displayStatusLabel 展示状态文案
     * @param currentStepLabel 当前步骤文案
     * @param nextStepHint 下一步提示
     * @param progressText 当前进度文案
     * @param reasonSummary 原因摘要
     * @param operationalNote 任务线索
     * @param progressSteps 完整步骤链
     * @param displayTone 展示色调
     * @param processingActive 是否仍需轮询
     * @param requiresManualAction 是否需要人工处理
     * @param noticeTone 通知语气
     * @param completionNotice 完成提示
     */
    public AdminProcessingTaskPresentation(
            String displayStatus,
            String displayStatusLabel,
            String currentStepLabel,
            String nextStepHint,
            String progressText,
            String reasonSummary,
            String operationalNote,
            List<AdminProcessingTaskStepResponse> progressSteps,
            String displayTone,
            boolean processingActive,
            boolean requiresManualAction,
            String noticeTone,
            String completionNotice
    ) {
        this.displayStatus = displayStatus;
        this.displayStatusLabel = displayStatusLabel;
        this.currentStepLabel = currentStepLabel;
        this.nextStepHint = nextStepHint;
        this.progressText = progressText;
        this.reasonSummary = reasonSummary;
        this.operationalNote = operationalNote;
        this.progressSteps = progressSteps;
        this.displayTone = displayTone;
        this.processingActive = processingActive;
        this.requiresManualAction = requiresManualAction;
        this.noticeTone = noticeTone;
        this.completionNotice = completionNotice;
    }

    public String getDisplayStatus() {
        return displayStatus;
    }

    public String getDisplayStatusLabel() {
        return displayStatusLabel;
    }

    public String getCurrentStepLabel() {
        return currentStepLabel;
    }

    public String getNextStepHint() {
        return nextStepHint;
    }

    public String getProgressText() {
        return progressText;
    }

    public String getReasonSummary() {
        return reasonSummary;
    }

    public String getOperationalNote() {
        return operationalNote;
    }

    public List<AdminProcessingTaskStepResponse> getProgressSteps() {
        return progressSteps;
    }

    public String getDisplayTone() {
        return displayTone;
    }

    public boolean isProcessingActive() {
        return processingActive;
    }

    public boolean isRequiresManualAction() {
        return requiresManualAction;
    }

    public String getNoticeTone() {
        return noticeTone;
    }

    public String getCompletionNotice() {
        return completionNotice;
    }
}
