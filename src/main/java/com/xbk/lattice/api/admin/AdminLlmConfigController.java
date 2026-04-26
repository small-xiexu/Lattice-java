package com.xbk.lattice.api.admin;

import com.xbk.lattice.llm.domain.AgentModelBinding;
import com.xbk.lattice.llm.domain.LlmModelProfile;
import com.xbk.lattice.llm.domain.LlmProviderConnection;
import com.xbk.lattice.llm.service.LlmConfigAdminService;
import com.xbk.lattice.llm.service.LlmSecretCryptoService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 管理侧 LLM 配置控制器
 *
 * 职责：暴露连接配置、模型配置与 Agent 绑定的后台管理接口
 *
 * @author xiexu
 */
@RestController
@Profile("jdbc")
@RequestMapping("/api/v1/admin/llm")
public class AdminLlmConfigController {

    private static final Map<String, List<String>> SCENE_ROLE_OPTIONS = createSceneRoleOptions();

    private final LlmConfigAdminService llmConfigAdminService;

    private final LlmSecretCryptoService llmSecretCryptoService;

    /**
     * 创建管理侧 LLM 配置控制器。
     *
     * @param llmConfigAdminService LLM 配置中心后台服务
     * @param llmSecretCryptoService 密钥加解密服务
     */
    public AdminLlmConfigController(
            LlmConfigAdminService llmConfigAdminService,
            LlmSecretCryptoService llmSecretCryptoService
    ) {
        this.llmConfigAdminService = llmConfigAdminService;
        this.llmSecretCryptoService = llmSecretCryptoService;
    }

    /**
     * 返回全部连接配置。
     *
     * @return 连接配置列表
     */
    @GetMapping("/connections")
    public AdminLlmConnectionListResponse listConnections() {
        List<AdminLlmConnectionResponse> items = new ArrayList<AdminLlmConnectionResponse>();
        for (LlmProviderConnection connection : llmConfigAdminService.listConnections()) {
            items.add(toConnectionResponse(connection));
        }
        return new AdminLlmConnectionListResponse(items.size(), items);
    }

    /**
     * 新增连接配置。
     *
     * @param request 请求体
     * @return 连接配置响应
     */
    @PostMapping("/connections")
    public AdminLlmConnectionResponse createConnection(@RequestBody AdminLlmConnectionRequest request) {
        validateConnectionRequest(request);
        requireApiKey(request.getApiKey());
        return toConnectionResponse(llmConfigAdminService.saveConnection(toConnection(null, request, Optional.empty())));
    }

    /**
     * 更新连接配置。
     *
     * @param id 主键
     * @param request 请求体
     * @return 连接配置响应
     */
    @PutMapping("/connections/{id}")
    public AdminLlmConnectionResponse updateConnection(
            @PathVariable Long id,
            @RequestBody AdminLlmConnectionRequest request
    ) {
        validateConnectionRequest(request);
        Optional<LlmProviderConnection> existing = llmConfigAdminService.findConnection(id);
        return toConnectionResponse(llmConfigAdminService.saveConnection(toConnection(id, request, existing)));
    }

    /**
     * 删除连接配置。
     *
     * @param id 主键
     * @return 删除响应
     */
    @DeleteMapping("/connections/{id}")
    public AdminMutationResponse deleteConnection(@PathVariable Long id) {
        llmConfigAdminService.deleteConnection(id);
        return new AdminMutationResponse(id, "deleted");
    }

    /**
     * 返回全部模型配置。
     *
     * @return 模型配置列表
     */
    @GetMapping("/models")
    public AdminLlmModelListResponse listModels() {
        List<AdminLlmModelResponse> items = new ArrayList<AdminLlmModelResponse>();
        for (LlmModelProfile modelProfile : llmConfigAdminService.listModelProfiles()) {
            items.add(toModelResponse(modelProfile));
        }
        return new AdminLlmModelListResponse(items.size(), items);
    }

    /**
     * 新增模型配置。
     *
     * @param request 请求体
     * @return 模型配置响应
     */
    @PostMapping("/models")
    public AdminLlmModelResponse createModel(@RequestBody AdminLlmModelRequest request) {
        validateModelRequest(request);
        return toModelResponse(llmConfigAdminService.saveModelProfile(toModelProfile(null, request, Optional.empty())));
    }

