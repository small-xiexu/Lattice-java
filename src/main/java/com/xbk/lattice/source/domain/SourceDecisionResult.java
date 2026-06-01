package com.xbk.lattice.source.domain;

import lombok.Getter;

/**
 * 资料源自动识别决策结果。
 *
 * <p>承载统一上传识别后的决策、动作与收口提示——决定 source 同步的方向和是否需要人工确认。
 *
 * @author xiexu
 */
@Getter
public class SourceDecisionResult {

    /** 识别模式（如 manual / auto_resolve）。 */
    private final String resolverMode;
    /** 决策结果（如 new_source / matched / conflict）。 */
    private final String resolverDecision;
    /** 同步动作（如 sync / skip / confirm）。 */
    private final String syncAction;
    /** 命中的资料源主键。为 null 表示未匹配。 */
    private final Long matchedSourceId;
    /** 是否等待人工确认。 */
    private final boolean waitConfirm;
    /** 是否因无变更而跳过（manifest hash 未变）。 */
    private final boolean skippedNoChange;
    /** 提示消息（含决策原因和建议操作）。 */
    private final String message;

    public SourceDecisionResult(
            String resolverMode, String resolverDecision, String syncAction,
            Long matchedSourceId, boolean waitConfirm, boolean skippedNoChange, String message
    ) {
        this.resolverMode = resolverMode;
        this.resolverDecision = resolverDecision;
        this.syncAction = syncAction;
        this.matchedSourceId = matchedSourceId;
        this.waitConfirm = waitConfirm;
        this.skippedNoChange = skippedNoChange;
        this.message = message;
    }
}
