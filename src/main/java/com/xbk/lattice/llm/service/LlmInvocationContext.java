package com.xbk.lattice.llm.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM 调用上下文
 *
 * 职责：承载 migrated path 需要透传到 Advisor 的最小调用语义
 *
 * @author xiexu
 */
public class LlmInvocationContext {

    private final String scene;

    private final String purpose;

    private final String scopeId;

    private final String agentRole;

    private final String routeLabel;

    /**
     * 创建 LLM 调用上下文。
     *
     * @param scene 调用场景
     * @param purpose 调用用途
     * @param scopeId 作用域标识
     * @param agentRole Agent 角色
     * @param routeLabel 路由标签
     */
    public LlmInvocationContext(
            String scene,
            String purpose,
            String scopeId,
            String agentRole,
            String routeLabel
    ) {
        this.scene = safeValue(scene);
        this.purpose = safeValue(purpose);
        this.scopeId = safeValue(scopeId);
        this.agentRole = safeValue(agentRole);
        this.routeLabel = safeValue(routeLabel);
    }

    /**
     * 根据路由和用途创建最小调用上下文。
     *
     * @param routeResolution 路由解析结果
     * @param purpose 调用用途
     * @return 调用上下文
     */
    public static LlmInvocationContext from(LlmRouteResolution routeResolution, String purpose) {
        if (routeResolution == null) {
            return new LlmInvocationContext("", purpose, "", "", "");
        }
        return new LlmInvocationContext(
                routeResolution.getScene(),
                purpose,
                routeResolution.getScopeId(),
                routeResolution.getAgentRole(),
                routeResolution.getRouteLabel()
        );
    }

    /**
     * 从请求上下文中解析最小调用上下文。
     *
     * @param context 请求上下文
     * @return 调用上下文
     */
    public static LlmInvocationContext from(Map<String, Object> context) {
        return new LlmInvocationContext(
                toStringValue(context, "scene"),
                toStringValue(context, "purpose"),
                toStringValue(context, "scopeId"),
                toStringValue(context, "agentRole"),
                toStringValue(context, "routeLabel")
        );
    }

    /**
     * 转为 Advisor 参数。
     *
     * @return Advisor 参数
     */
    public Map<String, Object> toAdvisorParams() {
        Map<String, Object> context = new LinkedHashMap<String, Object>();
        context.put("scene", scene);
        context.put("purpose", purpose);
        context.put("scopeId", scopeId);
        context.put("agentRole", agentRole);
        context.put("routeLabel", routeLabel);
        return context;
    }

    /**
     * 返回调用场景。
     *
     * @return 调用场景
     */
    public String getScene() {
        return scene;
    }

    /**
     * 返回调用用途。
     *
     * @return 调用用途
     */
    public String getPurpose() {
        return purpose;
    }

    /**
     * 返回作用域标识。
     *
     * @return 作用域标识
     */
    public String getScopeId() {
        return scopeId;
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
     * 返回路由标签。
     *
     * @return 路由标签
     */
    public String getRouteLabel() {
        return routeLabel;
    }

    private static String toStringValue(Map<String, Object> context, String key) {
        if (context == null || !context.containsKey(key) || context.get(key) == null) {
            return "";
        }
        return String.valueOf(context.get(key));
    }

    private static String safeValue(String value) {
        return value == null ? "" : value;
    }
}