    /**
     * 更新模型配置。
     *
     * @param id 主键
     * @param request 请求体
     * @return 模型配置响应
     */
    @PutMapping("/models/{id}")
    public AdminLlmModelResponse updateModel(
            @PathVariable Long id,
            @RequestBody AdminLlmModelRequest request
    ) {
        validateModelRequest(request);
        Optional<LlmModelProfile> existing = llmConfigAdminService.findModelProfile(id);
        return toModelResponse(llmConfigAdminService.saveModelProfile(toModelProfile(id, request, existing)));
    }

    /**
     * 删除模型配置。
     *
     * @param id 主键
     * @return 删除响应
     */
    @DeleteMapping("/models/{id}")
    public AdminMutationResponse deleteModel(@PathVariable Long id) {
        llmConfigAdminService.deleteModelProfile(id);
        return new AdminMutationResponse(id, "deleted");
    }

    /**
     * 返回全部 Agent 绑定。
     *
     * @return Agent 绑定列表
     */
    @GetMapping("/bindings")
    public AdminLlmBindingListResponse listBindings() {
        List<AdminLlmBindingResponse> items = new ArrayList<AdminLlmBindingResponse>();
        for (AgentModelBinding binding : llmConfigAdminService.listBindings()) {
            items.add(toBindingResponse(binding));
        }
        return new AdminLlmBindingListResponse(items.size(), items);
    }

    /**
     * 新增 Agent 绑定。
     *
     * @param request 请求体
     * @return Agent 绑定响应
     */
    @PostMapping("/bindings")
    public AdminLlmBindingResponse createBinding(@RequestBody AdminLlmBindingRequest request) {
        validateBindingRequest(request);
        return toBindingResponse(llmConfigAdminService.saveBinding(toBinding(null, request, Optional.empty())));
    }

    /**
     * 更新 Agent 绑定。
     *
     * @param id 主键
     * @param request 请求体
     * @return Agent 绑定响应
     */
    @PutMapping("/bindings/{id}")
    public AdminLlmBindingResponse updateBinding(
            @PathVariable Long id,
            @RequestBody AdminLlmBindingRequest request
    ) {
        validateBindingRequest(request);
        Optional<AgentModelBinding> existing = llmConfigAdminService.findBinding(id);
        return toBindingResponse(llmConfigAdminService.saveBinding(toBinding(id, request, existing)));
    }

    /**
     * 删除 Agent 绑定。
     *
     * @param id 主键
     * @return 删除响应
     */
    @DeleteMapping("/bindings/{id}")
    public AdminMutationResponse deleteBinding(@PathVariable Long id) {
        llmConfigAdminService.deleteBinding(id);
        return new AdminMutationResponse(id, "deleted");
    }

    private LlmProviderConnection toConnection(
            Long id,
            AdminLlmConnectionRequest request,
            Optional<LlmProviderConnection> existing
    ) {
        String apiKeyCiphertext = existing.map(LlmProviderConnection::getApiKeyCiphertext).orElse("");
        String apiKeyMask = existing.map(LlmProviderConnection::getApiKeyMask).orElse("");
        if (StringUtils.hasText(request.getApiKey())) {
            apiKeyCiphertext = llmSecretCryptoService.encrypt(request.getApiKey());
            apiKeyMask = llmSecretCryptoService.mask(request.getApiKey());
        }
        if (!StringUtils.hasText(apiKeyCiphertext)) {
            requireApiKey(request.getApiKey());
            apiKeyCiphertext = llmSecretCryptoService.encrypt(request.getApiKey());
            apiKeyMask = llmSecretCryptoService.mask(request.getApiKey());
        }
        String operator = resolveOperator(request.getOperator());
        return new LlmProviderConnection(
                id,
                request.getConnectionCode(),
                request.getProviderType(),
                request.getBaseUrl(),
                apiKeyCiphertext,
                apiKeyMask,
                request.getEnabled() == null || request.getEnabled().booleanValue(),
                request.getRemarks(),
                existing.map(LlmProviderConnection::getCreatedBy).orElse(operator),
                operator,
                existing.map(LlmProviderConnection::getCreatedAt).orElse(null),
                existing.map(LlmProviderConnection::getUpdatedAt).orElse(null)
        );
    }

