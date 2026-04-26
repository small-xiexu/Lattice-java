package com.xbk.lattice.llm.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.llm.domain.AgentModelBinding;
import com.xbk.lattice.llm.domain.ExecutionLlmSnapshot;
import com.xbk.lattice.llm.domain.LlmModelProfile;
import com.xbk.lattice.llm.domain.LlmProviderConnection;
import com.xbk.lattice.llm.infra.AgentModelBindingJdbcRepository;
import com.xbk.lattice.llm.infra.ExecutionLlmSnapshotJdbcRepository;
import com.xbk.lattice.llm.infra.LlmModelProfileJdbcRepository;
import com.xbk.lattice.llm.infra.LlmProviderConnectionJdbcRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 运行时快照服务
 *
 * 职责：冻结 compile 任务实际命中的模型绑定，并在执行期按快照解析稳定路由
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
@Slf4j
public class ExecutionLlmSnapshotService {

    public static final String COMPILE_SCOPE_TYPE = "compile_job";

    public static final String COMPILE_SCENE = "compile";

    public static final String QUERY_SCOPE_TYPE = "query_request";

    public static final String QUERY_SCENE = "query";

    public static final String DEEP_RESEARCH_SCOPE_TYPE = "deep_research_run";

    public static final String DEEP_RESEARCH_SCENE = "deep_research";

    public static final String ROLE_WRITER = "writer";

    public static final String ROLE_REVIEWER = "reviewer";

    public static final String ROLE_FIXER = "fixer";

    public static final String ROLE_ANSWER = "answer";

    public static final String ROLE_REWRITE = "rewrite";

    public static final String ROLE_PLANNER = "planner";

    public static final String ROLE_RESEARCHER = "researcher";

    public static final String ROLE_SYNTHESIZER = "synthesizer";

    private static final Set<String> DEEP_RESEARCH_REQUIRED_ROLES = Set.of(
            ROLE_PLANNER,
            ROLE_RESEARCHER,
            ROLE_SYNTHESIZER,
            ROLE_REVIEWER
    );

    private final LlmProperties llmProperties;

    private final AgentModelBindingJdbcRepository agentModelBindingJdbcRepository;

    private final LlmModelProfileJdbcRepository llmModelProfileJdbcRepository;

    private final LlmProviderConnectionJdbcRepository llmProviderConnectionJdbcRepository;

    private final ExecutionLlmSnapshotJdbcRepository executionLlmSnapshotJdbcRepository;

    private final LlmSecretCryptoService llmSecretCryptoService;

    /**
     * 创建运行时快照服务。
     *
     * @param llmProperties LLM 配置
     * @param agentModelBindingJdbcRepository Agent 绑定仓储
     * @param llmModelProfileJdbcRepository 模型配置仓储
     * @param llmProviderConnectionJdbcRepository 连接配置仓储
     * @param executionLlmSnapshotJdbcRepository 快照仓储
     * @param llmSecretCryptoService 密钥加解密服务
     */
    public ExecutionLlmSnapshotService(
            LlmProperties llmProperties,
            AgentModelBindingJdbcRepository agentModelBindingJdbcRepository,
            LlmModelProfileJdbcRepository llmModelProfileJdbcRepository,
            LlmProviderConnectionJdbcRepository llmProviderConnectionJdbcRepository,
            ExecutionLlmSnapshotJdbcRepository executionLlmSnapshotJdbcRepository,
            LlmSecretCryptoService llmSecretCryptoService
    ) {
        this.llmProperties = llmProperties;
        this.agentModelBindingJdbcRepository = agentModelBindingJdbcRepository;
        this.llmModelProfileJdbcRepository = llmModelProfileJdbcRepository;
        this.llmProviderConnectionJdbcRepository = llmProviderConnectionJdbcRepository;
        this.executionLlmSnapshotJdbcRepository = executionLlmSnapshotJdbcRepository;
        this.llmSecretCryptoService = llmSecretCryptoService;
    }

