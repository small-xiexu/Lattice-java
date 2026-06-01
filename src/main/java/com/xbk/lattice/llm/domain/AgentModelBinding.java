package com.xbk.lattice.llm.domain;

import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * Agent 模型绑定。
 *
 * <p>表示某个 scene 下某个 Agent 角色的模型绑定关系——主模型和降级模型的映射。
 * 由 LLM 路由层根据 scene+agentRole 查找对应模型配置。
 *
 * @author xiexu
 */
@Getter
public class AgentModelBinding {

    /** 绑定主键。 */
    private final Long id;
    /** 场景标识（compile / query / deep_research）。 */
    private final String scene;
    /** Agent 角色（如 writer / answer / planner）。 */
    private final String agentRole;
    /** 主模型配置主键。 */
    private final Long primaryModelProfileId;
    /** 降级模型配置主键。主模型不可用时自动切换。为 null 表示无降级。 */
    private final Long fallbackModelProfileId;
    /** 路由标签（由 scene.agentRole.modelCode 拼接生成）。 */
    private final String routeLabel;
    /** 是否启用。 */
    private final boolean enabled;
    /** 备注。 */
    private final String remarks;
    /** 创建人。 */
    private final String createdBy;
    /** 最后更新人。 */
    private final String updatedBy;
    /** 创建时间。 */
    private final OffsetDateTime createdAt;
    /** 最后更新时间。 */
    private final OffsetDateTime updatedAt;

    public AgentModelBinding(
            Long id, String scene, String agentRole, Long primaryModelProfileId,
            Long fallbackModelProfileId, String routeLabel, boolean enabled, String remarks,
            String createdBy, String updatedBy, OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.scene = scene;
        this.agentRole = agentRole;
        this.primaryModelProfileId = primaryModelProfileId;
        this.fallbackModelProfileId = fallbackModelProfileId;
        this.routeLabel = routeLabel;
        this.enabled = enabled;
        this.remarks = remarks;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