    private LlmModelProfile toModelProfile(
            Long id,
            AdminLlmModelRequest request,
            Optional<LlmModelProfile> existing
    ) {
        String operator = resolveOperator(request.getOperator());
        return new LlmModelProfile(
                id,
                resolveModelCode(request, existing),
                request.getConnectionId(),
                request.getModelName(),
                normalizeModelKind(request.getModelKind()),
                request.getExpectedDimensions(),
                request.getSupportsDimensionOverride() != null && request.getSupportsDimensionOverride().booleanValue(),
                request.getTemperature(),
                request.getMaxTokens(),
                request.getTimeoutSeconds(),
                request.getInputPricePer1kTokens(),
                request.getOutputPricePer1kTokens(),
                normalizeExtraOptions(request.getExtraOptionsJson()),
                request.getEnabled() == null || request.getEnabled().booleanValue(),
                request.getRemarks(),
                existing.map(LlmModelProfile::getCreatedBy).orElse(operator),
                operator,
                existing.map(LlmModelProfile::getCreatedAt).orElse(null),
                existing.map(LlmModelProfile::getUpdatedAt).orElse(null)
        );
    }

    private AgentModelBinding toBinding(
            Long id,
            AdminLlmBindingRequest request,
            Optional<AgentModelBinding> existing
    ) {
        String operator = resolveOperator(request.getOperator());
        String scene = normalizeScene(request.getScene());
        String agentRole = normalizeAgentRole(request.getAgentRole());
        return new AgentModelBinding(
                id,
                scene,
                agentRole,
                request.getPrimaryModelProfileId(),
                request.getFallbackModelProfileId(),
                resolveRouteLabel(request.getRouteLabel(), scene, agentRole, request.getPrimaryModelProfileId()),
                request.getEnabled() == null || request.getEnabled().booleanValue(),
                request.getRemarks(),
                existing.map(AgentModelBinding::getCreatedBy).orElse(operator),
                operator,
                existing.map(AgentModelBinding::getCreatedAt).orElse(null),
                existing.map(AgentModelBinding::getUpdatedAt).orElse(null)
        );
    }

    private AdminLlmConnectionResponse toConnectionResponse(LlmProviderConnection connection) {
        return new AdminLlmConnectionResponse(
                connection.getId(),
                connection.getConnectionCode(),
                connection.getProviderType(),
                connection.getBaseUrl(),
                connection.getApiKeyMask(),
                connection.isEnabled(),
                connection.getRemarks(),
                connection.getCreatedBy(),
                connection.getUpdatedBy(),
                connection.getCreatedAt() == null ? null : connection.getCreatedAt().toString(),
                connection.getUpdatedAt() == null ? null : connection.getUpdatedAt().toString()
        );
    }

    private AdminLlmModelResponse toModelResponse(LlmModelProfile modelProfile) {
        return new AdminLlmModelResponse(
                modelProfile.getId(),
                modelProfile.getModelCode(),
                modelProfile.getConnectionId(),
                modelProfile.getModelName(),
                modelProfile.getModelKind(),
                modelProfile.getExpectedDimensions(),
                modelProfile.isSupportsDimensionOverride(),
                modelProfile.getTemperature(),
                modelProfile.getMaxTokens(),
                modelProfile.getTimeoutSeconds(),
                modelProfile.getInputPricePer1kTokens(),
                modelProfile.getOutputPricePer1kTokens(),
                normalizeExtraOptions(modelProfile.getExtraOptionsJson()),
                modelProfile.isEnabled(),
                modelProfile.getRemarks(),
                modelProfile.getCreatedBy(),
                modelProfile.getUpdatedBy(),
                modelProfile.getCreatedAt() == null ? null : modelProfile.getCreatedAt().toString(),
                modelProfile.getUpdatedAt() == null ? null : modelProfile.getUpdatedAt().toString()
        );
    }

    private AdminLlmBindingResponse toBindingResponse(AgentModelBinding binding) {
        return new AdminLlmBindingResponse(
                binding.getId(),
                binding.getScene(),
                binding.getAgentRole(),
                binding.getPrimaryModelProfileId(),
                binding.getFallbackModelProfileId(),
                binding.getRouteLabel(),
                binding.isEnabled(),
                binding.getRemarks(),
                binding.getCreatedBy(),
                binding.getUpdatedBy(),
                binding.getCreatedAt() == null ? null : binding.getCreatedAt().toString(),
                binding.getUpdatedAt() == null ? null : binding.getUpdatedAt().toString()
        );
    }

