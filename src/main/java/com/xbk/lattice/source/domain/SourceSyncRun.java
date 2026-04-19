package com.xbk.lattice.source.domain;

import java.time.OffsetDateTime;

/**
 * 资料源同步运行
 *
 * 职责：表示单次资料源同步任务的阶段状态与上下文
 *
 * @author xiexu
 */
public class SourceSyncRun {

    private final Long id;

    private final Long sourceId;

    private final String sourceType;

    private final String manifestHash;

    private final String triggerType;

    private final String resolverMode;

    private final String resolverDecision;

    private final String syncAction;

    private final String status;

    private final Long matchedSourceId;

    private final String compileJobId;

    private final String evidenceJson;

    private final String errorMessage;

    private final OffsetDateTime requestedAt;

    private final OffsetDateTime updatedAt;

    private final OffsetDateTime startedAt;

    private final OffsetDateTime finishedAt;

    /**
     * 创建同步运行。
     *
     * @param id 主键
     * @param sourceId 资料源主键
     * @param sourceType 资料源类型
     * @param manifestHash manifest 哈希
     * @param triggerType 触发方式
     * @param resolverMode 识别模式
     * @param resolverDecision 识别决策
     * @param syncAction 同步动作
     * @param status 状态
     * @param matchedSourceId 命中的资料源
     * @param compileJobId 编译作业
     * @param evidenceJson 证据 JSON
     * @param errorMessage 错误信息
     * @param requestedAt 请求时间
     * @param updatedAt 更新时间
     * @param startedAt 开始时间
     * @param finishedAt 完成时间
     */
    public SourceSyncRun(
            Long id,
            Long sourceId,
            String sourceType,
            String manifestHash,
            String triggerType,
            String resolverMode,
            String resolverDecision,
            String syncAction,
            String status,
            Long matchedSourceId,
            String compileJobId,
            String evidenceJson,
            String errorMessage,
            OffsetDateTime requestedAt,
            OffsetDateTime updatedAt,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt
    ) {
        this.id = id;
        this.sourceId = sourceId;
        this.sourceType = sourceType;
        this.manifestHash = manifestHash;
        this.triggerType = triggerType;
        this.resolverMode = resolverMode;
        this.resolverDecision = resolverDecision;
        this.syncAction = syncAction;
        this.status = status;
        this.matchedSourceId = matchedSourceId;
        this.compileJobId = compileJobId;
        this.evidenceJson = evidenceJson;
        this.errorMessage = errorMessage;
        this.requestedAt = requestedAt;
        this.updatedAt = updatedAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getManifestHash() {
        return manifestHash;
    }

    public String getTriggerType() {
        return triggerType;
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

    public String getStatus() {
        return status;
    }

    public Long getMatchedSourceId() {
        return matchedSourceId;
    }

    public String getCompileJobId() {
        return compileJobId;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }
}
