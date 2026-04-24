package com.xbk.lattice.documentparse.infra.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.documentparse.domain.DocumentParseMode;
import com.xbk.lattice.documentparse.domain.model.ParseCapability;
import com.xbk.lattice.documentparse.domain.model.ParseOutput;
import com.xbk.lattice.documentparse.domain.model.ParseRequest;
import com.xbk.lattice.documentparse.domain.model.ProviderConnection;
import com.xbk.lattice.documentparse.domain.model.ProviderProbeResult;
import com.xbk.lattice.documentparse.port.OcrProviderAdapter;
import com.xbk.lattice.llm.service.LlmSecretCryptoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * JSON Body OCR 基础适配器
 *
 * 职责：承载腾讯 / 阿里 / Google 这类 JSON Base64 OCR 协议的公共探测与正文抽取能力
 *
 * @author xiexu
 */
public abstract class AbstractJsonBodyOcrProviderAdapter implements OcrProviderAdapter {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final String supportedProviderType;

    private final RestClient.Builder restClientBuilder;

    private final ObjectMapper objectMapper;

    private final LlmSecretCryptoService llmSecretCryptoService;

    /**
     * 创建 JSON Body OCR 基础适配器。
     *
     * @param supportedProviderType 当前适配器负责的 Provider 类型
     * @param restClientBuilder RestClient 构建器
     * @param objectMapper Jackson 对象映射器
     * @param llmSecretCryptoService 密钥加解密服务
     */
    protected AbstractJsonBodyOcrProviderAdapter(
            String supportedProviderType,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            LlmSecretCryptoService llmSecretCryptoService
    ) {
        this.supportedProviderType = supportedProviderType;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.llmSecretCryptoService = llmSecretCryptoService;
    }

    /**
     * 判断当前适配器是否支持指定 Provider。
     *
     * @param providerType Provider 类型
     * @return 是否支持
     */
    @Override
    public boolean supportsProvider(String providerType) {
        return normalize(supportedProviderType).equals(normalize(providerType));
    }

    /**
     * 判断当前适配器是否支持指定能力。
     *
     * @param parseCapability 解析能力
     * @return 是否支持
     */
    @Override
    public boolean supportsCapability(ParseCapability parseCapability) {
        return ParseCapability.IMAGE_OCR.equals(parseCapability)
                || ParseCapability.SCANNED_PDF_OCR.equals(parseCapability);
    }

