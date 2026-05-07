package com.xbk.lattice.documentparse.infra.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.documentparse.domain.model.ProviderConnection;
import com.xbk.lattice.llm.service.LlmSecretCryptoService;
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OCR 适配器
 *
 * 职责：承接阿里云 OCR 的 JSON Body 协议解析与探测能力
 *
 * @author xiexu
 */
@Component
public class AliyunOcrAdapter extends AbstractJsonBodyOcrProviderAdapter {

    /**
     * 创建阿里云 OCR 适配器。
     *
     * @param restClientBuilder RestClient 构建器
     * @param objectMapper Jackson 对象映射器
     * @param llmSecretCryptoService 密钥加解密服务
     */
    public AliyunOcrAdapter(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            LlmSecretCryptoService llmSecretCryptoService
    ) {
        super(ProviderConnection.PROVIDER_ALIYUN_OCR, restClientBuilder, objectMapper, llmSecretCryptoService);
    }

    /**
     * 返回阿里云 OCR 默认接口路径。
     *
     * @return 默认接口路径
     */
    @Override
    protected String defaultEndpointPath() {
        return "/ocr/v1/general";
    }
}
