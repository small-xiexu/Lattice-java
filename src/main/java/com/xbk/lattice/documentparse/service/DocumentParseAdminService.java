package com.xbk.lattice.documentparse.service;

import com.xbk.lattice.documentparse.domain.DocumentParseProviderConnection;
import com.xbk.lattice.documentparse.domain.DocumentParseSettings;
import com.xbk.lattice.documentparse.infra.DocumentParseProviderConnectionJdbcRepository;
import com.xbk.lattice.documentparse.infra.DocumentParseSettingsJdbcRepository;
import com.xbk.lattice.llm.domain.LlmModelProfile;
import com.xbk.lattice.llm.service.LlmConfigAdminService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 文档解析配置后台服务
 *
 * 职责：封装文档解析连接与全局设置的后台管理操作
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class DocumentParseAdminService {

    private final DocumentParseProviderConnectionJdbcRepository documentParseProviderConnectionJdbcRepository;

    private final DocumentParseSettingsJdbcRepository documentParseSettingsJdbcRepository;

    private final LlmConfigAdminService llmConfigAdminService;

    /**
     * 创建文档解析配置后台服务。
     *
     * @param documentParseProviderConnectionJdbcRepository 连接仓储
     * @param documentParseSettingsJdbcRepository 设置仓储
     * @param llmConfigAdminService LLM 配置后台服务
     */
    public DocumentParseAdminService(
            DocumentParseProviderConnectionJdbcRepository documentParseProviderConnectionJdbcRepository,
            DocumentParseSettingsJdbcRepository documentParseSettingsJdbcRepository,
            LlmConfigAdminService llmConfigAdminService
    ) {
        this.documentParseProviderConnectionJdbcRepository = documentParseProviderConnectionJdbcRepository;
        this.documentParseSettingsJdbcRepository = documentParseSettingsJdbcRepository;
        this.llmConfigAdminService = llmConfigAdminService;
    }

    /**
     * 返回全部连接配置。
     *
     * @return 连接配置列表
     */
    public List<DocumentParseProviderConnection> listConnections() {
        return documentParseProviderConnectionJdbcRepository.findAll();
    }

    /**
     * 查询连接配置。
     *
     * @param id 主键
     * @return 连接配置
     */
    public Optional<DocumentParseProviderConnection> findConnection(Long id) {
        return documentParseProviderConnectionJdbcRepository.findById(id);
    }

    /**
     * 保存连接配置。
     *
     * @param connection 连接配置
     * @return 保存后的连接配置
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentParseProviderConnection saveConnection(DocumentParseProviderConnection connection) {
        return documentParseProviderConnectionJdbcRepository.save(connection);
    }

    /**
     * 删除连接配置。
     *
     * @param id 主键
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConnection(Long id) {
        DocumentParseSettings currentSettings = getSettings();
        if (id != null && id.equals(currentSettings.getDefaultConnectionId())) {
            DocumentParseSettings clearedSettings = new DocumentParseSettings(
                    currentSettings.getId(),
                    currentSettings.getConfigScope(),
                    null,
                    currentSettings.isImageOcrEnabled(),
                    currentSettings.isScannedPdfOcrEnabled(),
                    currentSettings.isCleanupEnabled(),
                    currentSettings.getCleanupModelProfileId(),
                    currentSettings.getCreatedBy(),
                    "admin",
                    currentSettings.getCreatedAt(),
                    currentSettings.getUpdatedAt()
            );
            documentParseSettingsJdbcRepository.save(clearedSettings);
        }
        documentParseProviderConnectionJdbcRepository.deleteById(id);
    }

    /**
     * 返回默认设置。
     *
     * @return 默认设置
     */
    public DocumentParseSettings getSettings() {
        return documentParseSettingsJdbcRepository.findDefault().orElse(DocumentParseSettings.defaultSettings());
    }

    /**
     * 保存默认设置。
     *
     * @param settings 设置
     * @return 保存后的设置
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentParseSettings saveSettings(DocumentParseSettings settings) {
        if (settings.getDefaultConnectionId() != null && findConnection(settings.getDefaultConnectionId()).isEmpty()) {
            throw new IllegalArgumentException("defaultConnectionId不存在: " + settings.getDefaultConnectionId());
        }
        if (settings.getCleanupModelProfileId() != null) {
            LlmModelProfile modelProfile = llmConfigAdminService.findModelProfile(settings.getCleanupModelProfileId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "cleanupModelProfileId不存在: " + settings.getCleanupModelProfileId()
                    ));
            if (LlmModelProfile.MODEL_KIND_EMBEDDING.equalsIgnoreCase(modelProfile.getModelKind())) {
                throw new IllegalArgumentException("cleanupModelProfileId必须指向对话模型");
            }
        }
        return documentParseSettingsJdbcRepository.save(settings);
    }
}
