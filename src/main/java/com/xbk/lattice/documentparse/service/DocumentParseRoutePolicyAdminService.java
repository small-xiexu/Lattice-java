package com.xbk.lattice.documentparse.service;

import com.xbk.lattice.documentparse.domain.model.ParseRoutePolicy;
import com.xbk.lattice.documentparse.domain.model.ProviderConnection;
import com.xbk.lattice.documentparse.infra.persistence.DocumentParseRoutePolicyJdbcRepository;
import com.xbk.lattice.llm.domain.LlmModelProfile;
import com.xbk.lattice.llm.service.LlmConfigAdminService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档解析路由策略后台服务
 *
 * 职责：管理默认路由策略，并校验连接与后整理模型引用是否合法
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class DocumentParseRoutePolicyAdminService {

    private final DocumentParseRoutePolicyJdbcRepository documentParseRoutePolicyJdbcRepository;

    private final DocumentParseConnectionAdminService documentParseConnectionAdminService;

    private final LlmConfigAdminService llmConfigAdminService;

    /**
     * 创建文档解析路由策略后台服务。
     *
     * @param documentParseRoutePolicyJdbcRepository 路由策略仓储
     * @param documentParseConnectionAdminService 连接后台服务
     * @param llmConfigAdminService LLM 配置后台服务
     */
    public DocumentParseRoutePolicyAdminService(
            DocumentParseRoutePolicyJdbcRepository documentParseRoutePolicyJdbcRepository,
            DocumentParseConnectionAdminService documentParseConnectionAdminService,
            LlmConfigAdminService llmConfigAdminService
    ) {
        this.documentParseRoutePolicyJdbcRepository = documentParseRoutePolicyJdbcRepository;
        this.documentParseConnectionAdminService = documentParseConnectionAdminService;
        this.llmConfigAdminService = llmConfigAdminService;
    }

    /**
     * 返回默认路由策略。
     *
     * @return 默认路由策略
     */
    public ParseRoutePolicy getDefaultPolicy() {
        return documentParseRoutePolicyJdbcRepository.findDefault().orElse(ParseRoutePolicy.defaultPolicy());
    }

    /**
     * 保存默认路由策略。
     *
     * @param policy 路由策略
     * @return 保存后的路由策略
     */
    @Transactional(rollbackFor = Exception.class)
    public ParseRoutePolicy saveDefaultPolicy(ParseRoutePolicy policy) {
        validateConnection(policy.getImageConnectionId(), "imageConnectionId");
        validateConnection(policy.getScannedPdfConnectionId(), "scannedPdfConnectionId");
        validateCleanupModel(policy.getCleanupModelProfileId());
        return documentParseRoutePolicyJdbcRepository.save(policy);
    }

    /**
     * 校验连接引用。
     *
     * @param connectionId 连接主键
     * @param fieldName 字段名
     */
    private void validateConnection(Long connectionId, String fieldName) {
        if (connectionId == null) {
            return;
        }
        ProviderConnection providerConnection = documentParseConnectionAdminService.findConnection(connectionId)
                .orElseThrow(() -> new IllegalArgumentException(fieldName + "不存在: " + connectionId));
        if (!providerConnection.isEnabled()) {
            throw new IllegalArgumentException(fieldName + "关联连接未启用: " + connectionId);
        }
    }

    /**
     * 校验后整理模型引用。
     *
     * @param cleanupModelProfileId 后整理模型主键
     */
    private void validateCleanupModel(Long cleanupModelProfileId) {
        if (cleanupModelProfileId == null) {
            return;
        }
        LlmModelProfile modelProfile = llmConfigAdminService.findModelProfile(cleanupModelProfileId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "cleanupModelProfileId不存在: " + cleanupModelProfileId
                ));
        if (LlmModelProfile.MODEL_KIND_EMBEDDING.equalsIgnoreCase(modelProfile.getModelKind())) {
            throw new IllegalArgumentException("cleanupModelProfileId必须指向对话模型");
        }
    }
}
