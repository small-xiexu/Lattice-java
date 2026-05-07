package com.xbk.lattice.documentparse.service;

import com.xbk.lattice.documentparse.domain.model.ParseRoutePolicy;
import com.xbk.lattice.documentparse.domain.model.ProviderConnection;
import com.xbk.lattice.documentparse.infra.persistence.DocumentParseConnectionJdbcRepository;
import com.xbk.lattice.documentparse.infra.persistence.DocumentParseRoutePolicyJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 文档解析连接后台服务
 *
 * 职责：管理连接的增删改查，并在连接删除时维护默认路由策略引用
 *
 * @author xiexu
 */
@Service
public class DocumentParseConnectionAdminService {

    private final DocumentParseConnectionJdbcRepository documentParseConnectionJdbcRepository;

    private final DocumentParseRoutePolicyJdbcRepository documentParseRoutePolicyJdbcRepository;

    /**
     * 创建文档解析连接后台服务。
     *
     * @param documentParseConnectionJdbcRepository 连接仓储
     * @param documentParseRoutePolicyJdbcRepository 路由策略仓储
     */
    public DocumentParseConnectionAdminService(
            DocumentParseConnectionJdbcRepository documentParseConnectionJdbcRepository,
            DocumentParseRoutePolicyJdbcRepository documentParseRoutePolicyJdbcRepository
    ) {
        this.documentParseConnectionJdbcRepository = documentParseConnectionJdbcRepository;
        this.documentParseRoutePolicyJdbcRepository = documentParseRoutePolicyJdbcRepository;
    }

    /**
     * 返回全部连接配置。
     *
     * @return 连接配置列表
     */
    public List<ProviderConnection> listConnections() {
        return documentParseConnectionJdbcRepository.findAll();
    }

    /**
     * 按主键查询连接配置。
     *
     * @param id 主键
     * @return 连接配置
     */
    public Optional<ProviderConnection> findConnection(Long id) {
        return documentParseConnectionJdbcRepository.findById(id);
    }

    /**
     * 保存连接配置。
     *
     * @param connection 连接配置
     * @return 保存后的连接配置
     */
    @Transactional(rollbackFor = Exception.class)
    public ProviderConnection saveConnection(ProviderConnection connection) {
        return documentParseConnectionJdbcRepository.save(connection);
    }

    /**
     * 删除连接配置。
     *
     * @param id 连接主键
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConnection(Long id) {
        Optional<ParseRoutePolicy> existingPolicy = documentParseRoutePolicyJdbcRepository.findDefault();
        if (existingPolicy.isPresent()) {
            ParseRoutePolicy policy = existingPolicy.orElseThrow();
            Long imageConnectionId = policy.getImageConnectionId();
            Long scannedPdfConnectionId = policy.getScannedPdfConnectionId();
            boolean imageMatched = id != null && id.equals(imageConnectionId);
            boolean scannedMatched = id != null && id.equals(scannedPdfConnectionId);
            if (imageMatched || scannedMatched) {
                ParseRoutePolicy clearedPolicy = new ParseRoutePolicy(
                        policy.getId(),
                        policy.getPolicyScope(),
                        imageMatched ? null : imageConnectionId,
                        scannedMatched ? null : scannedPdfConnectionId,
                        policy.isCleanupEnabled(),
                        policy.getCleanupModelProfileId(),
                        policy.getFallbackPolicyJson(),
                        policy.getCreatedBy(),
                        "admin",
                        policy.getCreatedAt(),
                        policy.getUpdatedAt()
                );
                documentParseRoutePolicyJdbcRepository.save(clearedPolicy);
            }
        }
        documentParseConnectionJdbcRepository.deleteById(id);
    }
}
