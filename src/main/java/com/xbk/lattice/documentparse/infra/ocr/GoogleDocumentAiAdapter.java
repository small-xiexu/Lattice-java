package com.xbk.lattice.documentparse.infra.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.documentparse.domain.model.ProviderConnection;
import com.xbk.lattice.llm.service.LlmSecretCryptoService;
import org.springframework.web.client.RestClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Google Document AI 适配器
 *
 * 职责：承接 Google Document AI 的 JSON Body 协议解析与探测能力
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class GoogleDocumentAiAdapter extends AbstractJsonBodyOcrProviderAdapter {

    /**
     * 创建 Google Document AI 适配器。
     *
     * @param restClientBuilder RestClient 构建器
     * @param objectMapper Jackson 对象映射器
     * @param llmSecretCryptoService 密钥加解密服务
     */
    public GoogleDocumentAiAdapter(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            LlmSecretCryptoService llmSecretCryptoService
    ) {
        super(ProviderConnection.PROVIDER_GOOGLE_DOCUMENT_AI, restClientBuilder, objectMapper, llmSecretCryptoService);
    }

    /**
     * 返回 Google Document AI 默认接口路径。
     *
     * @return 默认接口路径
     */
    @Override
    protected String defaultEndpointPath() {
        return "/v1/documents:process";
    }
}
