package com.xbk.lattice.documentparse.application;

import com.xbk.lattice.documentparse.domain.model.ParseCapability;
import com.xbk.lattice.documentparse.domain.model.ParseOutput;
import com.xbk.lattice.documentparse.domain.model.ParseRequest;
import com.xbk.lattice.documentparse.domain.model.ProviderConnection;
import com.xbk.lattice.documentparse.domain.model.ProviderProbeResult;
import com.xbk.lattice.documentparse.port.OcrProviderAdapter;
import com.xbk.lattice.documentparse.service.DocumentParseRoutePolicyResolver;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * OCR Provider 注册表
 *
 * 职责：根据当前默认路由策略定位可用的 OCR Provider Adapter，并触发统一探测与解析
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class OcrProviderRegistry {

    private final List<OcrProviderAdapter> ocrProviderAdapters;

    private final DocumentParseRoutePolicyResolver documentParseRoutePolicyResolver;

    /**
     * 创建 OCR Provider 注册表。
     *
     * @param ocrProviderAdapters OCR Provider Adapter 集合
     * @param documentParseRoutePolicyResolver 路由策略解析器
     */
    public OcrProviderRegistry(
            List<OcrProviderAdapter> ocrProviderAdapters,
            DocumentParseRoutePolicyResolver documentParseRoutePolicyResolver
    ) {
        this.ocrProviderAdapters = ocrProviderAdapters;
        this.documentParseRoutePolicyResolver = documentParseRoutePolicyResolver;
    }

    /**
     * 触发 OCR 解析。
     *
     * @param parseCapability 解析能力
     * @param parseRequest 解析请求
     * @return 解析输出
     * @throws IOException IO 异常
     */
    public ParseOutput parse(ParseCapability parseCapability, ParseRequest parseRequest) throws IOException {
        ProviderConnection connection = documentParseRoutePolicyResolver.resolveConnection(parseCapability);
        OcrProviderAdapter ocrProviderAdapter = resolveAdapter(connection.getProviderType(), parseCapability);
        return ocrProviderAdapter.parse(connection, parseCapability, parseRequest);
    }

    /**
     * 探测指定连接是否可用。
     *
     * @param connection 连接配置
     * @return 探测结果
     */
    public ProviderProbeResult probe(ProviderConnection connection) {
        OcrProviderAdapter ocrProviderAdapter = resolveAdapter(connection.getProviderType(), null);
        return ocrProviderAdapter.probe(connection);
    }

    /**
     * 解析适配器。
     *
     * @param providerType Provider 类型
     * @param parseCapability 解析能力
     * @return OCR Provider Adapter
     */
    private OcrProviderAdapter resolveAdapter(String providerType, ParseCapability parseCapability) {
        for (OcrProviderAdapter ocrProviderAdapter : ocrProviderAdapters) {
            boolean providerSupported = ocrProviderAdapter.supportsProvider(providerType);
            boolean capabilitySupported = parseCapability == null
                    || ocrProviderAdapter.supportsCapability(parseCapability);
            if (providerSupported && capabilitySupported) {
                return ocrProviderAdapter;
            }
        }
        throw new IllegalArgumentException("No OCR adapter found for providerType: " + providerType);
    }
}