    private String resolveOperator(String operator) {
        return StringUtils.hasText(operator) ? operator.trim() : "admin";
    }

    private String normalizeExtraOptions(String extraOptionsJson) {
        return StringUtils.hasText(extraOptionsJson) ? extraOptionsJson : "{}";
    }

    private String resolveModelCode(
            AdminLlmModelRequest request,
            Optional<LlmModelProfile> existing
    ) {
        if (StringUtils.hasText(request.getModelCode())) {
            return request.getModelCode().trim();
        }
        if (existing.isPresent() && StringUtils.hasText(existing.orElseThrow().getModelCode())) {
            return existing.orElseThrow().getModelCode();
        }
        String baseCode = slugify(request.getModelName());
        String modelKind = normalizeModelKind(request.getModelKind()).toLowerCase(Locale.ROOT);
        String generated = baseCode + "-" + modelKind + "-" + request.getConnectionId();
        return truncate(generated, 64);
    }

    private String resolveRouteLabel(
            String routeLabel,
            String scene,
            String agentRole,
            Long primaryModelProfileId
    ) {
        if (StringUtils.hasText(routeLabel)) {
            return routeLabel.trim();
        }
        String modelCode = llmConfigAdminService.findModelProfile(primaryModelProfileId)
                .map(LlmModelProfile::getModelCode)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .orElse("model-" + primaryModelProfileId);
        return truncate(scene + "." + agentRole + "." + slugify(modelCode), 128);
    }

