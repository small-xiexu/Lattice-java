package com.xbk.lattice.documentparse.service;

import com.xbk.lattice.documentparse.domain.model.ParseCapability;
import com.xbk.lattice.documentparse.domain.model.ParseRoutePolicy;
import com.xbk.lattice.documentparse.domain.model.ProviderConnection;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 文档解析路由策略解析器
 *
 * 职责：按解析能力解析当前默认连接，供运行时 OCR 编排使用
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class DocumentParseRoutePolicyResolver {

    private final DocumentParseRoutePolicyAdminService documentParseRoutePolicyAdminService;

    private final DocumentParseConnectionAdminService documentParseConnectionAdminService;

    /**
     * 创建文档解析路由策略解析器。
     *
     * @param documentParseRoutePolicyAdminService 路由策略后台服务
     * @param documentParseConnectionAdminService 连接后台服务
     */
    public DocumentParseRoutePolicyResolver(
            DocumentParseRoutePolicyAdminService documentParseRoutePolicyAdminService,
            DocumentParseConnectionAdminService documentParseConnectionAdminService
    ) {
        this.documentParseRoutePolicyAdminService = documentParseRoutePolicyAdminService;
        this.documentParseConnectionAdminService = documentParseConnectionAdminService;
    }

    /**
     * 按解析能力解析默认连接。
     *
     * @param parseCapability 解析能力
     * @return 默认连接
     */
    public ProviderConnection resolveConnection(ParseCapability parseCapability) {
        ParseRoutePolicy parseRoutePolicy = documentParseRoutePolicyAdminService.getDefaultPolicy();
        Long connectionId = resolveConnectionId(parseRoutePolicy, parseCapability);
        if (connectionId == null) {
            throw new IllegalStateException(buildMissingConnectionMessage(parseCapability));
        }
        ProviderConnection providerConnection = documentParseConnectionAdminService.findConnection(connectionId)
                .orElseThrow(() -> new IllegalStateException(
                        "document parse connection not found: " + connectionId
                ));
        if (!providerConnection.isEnabled()) {
            throw new IllegalStateException("document parse connection is disabled: " + connectionId);
        }
        return providerConnection;
    }

    /**
     * 根据能力解析连接主键。
     *
     * @param parseRoutePolicy 路由策略
     * @param parseCapability 解析能力
     * @return 连接主键
     */
    private Long resolveConnectionId(ParseRoutePolicy parseRoutePolicy, ParseCapability parseCapability) {
        if (ParseCapability.IMAGE_OCR.equals(parseCapability)) {
            return parseRoutePolicy.getImageConnectionId();
        }
        if (ParseCapability.SCANNED_PDF_OCR.equals(parseCapability)) {
            return parseRoutePolicy.getScannedPdfConnectionId();
        }
        throw new IllegalArgumentException("Unsupported parse capability: " + parseCapability);
    }

    /**
     * 构造缺失连接时的提示信息。
     *
     * @param parseCapability 解析能力
     * @return 错误信息
     */
    private String buildMissingConnectionMessage(ParseCapability parseCapability) {
        if (ParseCapability.IMAGE_OCR.equals(parseCapability)) {
            return "document parse image OCR connection is not configured";
        }
        if (ParseCapability.SCANNED_PDF_OCR.equals(parseCapability)) {
            return "document parse scanned PDF OCR connection is not configured";
        }
        return "document parse route policy is not configured";
    }
}
