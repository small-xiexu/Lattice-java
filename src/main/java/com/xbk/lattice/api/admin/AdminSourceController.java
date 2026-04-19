package com.xbk.lattice.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.domain.KnowledgeSourcePage;
import com.xbk.lattice.source.service.SourceService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@Profile("jdbc")
@RequestMapping("/api/v1/admin/sources")
public class AdminSourceController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "DISABLED", "ARCHIVED");

    private static final Set<String> ALLOWED_VISIBILITIES = Set.of("NORMAL", "ADMIN_ONLY");

    private static final Set<String> ALLOWED_SYNC_MODES = Set.of("AUTO", "FULL", "INCREMENTAL");

    private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of("UPLOAD", "GIT", "SERVER_DIR");

    private final SourceService sourceService;

    /**
     * 创建资料源后台控制器。
     *
     * @param sourceService 资料源服务
     */
    public AdminSourceController(SourceService sourceService) {
        this.sourceService = sourceService;
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
        if ("legacy-default".equals(existing.getSourceCode())) {
            throw new IllegalArgumentException("legacy-default source is read-only");
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

    private String formatTime(java.time.OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    /**
     * 资料源分页响应。
     *
     * 职责：承载资料源列表页的分页信息与数据项
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminKnowledgeSourcePageResponse {

        private Integer page;

        private Integer size;

        private Long total;

        private List<AdminKnowledgeSourceSummaryResponse> items;
    }

    /**
     * 资料源摘要响应。
     *
     * 职责：承载资料源列表页的最小展示字段
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminKnowledgeSourceSummaryResponse {

        private Long id;

        private String sourceCode;

        private String name;

        private String sourceType;

        private String contentProfile;

        private String status;

        private String visibility;

        private String defaultSyncMode;

        private Long lastSyncRunId;

        private String lastSyncStatus;

        private String lastSyncAt;

        private String updatedAt;
    }

    /**
     * 资料源详情响应。
     *
     * 职责：承载资料源详情页的完整基础字段
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminKnowledgeSourceDetailResponse {

        private Long id;

        private String sourceCode;

        private String name;

        private String sourceType;

        private String contentProfile;

        private String status;

        private String visibility;

        private String defaultSyncMode;

        private String configJson;

        private String metadataJson;

        private String latestManifestHash;

        private Long lastSyncRunId;

        private String lastSyncStatus;

        private String lastSyncAt;

        private String createdAt;

        private String updatedAt;
    }

    /**
     * 资料源更新请求。
     *
     * 职责：承载资料源名称、状态与配置的最小 PATCH 字段
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminKnowledgeSourcePatchRequest {

        private String name;

        private String status;

        private String visibility;

        private String defaultSyncMode;

        private JsonNode configJson;
    }
}