    /**
     * 探测当前连接是否可用。
     *
     * @param connection 连接配置
     * @return 探测结果
     */
    @Override
    public ProviderProbeResult probe(ProviderConnection connection) {
        String endpointPath = resolveEndpointPath(connection);
        String endpoint = buildEndpoint(connection.getBaseUrl(), endpointPath);
        RestClient.Builder builder = createConfiguredRestClientBuilder(connection);
        long startedAt = System.nanoTime();
        builder.build().post()
                .uri(endpointPath)
                .body("""
                        {"probe":true}
                        """)
                .retrieve()
                .toBodilessEntity();
        Long latencyMs = Long.valueOf(Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L));
        return new ProviderProbeResult(
                true,
                connection.getProviderType(),
                latencyMs,
                endpoint,
                "文档解析连接可用，耗时 " + latencyMs + " ms"
        );
    }

    /**
     * 调用 JSON Body OCR Provider 解析文件。
     *
     * @param connection 连接配置
     * @param parseCapability 解析能力
     * @param parseRequest 解析请求
     * @return 解析输出
     * @throws IOException IO 异常
     */
    @Override
    public ParseOutput parse(
            ProviderConnection connection,
            ParseCapability parseCapability,
            ParseRequest parseRequest
    ) throws IOException {
        if (ParseCapability.IMAGE_OCR.equals(parseCapability)) {
            return extractAndBuild(parseRequest, connection, "image", DocumentParseMode.OCR_IMAGE);
        }
        if (ParseCapability.SCANNED_PDF_OCR.equals(parseCapability)) {
            return extractAndBuild(parseRequest, connection, "pdf", DocumentParseMode.OCR_SCANNED_PDF);
        }
        throw new IllegalArgumentException("Unsupported parse capability: " + parseCapability);
    }

    /**
     * 基于统一 JSON Body 协议执行 OCR 并构造输出。
     *
     * @param parseRequest 解析请求
     * @param connection 连接配置
     * @param fileKind 文件类型
     * @param parseMode 解析模式
     * @return 解析输出
     * @throws IOException IO 异常
     */
    private ParseOutput extractAndBuild(
            ParseRequest parseRequest,
            ProviderConnection connection,
            String fileKind,
            DocumentParseMode parseMode
    ) throws IOException {
        Path filePath = parseRequest.getFilePath();
        byte[] contentBytes = Files.readAllBytes(filePath);
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("fileName", filePath.getFileName().toString());
        requestBody.put("fileKind", fileKind);
        requestBody.put("contentBase64", Base64.getEncoder().encodeToString(contentBytes));
        requestBody.put("contentType", resolveContentType(filePath, fileKind));

        String endpointPath = resolveEndpointPath(connection);
        RestClient.Builder builder = createConfiguredRestClientBuilder(connection);
        String responseBody = builder.build().post()
                .uri(endpointPath)
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
        metadata.put("endpointPath", endpointPath);
        return new ParseOutput(
                null,
                parseRequest.getRelativePath(),
                extractedText.trim(),
                "",
                "",
                parseRequest.getFormat(),
                parseRequest.getFileSize(),
                parseMode,
                connection.getProviderType(),
                metadata.toString(),
                true,
                parseRequest.getRelativePath()
        );
    }

    /**
     * 创建已注入基础参数和鉴权头的 RestClient 构建器。
     *
     * @param connection 连接配置
     * @return RestClient 构建器
     */
    private RestClient.Builder createConfiguredRestClientBuilder(ProviderConnection connection) {
        RestClient.Builder builder = restClientBuilder.clone()
                .requestFactory(createRequestFactory())
                .baseUrl(normalizeBaseUrl(connection.getBaseUrl()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        applyCredentialHeaders(builder, connection);
        return builder;
    }

    /**
     * 注入凭证 Header。
     *
     * @param builder RestClient 构建器
     * @param connection 连接配置
     */
    private void applyCredentialHeaders(RestClient.Builder builder, ProviderConnection connection) {
        String credential = llmSecretCryptoService.decrypt(connection.getCredentialCiphertext());
        if (!StringUtils.hasText(credential)) {
            return;
        }
        String normalizedCredential = credential.trim();
        if (!normalizedCredential.startsWith("{")) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + normalizedCredential);
            return;
        }
        JsonNode credentialNode = parseJsonObject(normalizedCredential, "credentialJson");
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

    /**
     * 返回当前 Provider 的默认接口路径。
     *
     * @return 默认接口路径
     */
    protected abstract String defaultEndpointPath();

    /**
     * 解析配置 JSON 中的接口路径。
     *
     * @param connection 连接配置
     * @return 接口路径
     */
    private String resolveEndpointPath(ProviderConnection connection) {
        JsonNode configNode = parseJsonObject(connection.getConfigJson(), "configJson");
        String endpointPath = readValue(configNode, "endpointPath");
        if (!StringUtils.hasText(endpointPath)) {
            endpointPath = defaultEndpointPath();
        }
        return normalizeEndpointPath(endpointPath);
    }

    /**
     * 从 OCR 响应中抽取正文。
     *
     * @param responseBody OCR 响应
     * @return 抽取正文
     */
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

    /**
     * 读取 JSON 中的首个非空字段值。
     *
     * @param rootNode JSON 根节点
     * @param fieldNames 候选字段
     * @return 字段值
     */
    private String readValue(JsonNode rootNode, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode valueNode = rootNode.path(fieldName);
            if (!valueNode.isMissingNode() && !valueNode.isNull() && StringUtils.hasText(valueNode.asText())) {
                return valueNode.asText().trim();
            }
        }
        return "";
    }

    /**
     * 读取点路径字段值。
     *
     * @param rootNode JSON 根节点
     * @param pathExpression 点路径表达式
     * @return 字段值
     */
    private String readPath(JsonNode rootNode, String pathExpression) {
        JsonNode currentNode = rootNode;
        String[] segments = pathExpression.split("\\.");
        for (String segment : segments) {
            currentNode = currentNode.path(segment);
            if (currentNode.isMissingNode() || currentNode.isNull()) {
                return "";
            }
        }
        return currentNode.isValueNode() ? currentNode.asText().trim() : "";
    }

    /**
     * 规范化并解析 JSON 对象。
     *
     * @param rawJson 原始 JSON
     * @param fieldName 字段名
     * @return JSON 对象
     */
    private JsonNode parseJsonObject(String rawJson, String fieldName) {
        if (!StringUtils.hasText(rawJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(rawJson.trim());
            if (!jsonNode.isObject()) {
                throw new IllegalArgumentException(fieldName + "必须是 JSON 对象");
            }
            return jsonNode;
        }
        catch (Exception exception) {
            throw new IllegalArgumentException(fieldName + "不是合法 JSON", exception);
        }
    }

    /**
     * 解析文件的 Content-Type。
     *
     * @param filePath 文件路径
     * @param fileKind 文件类型
     * @return Content-Type
     */
    private String resolveContentType(Path filePath, String fileKind) {
        if ("pdf".equals(fileKind)) {
            return "application/pdf";
        }
        String fileName = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (fileName.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    /**
     * 规范化基础地址。
     *
     * @param baseUrl 原始基础地址
     * @return 规范化基础地址
     */
    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("baseUrl不能为空");
        }
        String normalizedBaseUrl = baseUrl.trim();
        while (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return normalizedBaseUrl;
    }

    /**
     * 规范化接口路径。
     *
     * @param endpointPath 原始接口路径
     * @return 规范化接口路径
     */
    private String normalizeEndpointPath(String endpointPath) {
        if (!StringUtils.hasText(endpointPath)) {
            throw new IllegalArgumentException("endpointPath不能为空");
        }
        String normalizedEndpointPath = endpointPath.trim();
        if (!normalizedEndpointPath.startsWith("/")) {
            normalizedEndpointPath = "/" + normalizedEndpointPath;
        }
        return normalizedEndpointPath;
    }

    /**
     * 拼接完整接口地址。
     *
     * @param baseUrl 基础地址
     * @param endpointPath 接口路径
     * @return 完整接口地址
     */
    private String buildEndpoint(String baseUrl, String endpointPath) {
        return normalizeBaseUrl(baseUrl) + normalizeEndpointPath(endpointPath);
    }

    /**
     * 创建 HTTP 请求工厂。
     *
     * @return 请求工厂
     */
    private SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
        requestFactory.setReadTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
        requestFactory.setProxy(Proxy.NO_PROXY);
        return requestFactory;
    }

    /**
     * 按需追加 Header。
     *
     * @param builder RestClient 构建器
     * @param name Header 名称
     * @param value Header 值
     */
    private void addHeaderIfPresent(RestClient.Builder builder, String name, String value) {
        if (StringUtils.hasText(value)) {
            builder.defaultHeader(name, value);
        }
    }

    /**
     * 规范化 Provider 类型。
     *
     * @param providerType Provider 类型
     * @return 规范化结果
     */
    private String normalize(String providerType) {
        return providerType == null ? "" : providerType.trim().toLowerCase(Locale.ROOT);
    }
}
