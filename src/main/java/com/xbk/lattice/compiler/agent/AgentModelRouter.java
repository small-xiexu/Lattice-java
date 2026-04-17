package com.xbk.lattice.compiler.agent;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.llm.service.LlmRouteResolution;

import java.util.Locale;
import java.util.Optional;

/**
 * Agent 模型路由器
 *
 * 职责：为编译侧固定角色提供当前生效的模型路由标签
 *
 * @author xiexu
 */
public class AgentModelRouter {

    private final LlmGateway llmGateway;

    private final ExecutionLlmSnapshotService executionLlmSnapshotService;

    private final LlmProperties llmProperties;

    /**
     * 创建 Agent 模型路由器。
     *
     * @param llmGateway LLM 网关
     */
    public AgentModelRouter(LlmGateway llmGateway) {
        this.llmGateway = llmGateway;
        this.executionLlmSnapshotService = null;
        this.llmProperties = null;
    }

    /**
     * 创建 Agent 模型路由器。
     *
     * @param executionLlmSnapshotService 运行时快照服务
     * @param llmProperties LLM 配置
     */
    public AgentModelRouter(
            ExecutionLlmSnapshotService executionLlmSnapshotService,
            LlmProperties llmProperties
    ) {
        this.llmGateway = null;
        this.executionLlmSnapshotService = executionLlmSnapshotService;
        this.llmProperties = llmProperties;
    }

    /**
     * 返回 WriterAgent 当前路由。
     *
     * @return 路由标签
     */
    public String routeForWriterAgent() {
        return routeForWriterAgent(null, ExecutionLlmSnapshotService.COMPILE_SCENE);
    }

    /**
     * 返回 WriterAgent 当前路由。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 路由标签
     */
    public String routeForWriterAgent(String scopeId, String scene) {
        return routeFor(scopeId, scene, ExecutionLlmSnapshotService.ROLE_WRITER);
    }

    /**
     * 返回 ReviewerAgent 当前路由。
     *
     * @return 路由标签
     */
    public String routeForReviewerAgent() {
        return routeForReviewerAgent(null, ExecutionLlmSnapshotService.COMPILE_SCENE);
    }

    /**
     * 返回 ReviewerAgent 当前路由。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 路由标签
     */
    public String routeForReviewerAgent(String scopeId, String scene) {
        if (llmProperties != null && !llmProperties.isReviewEnabled()) {
            return "rule-based";
        }
        if (llmGateway != null && !llmGateway.isReviewEnabled()) {
            return "rule-based";
        }
        return routeFor(scopeId, scene, ExecutionLlmSnapshotService.ROLE_REVIEWER);
    }

    /**
     * 返回 FixerAgent 当前路由。
     *
     * @return 路由标签
     */
    public String routeForFixerAgent() {
        return routeForFixerAgent(null, ExecutionLlmSnapshotService.COMPILE_SCENE);
    }

    /**
     * 返回 FixerAgent 当前路由。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 路由标签
     */
    public String routeForFixerAgent(String scopeId, String scene) {
        return routeFor(scopeId, scene, ExecutionLlmSnapshotService.ROLE_FIXER);
    }

    /**
     * 返回某个 Agent 角色在指定作用域下的当前路由。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @return 路由标签
     */
    public String routeFor(String scopeId, String scene, String agentRole) {
        String normalizedAgentRole = normalizeAgentRole(agentRole);
        if (ExecutionLlmSnapshotService.ROLE_REVIEWER.equals(normalizedAgentRole)) {
            if (llmProperties != null && !llmProperties.isReviewEnabled()) {
                return "rule-based";
            }
            if (llmGateway != null && !llmGateway.isReviewEnabled()) {
                return "rule-based";
            }
        }
        if (executionLlmSnapshotService != null && scopeId != null && !scopeId.isBlank()) {
            Optional<LlmRouteResolution> routeResolution = executionLlmSnapshotService.resolveRoute(
                    resolveScopeType(scene),
                    scopeId,
                    normalizeScene(scene),
                    normalizedAgentRole
            );
            if (routeResolution.isPresent()) {
                return routeResolution.orElseThrow().getRouteLabel();
            }
        }
        if (llmGateway != null) {
            if (ExecutionLlmSnapshotService.ROLE_REVIEWER.equals(normalizedAgentRole)) {
                return scopeId == null || scopeId.isBlank()
                        ? llmGateway.reviewRoute()
                        : llmGateway.reviewRoute(scopeId, normalizeScene(scene));
            }
            if (ExecutionLlmSnapshotService.ROLE_FIXER.equals(normalizedAgentRole)) {
                return scopeId == null || scopeId.isBlank()
                        ? llmGateway.fixRoute()
                        : llmGateway.fixRoute(scopeId, normalizeScene(scene));
            }
            return scopeId == null || scopeId.isBlank()
                    ? llmGateway.compileRoute()
                    : llmGateway.compileRoute(scopeId, normalizeScene(scene));
        }
        if (llmProperties == null) {
            return "fallback";
        }
        if (ExecutionLlmSnapshotService.ROLE_REVIEWER.equals(normalizedAgentRole)) {
            return normalizeModelName(llmProperties.getReviewerModel());
        }
        return normalizeModelName(llmProperties.getCompileModel());
    }

    private String resolveScopeType(String scene) {
        String normalizedScene = normalizeScene(scene);
        if (ExecutionLlmSnapshotService.COMPILE_SCENE.equals(normalizedScene)) {
            return ExecutionLlmSnapshotService.COMPILE_SCOPE_TYPE;
        }
        return normalizedScene + "_scope";
    }

    private String normalizeScene(String scene) {
        if (scene == null || scene.isBlank()) {
            return ExecutionLlmSnapshotService.COMPILE_SCENE;
        }
        return scene.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAgentRole(String agentRole) {
        if (agentRole == null || agentRole.isBlank()) {
            return ExecutionLlmSnapshotService.ROLE_WRITER;
        }
        return agentRole.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return "fallback";
        }
        return modelName.trim().toLowerCase(Locale.ROOT);
    }
}
