package com.xbk.lattice.api.admin;

import com.xbk.lattice.shared.json.JsonMappers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.domain.KnowledgeSourcePage;
import com.xbk.lattice.source.domain.SourceSyncRunDetail;
import com.xbk.lattice.source.domain.SourceValidationResult;
import com.xbk.lattice.source.service.SourceService;
import com.xbk.lattice.source.service.SourceSyncWorkflowService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 资料源后台控制器。
 *
 * 职责：暴露资料源列表、详情与最小更新能力
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/sources")
public class AdminSourceController {

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.defaultMapper();

    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "DISABLED", "ARCHIVED");

    private static final Set<String> ALLOWED_VISIBILITIES = Set.of("NORMAL", "ADMIN_ONLY");

    private static final Set<String> ALLOWED_SYNC_MODES = Set.of("AUTO", "FULL", "INCREMENTAL");

    private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of("UPLOAD", "GIT");

    private final SourceService sourceService;

    private final SourceSyncWorkflowService sourceSyncWorkflowService;

    /**
     * 创建资料源后台控制器。
     *
     * @param sourceService 资料源服务
     */
    public AdminSourceController(
            SourceService sourceService,
            SourceSyncWorkflowService sourceSyncWorkflowService
    ) {
        this.sourceService = sourceService;
        this.sourceSyncWorkflowService = sourceSyncWorkflowService;
    }

    /**
     * 分页查询资料源列表。
     *
     * @param keyword 关键词
     * @param status 状态过滤
     * @param sourceType 类型过滤
     * @param page 页码，从 1 开始
     * @param size 每页大小
     * @return 分页列表
     */
    @GetMapping
    public AdminKnowledgeSourcePageResponse listSources(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        int resolvedPage = normalizePage(page);
        int resolvedSize = normalizeSize(size);
        String normalizedStatus = normalizeOptionalEnum(status, ALLOWED_STATUSES, "status");
        String normalizedSourceType = normalizeOptionalEnum(sourceType, ALLOWED_SOURCE_TYPES, "sourceType");
        KnowledgeSourcePage sourcePage = sourceService.listSources(
                keyword,
                normalizedStatus,
                normalizedSourceType,
                resolvedPage,
                resolvedSize
        );
        List<AdminKnowledgeSourceSummaryResponse> items = new ArrayList<AdminKnowledgeSourceSummaryResponse>();
        for (KnowledgeSource source : sourcePage.getItems()) {
            items.add(toSummaryResponse(source));
        }
        return new AdminKnowledgeSourcePageResponse(
                sourcePage.getPage(),
                sourcePage.getSize(),
                sourcePage.getTotal(),
                items
        );
    }

    /**
     * 查询资料源详情。
     *
     * @param sourceId 资料源主键
     * @return 资料源详情
     */
    @GetMapping("/{sourceId}")
    public AdminKnowledgeSourceDetailResponse getSource(@PathVariable Long sourceId) {
        KnowledgeSource source = sourceService.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("source not found: " + sourceId));
        return toDetailResponse(source);
    }

    /**
     * 创建 Git 资料源。
     *
     * @param request 创建请求
     * @return 资料源详情
     */
    @PostMapping("/git")
    public AdminKnowledgeSourceDetailResponse createGitSource(@RequestBody AdminSourceCreateRequest request) {
        return toDetailResponse(sourceSyncWorkflowService.createGitSource(request));
    }

    /**
    /**
     * 更新资料源基础信息。
     *
     * @param sourceId 资料源主键
     * @param request 更新请求
     * @return 更新后的资料源详情
     */
    @PatchMapping("/{sourceId}")
    public AdminKnowledgeSourceDetailResponse updateSource(
            @PathVariable Long sourceId,
            @RequestBody AdminKnowledgeSourcePatchRequest request
    ) {
        KnowledgeSource existing = sourceService.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("source not found: " + sourceId));
        if ("default-source".equals(existing.getSourceCode())) {
            throw new IllegalArgumentException("default-source source is read-only");
        }
        String resolvedName = resolveName(existing, request);
        String resolvedStatus = resolveStatus(existing, request);
        String resolvedVisibility = resolveVisibility(existing, request);
        String resolvedDefaultSyncMode = resolveDefaultSyncMode(existing, request);
        String resolvedConfigJson = resolveConfigJson(existing, request);
        KnowledgeSource updated = sourceService.save(new KnowledgeSource(
                existing.getId(),
                existing.getSourceCode(),
                resolvedName,
                existing.getSourceType(),
                existing.getContentProfile(),
                resolvedStatus,
                resolvedVisibility,
                resolvedDefaultSyncMode,
                resolvedConfigJson,
                existing.getMetadataJson(),
                existing.getLatestManifestHash(),
                existing.getLastSyncRunId(),
                existing.getLastSyncStatus(),
                existing.getLastSyncAt(),
                existing.getCreatedAt(),
                existing.getUpdatedAt()
        ));
        return toDetailResponse(updated);
    }

    /**
     * 校验资料源配置。
     *
     * @param sourceId 资料源主键
     * @return 校验结果
     * @throws java.io.IOException IO 异常
     */
    @PostMapping("/{sourceId}/validate")
    public AdminSourceValidationResponse validateSource(@PathVariable Long sourceId) throws java.io.IOException {
        SourceValidationResult validationResult = sourceSyncWorkflowService.validateSource(sourceId);
        return new AdminSourceValidationResponse(
                validationResult.isValid(),
                validationResult.getSourceType(),
                validationResult.getMessage(),
                validationResult.getResolvedRef(),
                validationResult.getBranch(),
                validationResult.getGitCommit()
        );
    }

    /**
     * 对指定资料源发起同步。
     *
     * @param sourceId 资料源主键
     * @return 同步运行详情
     * @throws java.io.IOException IO 异常
     */
    @PostMapping("/{sourceId}/sync")
    public SourceSyncRunDetail syncSource(@PathVariable Long sourceId) throws java.io.IOException {
        return sourceSyncWorkflowService.syncSource(sourceId);
    }

    /**
     * 查询资料源下的文件列表。
     *
     * @param sourceId 资料源主键
     * @return 文件列表
     */
    @GetMapping("/{sourceId}/files")
    public List<AdminSourceFileResponse> listSourceFiles(@PathVariable Long sourceId) {
        List<AdminSourceFileResponse> responses = new ArrayList<AdminSourceFileResponse>();
        for (SourceFileRecord sourceFileRecord : sourceService.listSourceFiles(sourceId)) {
            JsonNode metadataNode = readJson(sourceFileRecord.getMetadataJson());
            responses.add(new AdminSourceFileResponse(
                    sourceFileRecord.getId(),
                    sourceFileRecord.getSourceId(),
                    sourceFileRecord.getRelativePath(),
                    sourceFileRecord.getFormat(),
                    sourceFileRecord.getFileSize(),
                    metadataNode.path("parseMode").asText(null),
                    metadataNode.path("parseProvider").asText(null),
                    sourceFileRecord.getContentPreview()
            ));
        }
        return responses;
    }

    private String resolveName(KnowledgeSource existing, AdminKnowledgeSourcePatchRequest request) {
        if (!StringUtils.hasText(request.getName())) {
            return existing.getName();
        }
        return request.getName().trim();
    }

    private String resolveStatus(KnowledgeSource existing, AdminKnowledgeSourcePatchRequest request) {
        String targetStatus = normalizeOptionalEnum(request.getStatus(), ALLOWED_STATUSES, "status");
        if (!StringUtils.hasText(targetStatus)) {
            return existing.getStatus();
        }
        validateStatusTransition(existing.getStatus(), targetStatus);
        return targetStatus;
    }

    private String resolveVisibility(KnowledgeSource existing, AdminKnowledgeSourcePatchRequest request) {
        String targetVisibility = normalizeOptionalEnum(request.getVisibility(), ALLOWED_VISIBILITIES, "visibility");
        if (!StringUtils.hasText(targetVisibility)) {
            return existing.getVisibility();
        }
        return targetVisibility;
    }

    private String resolveDefaultSyncMode(KnowledgeSource existing, AdminKnowledgeSourcePatchRequest request) {
        String targetSyncMode = normalizeOptionalEnum(request.getDefaultSyncMode(), ALLOWED_SYNC_MODES, "defaultSyncMode");
        if (!StringUtils.hasText(targetSyncMode)) {
            return existing.getDefaultSyncMode();
        }
        return targetSyncMode;
    }

    private String resolveConfigJson(KnowledgeSource existing, AdminKnowledgeSourcePatchRequest request) {
        if (request.getConfigJson() == null) {
            return existing.getConfigJson();
        }
        try {
            JsonNode configNode = request.getConfigJson();
            if (configNode.isNull() || configNode.isMissingNode()) {
                return "{}";
            }
            return OBJECT_MAPPER.writeValueAsString(configNode);
        }
        catch (Exception ex) {
            throw new IllegalArgumentException("configJson must be valid JSON");
        }
    }

    private void validateStatusTransition(String currentStatus, String targetStatus) {
        if (currentStatus == null || currentStatus.equals(targetStatus)) {
            return;
        }
        if ("ACTIVE".equals(currentStatus)
                && ("DISABLED".equals(targetStatus) || "ARCHIVED".equals(targetStatus))) {
            return;
        }
        if ("DISABLED".equals(currentStatus)
                && ("ACTIVE".equals(targetStatus) || "ARCHIVED".equals(targetStatus))) {
            return;
        }
        throw new IllegalArgumentException("unsupported source status transition: " + currentStatus + " -> " + targetStatus);
    }

    private int normalizePage(Integer page) {
        if (page == null || page.intValue() < 1) {
            throw new IllegalArgumentException("page must be greater than 0");
        }
        return page.intValue();
    }

    private int normalizeSize(Integer size) {
        if (size == null || size.intValue() < 1 || size.intValue() > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        return size.intValue();
    }

    private String normalizeOptionalEnum(String value, Set<String> allowedValues, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new IllegalArgumentException("unsupported " + fieldName + ": " + value);
        }
        return normalized;
    }

    private AdminKnowledgeSourceSummaryResponse toSummaryResponse(KnowledgeSource source) {
        return new AdminKnowledgeSourceSummaryResponse(
                source.getId(),
                source.getSourceCode(),
                source.getName(),
                resolveDisplayName(source),
                resolvePrimaryDocumentTitle(source),
                source.getSourceType(),
                source.getContentProfile(),
                source.getStatus(),
                source.getVisibility(),
                source.getDefaultSyncMode(),
                source.getLastSyncRunId(),
                source.getLastSyncStatus(),
                formatTime(source.getLastSyncAt()),
                formatTime(source.getUpdatedAt())
        );
    }

    private AdminKnowledgeSourceDetailResponse toDetailResponse(KnowledgeSource source) {
        return new AdminKnowledgeSourceDetailResponse(
                source.getId(),
                source.getSourceCode(),
                source.getName(),
                resolveDisplayName(source),
                resolvePrimaryDocumentTitle(source),
                source.getSourceType(),
                source.getContentProfile(),
                source.getStatus(),
                source.getVisibility(),
                source.getDefaultSyncMode(),
                source.getConfigJson(),
                source.getMetadataJson(),
                source.getLatestManifestHash(),
                source.getLastSyncRunId(),
                source.getLastSyncStatus(),
                formatTime(source.getLastSyncAt()),
                formatTime(source.getCreatedAt()),
                formatTime(source.getUpdatedAt())
        );
    }

    /**
     * 解析管理端展示名称。
     *
     * @param source 资料源
     * @return 展示名称
     */
    private String resolveDisplayName(KnowledgeSource source) {
        JsonNode metadataNode = readJson(source.getMetadataJson());
        JsonNode bundleNode = metadataNode.path("bundleSummary");
        String displayName = bundleNode.path("displayName").asText("");
        String primaryDocumentTitle = resolveFirstText(bundleNode.path("titleHints"));
        if (StringUtils.hasText(displayName) && !displayName.equals(primaryDocumentTitle)) {
            return displayName;
        }
        String relativePath = resolveFirstText(bundleNode.path("relativePathsSample"));
        String fileOrDirectoryName = resolveFileOrDirectoryDisplayName(relativePath);
        if (StringUtils.hasText(fileOrDirectoryName)) {
            return fileOrDirectoryName;
        }
        return source.getName();
    }

    /**
     * 解析资料源中的主要文档标题。
     *
     * @param source 资料源
     * @return 主要文档标题
     */
    private String resolvePrimaryDocumentTitle(KnowledgeSource source) {
        JsonNode metadataNode = readJson(source.getMetadataJson());
        JsonNode bundleNode = metadataNode.path("bundleSummary");
        JsonNode titleHintsNode = bundleNode.path("titleHints");
        String primaryDocumentTitle = resolveFirstText(titleHintsNode);
        return StringUtils.hasText(primaryDocumentTitle) ? primaryDocumentTitle : null;
    }

    /**
     * 解析数组中的第一个有效文本。
     *
     * @param arrayNode 数组节点
     * @return 第一个有效文本
     */
    private String resolveFirstText(JsonNode arrayNode) {
        if (!arrayNode.isArray()) {
            return null;
        }
        for (JsonNode itemNode : arrayNode) {
            String text = itemNode.asText("");
            if (StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return null;
    }

    /**
     * 从相对路径解析适合作为资料源名的文件或目录名。
     *
     * @param relativePath 相对路径
     * @return 文件或目录展示名
     */
    private String resolveFileOrDirectoryDisplayName(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return null;
        }
        String normalizedPath = relativePath.trim().replace("\\", "/");
        int separatorIndex = normalizedPath.indexOf('/');
        if (separatorIndex >= 0) {
            return normalizedPath.substring(0, separatorIndex);
        }
        int dotIndex = normalizedPath.lastIndexOf('.');
        if (dotIndex <= 0) {
            return normalizedPath;
        }
        return normalizedPath.substring(0, dotIndex);
    }

    private String formatTime(java.time.OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    private JsonNode readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        }
        catch (Exception ex) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    /**
     * 资料源分页响应。
     *
     * <p>承载资料源列表页的分页信息与数据项，由 {@code listSources()} 组装。
     *
     * @author xiexu
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminKnowledgeSourcePageResponse {

        /** 当前页码（1-based）。 */
        private Integer page;

        /** 每页大小。 */
        private Integer size;

        /** 符合条件的总记录数。 */
        private Long total;

        /** 资料源摘要列表。 */
        private List<AdminKnowledgeSourceSummaryResponse> items;
    }

    /**
     * 资料源摘要响应。
     *
     * <p>承载资料源列表页的最小展示字段，由 {@code toSummaryResponse()} 组装。
     *
     * @author xiexu
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminKnowledgeSourceSummaryResponse {

        /** 资料源主键。 */
        private Long id;

        /** 资料源编码（系统内唯一标识）。 */
        private String sourceCode;

        /** 资料源原始名称。 */
        private String name;

        /**
         * 管理台展示名称。
         *
         * <p>由 controller 从 {@code metadataJson} 中的 {@code bundleSummary} 计算：
         * 优先使用 displayName → 文件/目录名 → 回退到 name。</p>
         */
        private String displayName;

        /**
         * 主要文档标题。
         *
         * <p>从 {@code metadataJson.bundleSummary.titleHints} 提取。
         * 为 {@code null} 表示未提取到标题。</p>
         */
        private String primaryDocumentTitle;

        /** 资料源类型（{@code UPLOAD} / {@code GIT}）。 */
        private String sourceType;

        /** 内容画像（如 {@code code} / {@code document} / {@code mixed}）。 */
        private String contentProfile;

        /** 生命周期状态（{@code ACTIVE} / {@code DISABLED} / {@code ARCHIVED}）。 */
        private String status;

        /** 可见性（{@code NORMAL} / {@code ADMIN_ONLY}）。 */
        private String visibility;

        /** 默认同步模式（{@code AUTO} / {@code FULL} / {@code INCREMENTAL}）。 */
        private String defaultSyncMode;

        /** 最近一次同步运行主键。为 {@code null} 表示从未同步。 */
        private Long lastSyncRunId;

        /** 最近一次同步状态。为 {@code null} 表示从未同步。 */
        private String lastSyncStatus;

        /** 最近一次同步时间（ISO-8601）。为 {@code null} 表示从未同步。 */
        private String lastSyncAt;

        /** 最后更新时间（ISO-8601）。 */
        private String updatedAt;
    }

    /**
     * 资料源详情响应。
     *
     * <p>承载资料源详情页的完整基础字段——含配置 JSON、元数据 JSON 和同步信息，
     * 由 {@code toDetailResponse()} 组装。
     *
     * @author xiexu
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminKnowledgeSourceDetailResponse {

        /** 资料源主键。 */
        private Long id;

        /** 资料源编码。 */
        private String sourceCode;

        /** 资料源原始名称。 */
        private String name;

        /** 管理台展示名称（由 controller 从 metadataJson 计算）。 */
        private String displayName;

        /** 主要文档标题（从 metadataJson.bundleSummary.titleHints 提取）。 */
        private String primaryDocumentTitle;

        /** 资料源类型（{@code UPLOAD} / {@code GIT}）。 */
        private String sourceType;

        /** 内容画像。 */
        private String contentProfile;

        /** 生命周期状态（{@code ACTIVE} / {@code DISABLED} / {@code ARCHIVED}）。 */
        private String status;

        /** 可见性（{@code NORMAL} / {@code ADMIN_ONLY}）。 */
        private String visibility;

        /** 默认同步模式（{@code AUTO} / {@code FULL} / {@code INCREMENTAL}）。 */
        private String defaultSyncMode;

        /**
         * 资料源配置 JSON。
         *
         * <p>可能含 repo 路径、Vault 引用、文件路径等配置信息。可能为大型 JSON 字符串。</p>
         */
        private String configJson;

        /**
         * 资料源扩展元数据 JSON。
         *
         * <p>含 bundleSummary（displayName、titleHints、relativePathsSample）等信息。
         * 可能为大型 JSON 字符串。</p>
         */
        private String metadataJson;

        /** 最近一次 manifest 哈希（用于检测输入变更）。 */
        private String latestManifestHash;

        /** 最近一次同步运行主键。 */
        private Long lastSyncRunId;

        /** 最近一次同步状态。 */
        private String lastSyncStatus;

        /** 最近一次同步时间（ISO-8601）。 */
        private String lastSyncAt;

        /** 创建时间（ISO-8601）。 */
        private String createdAt;

        /** 最后更新时间（ISO-8601）。 */
        private String updatedAt;
    }

    /**
     * 资料源更新请求。
     *
     * <p>承载资料源名称、状态与配置的最小 PATCH 字段，由 Spring MVC 从 JSON 请求体绑定。
     * 各字段为空时 controller 沿用现有值，不做覆盖。
     *
     * @author xiexu
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminKnowledgeSourcePatchRequest {

        /** 资料源名称。为空时沿用现有名称。 */
        private String name;

        /** 生命周期状态。为空时沿用现有状态。必须为 {@code ACTIVE/DISABLED/ARCHIVED} 之一。 */
        private String status;

        /** 可见性。为空时沿用现有可见性。必须为 {@code NORMAL/ADMIN_ONLY} 之一。 */
        private String visibility;

        /** 默认同步模式。为空时沿用现有模式。必须为 {@code AUTO/FULL/INCREMENTAL} 之一。 */
        private String defaultSyncMode;

        /**
         * 目标配置 JSON 对象。
         *
         * <p>为 {@code null} 时沿用现有配置。controller 负责序列化为 JSON 字符串存储。
         * 该字段可能是大型 JSON 树（含 repo 路径、认证引用等），禁止参与 {@code toString()}。</p>
         */
        private JsonNode configJson;
    }
}
