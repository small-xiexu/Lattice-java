package com.xbk.lattice.source.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.api.admin.AdminSourceCreateRequest;
import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.domain.SourceMaterializationResult;
import com.xbk.lattice.source.domain.SourceSyncRunDetail;
import com.xbk.lattice.source.domain.SourceValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 资料源同步工作流服务。
 *
 * 职责：承载 Git / SERVER_DIR 资料源创建、校验与同步编排
 *
 * @author xiexu
 */
@Service
public class SourceSyncWorkflowService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final SourceService sourceService;

    private final SourceMaterializationService sourceMaterializationService;

    private final SourceUploadService sourceUploadService;

    /**
     * 创建资料源同步工作流服务。
     *
     * @param sourceService 资料源服务
     * @param sourceMaterializationService 资料源物化服务
     * @param sourceUploadService 统一上传服务
     */
    public SourceSyncWorkflowService(
            SourceService sourceService,
            SourceMaterializationService sourceMaterializationService,
            SourceUploadService sourceUploadService
    ) {
        this.sourceService = sourceService;
        this.sourceMaterializationService = sourceMaterializationService;
        this.sourceUploadService = sourceUploadService;
    }

    /**
     * 创建 Git 资料源。
     *
     * @param request 请求
     * @return 资料源详情
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeSource createGitSource(AdminSourceCreateRequest request) {
        return createSource(request, "GIT");
    }

    /**
     * 创建服务器目录资料源。
     *
     * @param request 请求
     * @return 资料源详情
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeSource createServerDirSource(AdminSourceCreateRequest request) {
        return createSource(request, "SERVER_DIR");
    }

    /**
     * 校验资料源配置。
     *
     * @param sourceId 资料源主键
     * @return 校验结果
     * @throws IOException IO 异常
     */
    public SourceValidationResult validateSource(Long sourceId) throws IOException {
        KnowledgeSource source = requireSource(sourceId);
        return sourceMaterializationService.validate(source);
    }

    /**
     * 对资料源发起同步。
     *
     * @param sourceId 资料源主键
     * @return 同步运行详情
     * @throws IOException IO 异常
     */
    public SourceSyncRunDetail syncSource(Long sourceId) throws IOException {
        KnowledgeSource source = requireSource(sourceId);
        SourceMaterializationResult materializationResult = sourceMaterializationService.materialize(source);
        JsonNode materializationNode = readJson(materializationResult.getMetadataJson());
        return sourceUploadService.acceptMaterializedSource(
                materializationResult.getStagingDir(),
                source.getId(),
                source.getSourceType(),
                materializationNode
        );
    }

    /**
     * 查询最近同步运行。
     *
     * @param limit 返回数量
     * @return 最近同步运行
     */
    public List<SourceSyncRunDetail> listRecentRuns(int limit) {
        return sourceUploadService.listRecentRunDetails(limit);
    }

    private KnowledgeSource createSource(AdminSourceCreateRequest request, String sourceType) {
        String name = requireText(request.getName(), "name");
        String sourceCode = normalizeSourceCode(request.getSourceCode(), name);
        sourceService.findBySourceCode(sourceCode).ifPresent(existing -> {
            throw new IllegalArgumentException("sourceCode already exists: " + existing.getSourceCode());
        });
        String contentProfile = defaultValue(request.getContentProfile(), "DOCUMENT");
        String visibility = defaultValue(request.getVisibility(), "NORMAL");
        String defaultSyncMode = defaultValue(request.getDefaultSyncMode(), "AUTO");
        return sourceService.save(new KnowledgeSource(
                null,
                sourceCode,
                name,
                sourceType,
                contentProfile,
                "ACTIVE",
                visibility,
                defaultSyncMode,
                buildConfigJson(request, sourceType),
                "{}",
                null,
                null,
                null,
                null,
                null,
                null
        ));
    }

    private String buildConfigJson(AdminSourceCreateRequest request, String sourceType) {
        ObjectNode configNode = OBJECT_MAPPER.createObjectNode();
        if ("GIT".equals(sourceType)) {
            configNode.put("remoteUrl", requireText(request.getRemoteUrl(), "remoteUrl"));
            configNode.put("branch", defaultValue(request.getBranch(), "main"));
            if (StringUtils.hasText(request.getCredentialRef())) {
                configNode.put("credentialRef", request.getCredentialRef().trim());
            }
        }
        else if ("SERVER_DIR".equals(sourceType)) {
            configNode.put("serverDir", requireText(request.getServerDir(), "serverDir"));
        }
        else {
            throw new IllegalArgumentException("unsupported source type: " + sourceType);
        }
        return configNode.toString();
    }

    private KnowledgeSource requireSource(Long sourceId) {
        return sourceService.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("source not found: " + sourceId));
    }

    private JsonNode readJson(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        }
        catch (Exception exception) {
            throw new IllegalStateException("invalid materialization metadata json", exception);
        }
    }

    private String normalizeSourceCode(String sourceCode, String fallbackName) {
        String rawValue = StringUtils.hasText(sourceCode) ? sourceCode.trim() : fallbackName;
        String normalized = rawValue.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (!StringUtils.hasText(normalized)) {
            return "source";
        }
        return normalized;
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String defaultValue(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