    /**
     * 冻结某个作用域的全部快照。
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param scene 场景
     * @return 冻结后的快照列表
     */
    public List<ExecutionLlmSnapshot> freezeSnapshots(String scopeType, String scopeId, String scene) {
        String normalizedScene = resolveScene(scene);
        List<ExecutionLlmSnapshot> existingSnapshots = executionLlmSnapshotJdbcRepository.findByScope(
                scopeType,
                scopeId,
                normalizedScene
        );
        if (!existingSnapshots.isEmpty()) {
            return existingSnapshots;
        }
        if (requiresStrictBindings(normalizedScene) && "properties".equalsIgnoreCase(normalizeConfigSource())) {
            throw new IllegalStateException("deep_research scene 不支持 properties/bootstrap 回退，必须配置 JDBC bindings");
        }
        if ("properties".equalsIgnoreCase(normalizeConfigSource())) {
            return List.of();
        }
        List<AgentModelBinding> bindings = agentModelBindingJdbcRepository.findEnabledByScene(normalizedScene);
        if (requiresStrictBindings(normalizedScene)) {
            validateStrictSceneBindings(normalizedScene, bindings);
        }
        if (bindings.isEmpty()) {
            if (requiresStrictBindings(normalizedScene)) {
                throw new IllegalStateException("deep_research scene 缺少启用中的 agent_model_bindings");
            }
            return List.of();
        }
        List<ExecutionLlmSnapshot> snapshots = new ArrayList<ExecutionLlmSnapshot>();
        for (AgentModelBinding binding : bindings) {
            Optional<LlmModelProfile> modelProfile = llmModelProfileJdbcRepository.findEnabledById(
                    binding.getPrimaryModelProfileId()
            );
            if (modelProfile.isEmpty()) {
                if (requiresStrictBindings(normalizedScene)) {
                    throw new IllegalStateException("deep_research role " + binding.getAgentRole()
                            + " 缺少启用中的 model profile: " + binding.getPrimaryModelProfileId());
                }
                log.warn("Skip llm snapshot freeze because model profile {} is missing or disabled", binding.getPrimaryModelProfileId());
                continue;
            }
            Optional<LlmProviderConnection> providerConnection = llmProviderConnectionJdbcRepository.findEnabledById(
                    modelProfile.orElseThrow().getConnectionId()
            );
            if (providerConnection.isEmpty()) {
                if (requiresStrictBindings(normalizedScene)) {
                    throw new IllegalStateException("deep_research role " + binding.getAgentRole()
                            + " 缺少启用中的 provider connection: " + modelProfile.orElseThrow().getConnectionId());
                }
                log.warn("Skip llm snapshot freeze because provider connection {} is missing or disabled",
                        modelProfile.orElseThrow().getConnectionId());
                continue;
            }
            snapshots.add(toSnapshot(
                    scopeType,
                    scopeId,
                    normalizedScene,
                    binding,
                    modelProfile.orElseThrow(),
                    providerConnection.orElseThrow()
            ));
        }
        if (snapshots.isEmpty()) {
            if (requiresStrictBindings(normalizedScene)) {
                throw new IllegalStateException("deep_research scene 未能冻结任何有效 LLM 快照");
            }
            return List.of();
        }
        executionLlmSnapshotJdbcRepository.saveAll(snapshots);
        return executionLlmSnapshotJdbcRepository.findByScope(scopeType, scopeId, normalizedScene);
    }

    /**
     * 校验某个场景的绑定是否完整可用。
     *
     * @param scene 场景
     */
    public void validateSceneBindings(String scene) {
        String normalizedScene = resolveScene(scene);
        if (!requiresStrictBindings(normalizedScene)) {
            return;
        }
        if ("properties".equalsIgnoreCase(normalizeConfigSource())) {
            throw new IllegalStateException("deep_research scene 不支持 properties/bootstrap 回退，必须配置 JDBC bindings");
        }
        List<AgentModelBinding> bindings = agentModelBindingJdbcRepository.findEnabledByScene(normalizedScene);
        validateStrictSceneBindings(normalizedScene, bindings);
        for (AgentModelBinding binding : bindings) {
            Optional<LlmModelProfile> modelProfile = llmModelProfileJdbcRepository.findEnabledById(
                    binding.getPrimaryModelProfileId()
            );
            if (modelProfile.isEmpty()) {
                throw new IllegalStateException("deep_research role " + binding.getAgentRole()
                        + " 缺少启用中的 model profile: " + binding.getPrimaryModelProfileId());
            }
            Optional<LlmProviderConnection> providerConnection = llmProviderConnectionJdbcRepository.findEnabledById(
                    modelProfile.orElseThrow().getConnectionId()
            );
            if (providerConnection.isEmpty()) {
                throw new IllegalStateException("deep_research role " + binding.getAgentRole()
                        + " 缺少启用中的 provider connection: " + modelProfile.orElseThrow().getConnectionId());
            }
        }
    }

