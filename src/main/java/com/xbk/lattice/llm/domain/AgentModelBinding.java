package com.xbk.lattice.llm.domain;

import java.time.OffsetDateTime;

/**
 * Agent 模型绑定
 *
 * 职责：表示某个 scene 下某个 Agent 角色的模型绑定关系
 *
 * @author xiexu
 */
public class AgentModelBinding {

    private final Long id;

    private final String scene;

    private final String agentRole;

    private final Long primaryModelProfileId;

    private final Long fallbackModelProfileId;

    private final String routeLabel;

    private final boolean enabled;

    private final String remarks;

    private final String createdBy;

    private final String updatedBy;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    /**
     * 创建 Agent 模型绑定。
     *
     * @param id 主键
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param primaryModelProfileId 主模型配置 ID
     * @param fallbackModelProfileId 备用模型配置 ID
     * @param routeLabel 路由标签
     * @param enabled 是否启用
     * @param remarks 备注
     * @param createdBy 创建人
     * @param updatedBy 更新人
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public AgentModelBinding(
            Long id,
            String scene,
            String agentRole,
            Long primaryModelProfileId,
            Long fallbackModelProfileId,
            String routeLabel,
            boolean enabled,
            String remarks,
            String createdBy,
            String updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
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

    /**
     * 返回主键。
     *
     * @return 主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 返回场景。
     *
     * @return 场景
     */
    public String getScene() {
        return scene;
    }

    /**
     * 返回 Agent 角色。
     *
     * @return Agent 角色
     */
    public String getAgentRole() {
        return agentRole;
    }

    /**
     * 返回主模型配置 ID。
     *
     * @return 主模型配置 ID
     */
    public Long getPrimaryModelProfileId() {
        return primaryModelProfileId;
    }

    /**
     * 返回备用模型配置 ID。
     *
     * @return 备用模型配置 ID
     */
    public Long getFallbackModelProfileId() {
        return fallbackModelProfileId;
    }

    /**
     * 返回路由标签。
     *
     * @return 路由标签
     */
    public String getRouteLabel() {
        return routeLabel;
    }

    /**
     * 返回是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 返回备注。
     *
     * @return 备注
     */
    public String getRemarks() {
        return remarks;
    }

    /**
     * 返回创建人。
     *
     * @return 创建人
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * 返回更新人。
     *
     * @return 更新人
     */
    public String getUpdatedBy() {
        return updatedBy;
    }

    /**
     * 返回创建时间。
     *
     * @return 创建时间
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 返回更新时间。
     *
     * @return 更新时间
     */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