    private String slugify(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return "default";
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        return StringUtils.hasText(normalized) ? normalized : "default";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void requireApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("apiKey不能为空");
        }
    }

    private void validateConnectionRequest(AdminLlmConnectionRequest request) {
        if (!StringUtils.hasText(request.getConnectionCode())) {
            throw new IllegalArgumentException("connectionCode不能为空");
        }
        if (!StringUtils.hasText(request.getProviderType())) {
            throw new IllegalArgumentException("providerType不能为空");
        }
        if (!StringUtils.hasText(request.getBaseUrl())) {
            throw new IllegalArgumentException("baseUrl不能为空");
        }
    }

    private void validateModelRequest(AdminLlmModelRequest request) {
        if (request.getConnectionId() == null) {
            throw new IllegalArgumentException("connectionId不能为空");
        }
        if (!StringUtils.hasText(request.getModelName())) {
            throw new IllegalArgumentException("modelName不能为空");
        }
        String modelKind = normalizeModelKind(request.getModelKind());
        if (LlmModelProfile.MODEL_KIND_EMBEDDING.equals(modelKind)
                && (request.getExpectedDimensions() == null || request.getExpectedDimensions().intValue() <= 0)) {
            throw new IllegalArgumentException("EMBEDDING 模型必须填写 expectedDimensions");
        }
        if (LlmModelProfile.MODEL_KIND_CHAT.equals(modelKind) && request.getExpectedDimensions() != null) {
            throw new IllegalArgumentException("CHAT 模型不允许填写 expectedDimensions");
        }
    }

    private String normalizeModelKind(String modelKind) {
        if (!StringUtils.hasText(modelKind)) {
            return LlmModelProfile.MODEL_KIND_CHAT;
        }
        return modelKind.trim().toUpperCase();
    }

    private void validateBindingRequest(AdminLlmBindingRequest request) {
        if (!StringUtils.hasText(request.getScene())) {
            throw new IllegalArgumentException("scene不能为空");
        }
        if (!StringUtils.hasText(request.getAgentRole())) {
            throw new IllegalArgumentException("agentRole不能为空");
        }
        String scene = normalizeScene(request.getScene());
        List<String> sceneRoles = resolveSceneRoles(scene);
        if (!sceneRoles.contains(normalizeAgentRole(request.getAgentRole()))) {
            throw new IllegalArgumentException("agentRole与scene不匹配");
        }
        if (request.getPrimaryModelProfileId() == null) {
            throw new IllegalArgumentException("primaryModelProfileId不能为空");
        }
    }

    private String normalizeScene(String scene) {
        return StringUtils.hasText(scene) ? scene.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeAgentRole(String agentRole) {
        return StringUtils.hasText(agentRole) ? agentRole.trim().toLowerCase(Locale.ROOT) : "";
    }

    private List<String> resolveSceneRoles(String scene) {
        List<String> roles = SCENE_ROLE_OPTIONS.get(normalizeScene(scene));
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("scene不支持");
        }
        return roles;
    }

    private static Map<String, List<String>> createSceneRoleOptions() {
        Map<String, List<String>> options = new LinkedHashMap<String, List<String>>();
        options.put("compile", List.of("writer", "reviewer", "fixer"));
        options.put("query", List.of("answer", "reviewer", "rewrite"));
        options.put("deep_research", List.of("planner", "researcher", "synthesizer", "reviewer"));
        return options;
    }

    /**
     * 连接配置请求。
     *
     * 职责：承载管理侧连接配置新增与更新参数
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLlmConnectionRequest {

        private String connectionCode;

        private String providerType;

        private String baseUrl;

        private String apiKey;

        private Boolean enabled;

        private String remarks;

        private String operator;
    }

    /**
     * 连接配置响应。
     *
     * 职责：返回管理侧连接配置展示信息
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLlmConnectionResponse {

        private Long id;

        private String connectionCode;

        private String providerType;

        private String baseUrl;

        private String apiKeyMask;

        private boolean enabled;

        private String remarks;

        private String createdBy;

        private String updatedBy;

        private String createdAt;

        private String updatedAt;
    }

    /**
     * 连接配置列表响应。
     *
     * 职责：返回管理侧连接配置列表
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLlmConnectionListResponse {

        private int count;

        private List<AdminLlmConnectionResponse> items;
    }

    /**
     * 模型配置请求。
     *
     * 职责：承载管理侧模型配置新增与更新参数
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLlmModelRequest {

        private String modelCode;

        private Long connectionId;

        private String modelName;

        private String modelKind;

        private Integer expectedDimensions;

        private Boolean supportsDimensionOverride;

        private BigDecimal temperature;

        private Integer maxTokens;

        private Integer timeoutSeconds;

        private BigDecimal inputPricePer1kTokens;

        private BigDecimal outputPricePer1kTokens;

        private String extraOptionsJson;

        private Boolean enabled;

        private String remarks;

        private String operator;
    }

    /**
     * 模型配置响应。
     *
     * 职责：返回管理侧模型配置展示信息
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLlmModelResponse {

        private Long id;

        private String modelCode;

        private Long connectionId;

        private String modelName;

        private String modelKind;

        private Integer expectedDimensions;

        private boolean supportsDimensionOverride;

        private BigDecimal temperature;

        private Integer maxTokens;

        private Integer timeoutSeconds;

        private BigDecimal inputPricePer1kTokens;

        private BigDecimal outputPricePer1kTokens;

        private String extraOptionsJson;

        private boolean enabled;

        private String remarks;

        private String createdBy;

        private String updatedBy;

        private String createdAt;

        private String updatedAt;
    }

    /**
     * 模型配置列表响应。
     *
     * 职责：返回管理侧模型配置列表
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLlmModelListResponse {

        private int count;

        private List<AdminLlmModelResponse> items;
    }

    /**
     * Agent 绑定请求。
     *
     * 职责：承载管理侧 Agent 绑定新增与更新参数
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLlmBindingRequest {

        private String scene;

        private String agentRole;

        private Long primaryModelProfileId;

        private Long fallbackModelProfileId;

        private String routeLabel;

        private Boolean enabled;

        private String remarks;

        private String operator;
    }

    /**
     * Agent 绑定响应。
     *
     * 职责：返回管理侧 Agent 绑定展示信息
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLlmBindingResponse {

        private Long id;

        private String scene;

        private String agentRole;

        private Long primaryModelProfileId;

        private Long fallbackModelProfileId;

        private String routeLabel;

        private boolean enabled;

        private String remarks;

        private String createdBy;

        private String updatedBy;

        private String createdAt;

        private String updatedAt;
    }

    /**
     * Agent 绑定列表响应。
     *
     * 职责：返回管理侧 Agent 绑定列表
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLlmBindingListResponse {

        private int count;

        private List<AdminLlmBindingResponse> items;
    }

    /**
     * 变更响应。
     *
     * 职责：返回删除类操作的最小结果
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminMutationResponse {

        private Long id;

        private String status;
    }
}
