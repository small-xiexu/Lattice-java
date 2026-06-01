package com.xbk.lattice.source.domain;

import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 资料源同步运行。
 *
 * <p>表示单次资料源同步任务的阶段状态与上下文——含触发类型、识别决策、编译关联和错误信息。
 * 为不可变领域对象。
 *
 * @author xiexu
 */
@Getter
public class SourceSyncRun {

    /** 运行主键。 */
    private final Long id;
    /** 资料源主键。 */
    private final Long sourceId;
    /** 资料源类型。 */
    private final String sourceType;
    /** 本次同步的 manifest 哈希。 */
    private final String manifestHash;
    /** 触发方式（如 manual / auto / webhook）。 */
    private final String triggerType;
    /** 识别模式。 */
    private final String resolverMode;
    /** 识别决策。 */
    private final String resolverDecision;
    /** 同步动作（sync / skip / confirm）。 */
    private final String syncAction;
    /** 运行状态（如 QUEUED / RUNNING / SUCCESS / FAILED）。 */
    private final String status;
    /** 命中的资料源主键。 */
    private final Long matchedSourceId;
    /** 关联的编译作业主键。为 null 表示未触发编译。 */
    private final String compileJobId;
    /** 证据 JSON。可能较大。 */
    private final String evidenceJson;
    /** 错误信息。可能含异常详情，禁止参与 toString()。 */
    private final String errorMessage;
    /** 请求时间。 */
    private final OffsetDateTime requestedAt;
    /** 最后更新时间。 */
    private final OffsetDateTime updatedAt;
    /** 开始时间。为 null 表示尚未开始。 */
    private final OffsetDateTime startedAt;
    /** 完成时间。为 null 表示未完成。 */
    private final OffsetDateTime finishedAt;

    public SourceSyncRun(
            Long id, Long sourceId, String sourceType, String manifestHash, String triggerType,
            String resolverMode, String resolverDecision, String syncAction, String status,
            Long matchedSourceId, String compileJobId, String evidenceJson, String errorMessage,
            OffsetDateTime requestedAt, OffsetDateTime updatedAt,
            OffsetDateTime startedAt, OffsetDateTime finishedAt
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
}
