package com.xbk.lattice.source.domain;

/**
 * 资料源自动识别决策结果。
 *
 * 职责：承载统一上传识别后的决策、动作与收口提示
 *
 * @author xiexu
 */
public class SourceDecisionResult {

    private final String resolverMode;

    private final String resolverDecision;

    private final String syncAction;

    private final Long matchedSourceId;

    private final boolean waitConfirm;

    private final boolean skippedNoChange;

    private final String message;

    /**
     * 创建自动识别决策结果。
     *
     * @param resolverMode 识别模式
     * @param resolverDecision 决策结果
     * @param syncAction 同步动作
     * @param matchedSourceId 命中的资料源主键
     * @param waitConfirm 是否等待人工确认
     * @param skippedNoChange 是否无变更跳过
     * @param message 提示消息
     */
    public SourceDecisionResult(
            String resolverMode,
            String resolverDecision,
            String syncAction,
            Long matchedSourceId,
            boolean waitConfirm,
            boolean skippedNoChange,
            String message
    ) {
        this.resolverMode = resolverMode;
        this.resolverDecision = resolverDecision;
        this.syncAction = syncAction;
        this.matchedSourceId = matchedSourceId;
        this.waitConfirm = waitConfirm;
        this.skippedNoChange = skippedNoChange;
        this.message = message;
    }

    public String getResolverMode() {
        return resolverMode;
    }

    public String getResolverDecision() {
        return resolverDecision;
    }

    public String getSyncAction() {
        return syncAction;
    }

    public Long getMatchedSourceId() {
        return matchedSourceId;
    }

    public boolean isWaitConfirm() {
        return waitConfirm;
    }

    public boolean isSkippedNoChange() {
        return skippedNoChange;
    }

    public String getMessage() {
        return message;
    }
}