    /**
     * 按作用域和角色解析实际路由。
     *
     * @param scopeType 作用域类型
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @return 路由解析结果
     */
    public Optional<LlmRouteResolution> resolveRoute(
            String scopeType,
            String scopeId,
            String scene,
            String agentRole
    ) {
        String normalizedScene = resolveScene(scene);
        Optional<ExecutionLlmSnapshot> snapshot = executionLlmSnapshotJdbcRepository.findByScopeAndRole(
                scopeType,
                scopeId,
                normalizedScene,
                agentRole
        );
        if (snapshot.isEmpty()) {
            if (requiresStrictBindings(normalizedScene)) {
                throw new IllegalStateException("No llm route configured for " + normalizedScene + "/" + agentRole
                        + " scopeId=" + scopeId);
            }
            return Optional.empty();
        }
        Optional<LlmProviderConnection> providerConnection = llmProviderConnectionJdbcRepository.findById(
                snapshot.orElseThrow().getConnectionId()
        );
        if (providerConnection.isEmpty()) {
            if (requiresStrictBindings(normalizedScene)) {
                throw new IllegalStateException("LLM snapshot exists but provider connection "
                        + snapshot.orElseThrow().getConnectionId() + " is missing");
            }
            log.warn("LLM snapshot exists but provider connection {} is missing", snapshot.orElseThrow().getConnectionId());
            return Optional.empty();
        }
        String apiKey = llmSecretCryptoService.decrypt(providerConnection.orElseThrow().getApiKeyCiphertext());
        return Optional.of(new LlmRouteResolution(
                snapshot.orElseThrow().getScopeType(),
                snapshot.orElseThrow().getScopeId(),
                snapshot.orElseThrow().getScene(),
                snapshot.orElseThrow().getAgentRole(),
                snapshot.orElseThrow().getBindingId(),
                snapshot.orElseThrow().getId(),
                snapshot.orElseThrow().getSnapshotVersion(),
                snapshot.orElseThrow().getRouteLabel(),
                snapshot.orElseThrow().getProviderType(),
                snapshot.orElseThrow().getBaseUrl(),
                apiKey,
                snapshot.orElseThrow().getModelName(),
                snapshot.orElseThrow().getTemperature(),
                snapshot.orElseThrow().getMaxTokens(),
                snapshot.orElseThrow().getTimeoutSeconds(),
                snapshot.orElseThrow().getExtraOptionsJson(),
                snapshot.orElseThrow().getInputPricePer1kTokens(),
                snapshot.orElseThrow().getOutputPricePer1kTokens(),
                true
        ));
    }

    /**
     * 构建 bootstrap fallback 路由。
     *
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param compileBaseUrl 编译 fallback 地址
     * @param compileApiKey 编译 fallback API Key
     * @param reviewerBaseUrl 审查 fallback 地址
     * @param reviewerApiKey 审查 fallback API Key
     * @return fallback 路由
     */
    public LlmRouteResolution bootstrapRoute(
            String scene,
            String agentRole,
            String compileBaseUrl,
            String compileApiKey,
            String reviewerBaseUrl,
            String reviewerApiKey
    ) {
        String normalizedScene = resolveScene(scene);
        if (requiresStrictBindings(normalizedScene)) {
            throw new IllegalStateException("deep_research scene 不允许 bootstrap fallback");
        }
        if (ROLE_REVIEWER.equals(agentRole)) {
            return new LlmRouteResolution(
                    resolveScopeType(normalizedScene),
                    null,
                    normalizedScene,
                    agentRole,
                    null,
                    null,
                    Integer.valueOf(0),
                    normalizeModelName(llmProperties.getReviewerModel()),
                    normalizeProviderType(llmProperties.getReviewerModel()),
                    reviewerBaseUrl,
                    reviewerApiKey,
                    llmProperties.getReviewerModel(),
                    null,
                    null,
                    Integer.valueOf(resolveCompileRoleTimeoutSeconds(scene, agentRole, null)),
                    "{}",
                    llmProperties.getPricing().getReviewerInputPricePer1kTokens(),
                    llmProperties.getPricing().getReviewerOutputPricePer1kTokens(),
                    false
            );
        }
        return new LlmRouteResolution(
                resolveScopeType(normalizedScene),
                null,
                normalizedScene,
                agentRole,
                null,
                null,
                Integer.valueOf(0),
                normalizeModelName(llmProperties.getCompileModel()),
                normalizeProviderType(llmProperties.getCompileModel()),
                compileBaseUrl,
                compileApiKey,
                llmProperties.getCompileModel(),
                null,
                null,
                Integer.valueOf(resolveCompileRoleTimeoutSeconds(scene, agentRole, null)),
                "{}",
                llmProperties.getPricing().getCompileInputPricePer1kTokens(),
                llmProperties.getPricing().getCompileOutputPricePer1kTokens(),
                false
        );
    }

