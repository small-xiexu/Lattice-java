package com.xbk.lattice.documentparse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.documentparse.domain.DocumentParseProviderConnection;
import com.xbk.lattice.documentparse.domain.DocumentParseSettings;
import com.xbk.lattice.llm.service.LlmSecretCryptoService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

/**
 * 文档解析 OCR 服务
 *
 * 职责：调用已配置的文档解析连接，为图片与扫描 PDF 提供 OCR 文本抽取
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class DocumentParseOcrService {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final RestClient.Builder restClientBuilder;

    private final ObjectMapper objectMapper;

    private final DocumentParseAdminService documentParseAdminService;

    private final LlmSecretCryptoService llmSecretCryptoService;

    /**
     * 创建文档解析 OCR 服务。
     *
     * @param restClientBuilder RestClient 构建器
     * @param objectMapper Jackson 对象映射器
     * @param documentParseAdminService 文档解析后台服务
     * @param llmSecretCryptoService 密钥加解密服务
     */
    public DocumentParseOcrService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            DocumentParseAdminService documentParseAdminService,
            LlmSecretCryptoService llmSecretCryptoService
    ) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.documentParseAdminService = documentParseAdminService;
        this.llmSecretCryptoService = llmSecretCryptoService;
    }

    /**
     * 执行图片 OCR。
     *
     * @param imagePath 图片路径
     * @return OCR 结果
     * @throws IOException IO 异常
     */
    public OcrExtractionResult extractImage(Path imagePath) throws IOException {
        return extract(imagePath, "image");
    }

    /**
     * 执行扫描 PDF OCR。
     *
     * @param pdfPath PDF 路径
     * @return OCR 结果
     * @throws IOException IO 异常
     */
    public OcrExtractionResult extractScannedPdf(Path pdfPath) throws IOException {
        return extract(pdfPath, "pdf");
    }

    private OcrExtractionResult extract(Path filePath, String fileKind) throws IOException {
        DocumentParseSettings settings = documentParseAdminService.getSettings();
        if (settings.getDefaultConnectionId() == null) {
            throw new IllegalStateException("document parse default connection is not configured");
        }
        DocumentParseProviderConnection connection = documentParseAdminService.findConnection(settings.getDefaultConnectionId())
                .orElseThrow(() -> new IllegalStateException(
                        "document parse connection not found: " + settings.getDefaultConnectionId()
                ));
        byte[] contentBytes = Files.readAllBytes(filePath);
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("fileName", filePath.getFileName().toString());
        requestBody.put("fileKind", fileKind);
        requestBody.put("contentBase64", Base64.getEncoder().encodeToString(contentBytes));
        requestBody.put("contentType", resolveContentType(filePath, fileKind));

        RestClient.Builder builder = restClientBuilder.clone()
                .requestFactory(createRequestFactory())
                .baseUrl(connection.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        applyCredentialHeaders(builder, connection);
        String responseBody = builder.build().post()
                .uri(connection.getEndpointPath())
                .body(requestBody.toString())
                .retrieve()
                .body(String.class);
        String extractedText = extractText(responseBody);
        if (!StringUtils.hasText(extractedText)) {
            throw new IllegalStateException("OCR 返回内容为空");
        }
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("providerType", connection.getProviderType());
        metadata.put("fileKind", fileKind);
        metadata.put("ocrApplied", true);
        metadata.put("endpointPath", connection.getEndpointPath());
        return new OcrExtractionResult(extractedText.trim(), connection.getProviderType(), metadata.toString());
    }

    private void applyCredentialHeaders(RestClient.Builder builder, DocumentParseProviderConnection connection) {
        String credential = llmSecretCryptoService.decrypt(connection.getCredentialCiphertext());
        if (!StringUtils.hasText(credential)) {
            return;
        }
        if (!credential.trim().startsWith("{")) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + credential.trim());
            return;
        }
        try {
            JsonNode credentialNode = objectMapper.readTree(credential);
            addHeaderIfPresent(builder, HttpHeaders.AUTHORIZATION, "Bearer " + readValue(
                    credentialNode,
                    "apiKey",
                    "token",
                    "bearerToken"
            ));
            addHeaderIfPresent(builder, "x-api-key", readValue(credentialNode, "xApiKey", "apiKey"));
            addHeaderIfPresent(builder, "x-secret-id", readValue(credentialNode, "secretId"));
            addHeaderIfPresent(builder, "x-secret-key", readValue(credentialNode, "secretKey"));
            addHeaderIfPresent(builder, "x-access-key-id", readValue(credentialNode, "accessKeyId"));
            addHeaderIfPresent(builder, "x-access-key-secret", readValue(credentialNode, "accessKeySecret"));
            addHeaderIfPresent(builder, "x-project-id", readValue(credentialNode, "projectId"));
        }
        catch (Exception exception) {
            throw new IllegalStateException("Invalid OCR credential JSON", exception);
        }
    }

    private void addHeaderIfPresent(RestClient.Builder builder, String name, String value) {
        if (StringUtils.hasText(value)) {
            builder.defaultHeader(name, value);
        }
    }

    private String readValue(JsonNode rootNode, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode valueNode = rootNode.path(fieldName);
            if (!valueNode.isMissingNode() && !valueNode.isNull() && StringUtils.hasText(valueNode.asText())) {
                return valueNode.asText().trim();
            }
        }
        return "";
    }

    private String extractText(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            String[] candidatePaths = {"text", "content", "fullText", "data.text", "data.content", "result.text"};
            for (String candidatePath : candidatePaths) {
                String value = readPath(rootNode, candidatePath);
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
            return "";
        }
        catch (Exception exception) {
            return responseBody;
        }
    }

    private String readPath(JsonNode rootNode, String pathExpression) {
        String[] parts = pathExpression.split("\\.");
        JsonNode currentNode = rootNode;
        for (String part : parts) {
            currentNode = currentNode.path(part);
            if (currentNode.isMissingNode() || currentNode.isNull()) {
                return "";
            }
        }
        return currentNode.asText("");
    }

    private String resolveContentType(Path filePath, String fileKind) throws IOException {
        String probeContentType = Files.probeContentType(filePath);
        if (StringUtils.hasText(probeContentType)) {
            return probeContentType;
        }
        if ("pdf".equals(fileKind)) {
            return "application/pdf";
        }
        return "application/octet-stream";
    }

    private SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.toIntExact(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS).toMillis());
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        requestFactory.setProxy(Proxy.NO_PROXY);
        return requestFactory;
    }

    /**
     * OCR 抽取结果
     *
     * 职责：承载 OCR 返回的文本、供应商与元数据
     *
     * @author xiexu
     */
    public static class OcrExtractionResult {

        private final String content;

        private final String providerType;

        private final String metadataJson;

        /**
         * 创建 OCR 抽取结果。
         *
         * @param content 抽取正文
         * @param providerType 供应商类型
         * @param metadataJson 元数据 JSON
         */
        public OcrExtractionResult(String content, String providerType, String metadataJson) {
            this.content = content;
            this.providerType = providerType;
            this.metadataJson = metadataJson;
        }

        /**
         * 返回抽取正文。
         *
         * @return 抽取正文
         */
        public String getContent() {
            return content;
        }

        /**
         * 返回供应商类型。
         *
         * @return 供应商类型
         */
        public String getProviderType() {
            return providerType;
        }

        /**
         * 返回元数据 JSON。
         *
         * @return 元数据 JSON
         */
        public String getMetadataJson() {
            return metadataJson;
        }
    }
}