    /**
     * 返回是否允许 fallback。
     *
     * @return 是否允许 fallback
     */
    public boolean isBootstrapEnabled() {
        return llmProperties.isBootstrapEnabled();
    }

    /**
     * 返回某个场景是否允许 bootstrap fallback。
     *
     * @param scene 场景
     * @return 是否允许 fallback
     */
    public boolean isBootstrapAllowed(String scene) {
        return llmProperties.isBootstrapEnabled() && !requiresStrictBindings(resolveScene(scene));
    }

    private ExecutionLlmSnapshot toSnapshot(
            String scopeType,
            String scopeId,
            String scene,
            AgentModelBinding binding,
            LlmModelProfile modelProfile,
            LlmProviderConnection providerConnection
    ) {
        return new ExecutionLlmSnapshot(
                null,
                scopeType,
                scopeId,
                scene,
                binding.getAgentRole(),
                binding.getId(),
                modelProfile.getId(),
                providerConnection.getId(),
                binding.getRouteLabel(),
                providerConnection.getProviderType(),
                providerConnection.getBaseUrl(),
                modelProfile.getModelName(),
                modelProfile.getTemperature(),
                modelProfile.getMaxTokens(),
                Integer.valueOf(resolveCompileRoleTimeoutSeconds(scene, binding.getAgentRole(), modelProfile.getTimeoutSeconds())),
                modelProfile.getExtraOptionsJson(),
                modelProfile.getInputPricePer1kTokens(),
                modelProfile.getOutputPricePer1kTokens(),
                Integer.valueOf(1),
                null
        );
    }

    private String normalizeConfigSource() {
        if (llmProperties.getConfigSource() == null) {
            return "hybrid";
        }
        return llmProperties.getConfigSource().trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return "fallback";
        }
        return modelName.trim().toLowerCase(Locale.ROOT);
    }

    private int resolveCompileRoleTimeoutSeconds(String scene, String agentRole, Integer configuredTimeoutSeconds) {
        if (configuredTimeoutSeconds != null && configuredTimeoutSeconds.intValue() > 0) {
            return configuredTimeoutSeconds.intValue();
        }
        if (!COMPILE_SCENE.equals(resolveScene(scene))) {
            return 300;
        }
        if (ROLE_WRITER.equals(agentRole)) {
            return llmProperties.getCompileTimeout().getWriterSeconds();
        }
        if (ROLE_REVIEWER.equals(agentRole)) {
            return llmProperties.getCompileTimeout().getReviewerSeconds();
        }
        if (ROLE_FIXER.equals(agentRole)) {
            return llmProperties.getCompileTimeout().getFixerSeconds();
        }
        return 300;
    }

    private String resolveScene(String scene) {
        if (scene == null || scene.isBlank()) {
            return COMPILE_SCENE;
        }
        return scene.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeProviderType(String routeOrProvider) {
        if (routeOrProvider == null || routeOrProvider.isBlank()) {
            return "openai";
        }
        String normalized = routeOrProvider.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("anthropic") || normalized.contains("claude")) {
            return "anthropic";
        }
        if (normalized.contains("compatible")) {
            return "openai_compatible";
        }
        return "openai";
    }

    private String resolveScopeType(String scene) {
        if (COMPILE_SCENE.equals(scene)) {
            return COMPILE_SCOPE_TYPE;
        }
        if (QUERY_SCENE.equals(scene)) {
            return QUERY_SCOPE_TYPE;
        }
        if (DEEP_RESEARCH_SCENE.equals(scene)) {
            return DEEP_RESEARCH_SCOPE_TYPE;
        }
        return scene == null ? "unknown_scope" : scene + "_scope";
    }

    private boolean requiresStrictBindings(String scene) {
        return DEEP_RESEARCH_SCENE.equals(resolveScene(scene));
    }

    private void validateStrictSceneBindings(String scene, List<AgentModelBinding> bindings) {
        if (!requiresStrictBindings(scene)) {
            return;
        }
        if (bindings == null || bindings.isEmpty()) {
            throw new IllegalStateException("deep_research scene 缺少启用中的 agent_model_bindings");
        }
        Set<String> configuredRoles = new LinkedHashSet<String>();
        for (AgentModelBinding binding : bindings) {
            configuredRoles.add(binding.getAgentRole());
        }
        if (!DEEP_RESEARCH_REQUIRED_ROLES.equals(configuredRoles)) {
            throw new IllegalStateException("deep_research scene 必须完整配置角色 "
                    + DEEP_RESEARCH_REQUIRED_ROLES + "，当前为 " + configuredRoles);
        }
    }
}
