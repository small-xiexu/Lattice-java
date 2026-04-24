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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/**
 * TextIn xParse OCR Provider 适配器
 *
 * 职责：按 TextIn 官方 multipart 协议完成连接探测、图片解析与扫描 PDF 解析
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class TextInXParseAdapter implements OcrProviderAdapter {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final RestClient.Builder restClientBuilder;

    private final ObjectMapper objectMapper;

    private final LlmSecretCryptoService llmSecretCryptoService;

    /**
     * 创建 TextIn xParse 适配器。
     *
     * @param restClientBuilder RestClient 构建器
     * @param objectMapper Jackson 对象映射器
     * @param llmSecretCryptoService 密钥加解密服务
     */
    public TextInXParseAdapter(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            LlmSecretCryptoService llmSecretCryptoService
    ) {
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
        return ProviderConnection.PROVIDER_TEXTIN_XPARSE.equals(normalize(providerType));
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
        long startedAt = System.nanoTime();
        JsonNode responseNode = executeMultipartRequest(
                connection,
                endpointPath,
                buildMultipartBody(
                        "probe.pdf",
                        "application/pdf",
                        buildProbePdfBytes(),
                        resolveParseConfigPayload(connection)
                )
        );
        ensureSuccessfulResponse(responseNode);
        Long latencyMs = Long.valueOf(Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L));
        return new ProviderProbeResult(
                true,
                connection.getProviderType(),
                latencyMs,
                endpoint,
                "TextIn xParse 连接可用，耗时 " + latencyMs + " ms"
        );
    }

    /**
     * 调用 TextIn xParse 解析文件。
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
        if (!supportsCapability(parseCapability)) {
            throw new IllegalArgumentException("Unsupported parse capability: " + parseCapability);
        }
        String endpointPath = resolveEndpointPath(connection);
        String parseConfigPayload = resolveParseConfigPayload(connection);
        byte[] fileBytes = Files.readAllBytes(parseRequest.getFilePath());
        String contentType = resolveContentType(parseRequest.getFilePath(), parseCapability);
        JsonNode responseNode = executeMultipartRequest(
                connection,
                endpointPath,
                buildMultipartBody(
                        parseRequest.getFilePath().getFileName().toString(),
                        contentType,
                        fileBytes,
                        parseConfigPayload
                )
        );
        ensureSuccessfulResponse(responseNode);
        return buildOutput(connection, parseCapability, parseRequest, endpointPath, responseNode);
    }

    /**
     * 组装 TextIn 解析输出。
     *
     * @param connection 连接配置
     * @param parseCapability 解析能力
     * @param parseRequest 解析请求
     * @param endpointPath 接口路径
     * @param responseNode TextIn 响应
     * @return 统一解析输出
     */
    private ParseOutput buildOutput(
            ProviderConnection connection,
            ParseCapability parseCapability,
            ParseRequest parseRequest,
            String endpointPath,
            JsonNode responseNode
    ) {
        JsonNode dataNode = findFirstNode(responseNode, "data", "result");
        JsonNode resultNode = findFirstNode(responseNode, "data.result", "result");
        JsonNode elementsNode = findFirstNode(responseNode, "data.result.elements", "data.elements", "result.elements", "elements");
        String markdown = readFirstText(responseNode, "data.markdown", "data.result.markdown", "result.markdown", "markdown");
        String plainText = readFirstText(
                responseNode,
                "data.text",
                "data.content",
                "data.result.text",
                "data.result.content",
                "result.text",
                "result.content",
                "text",
                "content"
        );
        if (!StringUtils.hasText(plainText) && !StringUtils.hasText(markdown)) {
            plainText = flattenElements(elementsNode);
        }
        if (!StringUtils.hasText(plainText) && !StringUtils.hasText(markdown) && resultNode != null && resultNode.isTextual()) {
            plainText = resultNode.asText("");
        }
        if (!StringUtils.hasText(plainText) && !StringUtils.hasText(markdown) && dataNode != null && dataNode.isTextual()) {
            plainText = dataNode.asText("");
        }
        if (!StringUtils.hasText(plainText) && !StringUtils.hasText(markdown)) {
            throw new IllegalStateException("TextIn xParse 返回内容为空");
        }

        ObjectNode metadataNode = objectMapper.createObjectNode();
        metadataNode.put("providerType", connection.getProviderType());
        metadataNode.put("fileKind", resolveFileKind(parseCapability));
        metadataNode.put("ocrApplied", true);
        metadataNode.put("endpointPath", endpointPath);
        String requestId = readFirstText(responseNode, "requestId", "request_id", "traceId", "trace_id");
        if (StringUtils.hasText(requestId)) {
            metadataNode.put("requestId", requestId);
        }
        String message = readFirstText(responseNode, "msg", "message");
        if (StringUtils.hasText(message)) {
            metadataNode.put("message", message);
        }

        return new ParseOutput(
                null,
                parseRequest.getRelativePath(),
                StringUtils.hasText(markdown) ? "" : normalizeText(plainText),
                normalizeText(markdown),
                resolveStructuredContentJson(resultNode, elementsNode),
                parseRequest.getFormat(),
                parseRequest.getFileSize(),
                resolveParseMode(parseCapability),
                connection.getProviderType(),
                metadataNode.toString(),
                true,
                parseRequest.getRelativePath()
        );
    }

    /**
     * 创建已带鉴权头的 RestClient。
     *
     * @param connection 连接配置
     * @return RestClient 构建器
     */
    private RestClient.Builder createConfiguredRestClientBuilder(ProviderConnection connection) {
        RestClient.Builder builder = restClientBuilder.clone()
                .requestFactory(createRequestFactory())
                .baseUrl(normalizeBaseUrl(connection.getBaseUrl()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        applyCredentialHeaders(builder, connection);
        return builder;
    }

    /**
     * 执行 multipart 请求。
     *
     * @param connection 连接配置
     * @param endpointPath 接口路径
     * @param multipartBody multipart 请求体
     * @return JSON 响应
     */
    private JsonNode executeMultipartRequest(
            ProviderConnection connection,
            String endpointPath,
            MultiValueMap<String, Object> multipartBody
    ) {
        String responseBody = createConfiguredRestClientBuilder(connection)
                .build()
                .post()
                .uri(endpointPath)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipartBody)
                .retrieve()
                .body(String.class);
        try {
            return objectMapper.readTree(responseBody);
        }
        catch (IOException exception) {
            throw new IllegalStateException("TextIn xParse 响应不是合法 JSON", exception);
        }
    }

    /**
     * 注入 TextIn 所需鉴权头。
     *
     * @param builder RestClient 构建器
     * @param connection 连接配置
     */
    private void applyCredentialHeaders(RestClient.Builder builder, ProviderConnection connection) {
        JsonNode credentialNode = parseCredentialJson(connection);
        String appId = readFirstText(credentialNode, "appId");
        String secretCode = readFirstText(credentialNode, "secretCode");
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(secretCode)) {
            throw new IllegalArgumentException("TextIn xParse 需要 appId 和 secretCode");
        }
        builder.defaultHeader("x-ti-app-id", appId);
        builder.defaultHeader("x-ti-secret-code", secretCode);
    }

    /**
     * 构造 multipart 请求体。
     *
     * @param fileName 文件名
     * @param contentType 文件 Content-Type
     * @param fileBytes 文件内容
     * @param parseConfigPayload 解析配置 JSON
     * @return 请求体
     */
    private MultiValueMap<String, Object> buildMultipartBody(
            String fileName,
            String contentType,
            byte[] fileBytes,
            String parseConfigPayload
    ) {
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<String, Object>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        body.add("file", new HttpEntity<ByteArrayResource>(
                new NamedByteArrayResource(fileName, fileBytes),
                fileHeaders
        ));
        if (StringUtils.hasText(parseConfigPayload)) {
            HttpHeaders configHeaders = new HttpHeaders();
            configHeaders.setContentType(MediaType.TEXT_PLAIN);
            body.add("config", new HttpEntity<String>(parseConfigPayload, configHeaders));
        }
        return body;
    }

    /**
     * 校验 TextIn 响应是否成功。
     *
     * @param responseNode 响应 JSON
     */
    private void ensureSuccessfulResponse(JsonNode responseNode) {
        JsonNode codeNode = responseNode.path("code");
        if (!codeNode.isMissingNode() && !codeNode.isNull()) {
            int code = codeNode.asInt(Integer.MIN_VALUE);
            if (code != 0 && code != 200) {
                throw new IllegalStateException("TextIn xParse 请求失败: " + resolveErrorMessage(responseNode));
            }
        }
        JsonNode successNode = responseNode.path("success");
        if (!successNode.isMissingNode() && successNode.isBoolean() && !successNode.asBoolean()) {
            throw new IllegalStateException("TextIn xParse 请求失败: " + resolveErrorMessage(responseNode));
        }
    }

    /**
     * 解析配置 JSON 中的接口路径。
     *
     * @param connection 连接配置
     * @return 接口路径
     */
    private String resolveEndpointPath(ProviderConnection connection) {
        JsonNode configNode = parseJsonObject(connection.getConfigJson(), "configJson");
        String endpointPath = readFirstText(configNode, "endpointPath");
        if (!StringUtils.hasText(endpointPath)) {
            endpointPath = "/api/v1/xparse/parse/sync";
        }
        return normalizeEndpointPath(endpointPath);
    }

    /**
     * 解析 TextIn 透传配置。
     *
     * @param connection 连接配置
     * @return 归一化后的 JSON 字符串
     */
    private String resolveParseConfigPayload(ProviderConnection connection) {
        JsonNode configNode = parseJsonObject(connection.getConfigJson(), "configJson");
        String parseConfigJson = readFirstText(configNode, "parseConfigJson");
        if (!StringUtils.hasText(parseConfigJson)) {
            return "";
        }
        return normalizeJsonObject(parseConfigJson, "parseConfigJson");
    }

    /**
     * 解析凭证 JSON。
     *
     * @param connection 连接配置
     * @return 凭证 JSON
     */
    private JsonNode parseCredentialJson(ProviderConnection connection) {
        String credentialJson = llmSecretCryptoService.decrypt(connection.getCredentialCiphertext());
        if (!StringUtils.hasText(credentialJson)) {
            throw new IllegalArgumentException("TextIn xParse 缺少 credentialJson");
        }
        return parseJsonObject(credentialJson, "credentialJson");
    }

    /**
     * 构造探测用 PDF。
     *
     * @return PDF 字节数组
     */
    private byte[] buildProbePdfBytes() {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText("textin probe");
                contentStream.endText();
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
        catch (IOException exception) {
            throw new IllegalStateException("构造 TextIn probe PDF 失败", exception);
        }
    }

    /**
     * 提取首个非空字段值。
     *
     * @param rootNode JSON 根节点
     * @param paths 候选路径
     * @return 非空字段值
     */
    private String readFirstText(JsonNode rootNode, String... paths) {
        for (String path : paths) {
            JsonNode node = findFirstNode(rootNode, path);
            if (node != null && node.isValueNode() && StringUtils.hasText(node.asText())) {
                return node.asText().trim();
            }
        }
        return "";
    }

    /**
     * 按点路径查找首个存在的节点。
     *
     * @param rootNode JSON 根节点
     * @param paths 路径列表
     * @return 命中的节点
     */
    private JsonNode findFirstNode(JsonNode rootNode, String... paths) {
        for (String path : paths) {
            JsonNode currentNode = rootNode;
            if (currentNode == null) {
                return null;
            }
            String[] parts = path.split("\\.");
            boolean missing = false;
            for (String part : parts) {
                currentNode = currentNode.path(part);
                if (currentNode.isMissingNode() || currentNode.isNull()) {
                    missing = true;
                    break;
                }
            }
            if (!missing) {
                return currentNode;
            }
        }
        return null;
    }

    /**
     * 从 elements 数组中拼接纯文本。
     *
     * @param elementsNode elements 节点
     * @return 纯文本
     */
    private String flattenElements(JsonNode elementsNode) {
        if (elementsNode == null || !elementsNode.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode elementNode : elementsNode) {
            String value = readFirstText(elementNode, "text", "content");
            if (StringUtils.hasText(value)) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(value.trim());
            }
        }
        return builder.toString();
    }

    /**
     * 解析结构化内容 JSON。
     *
     * @param resultNode 结果节点
     * @param elementsNode elements 节点
     * @return 结构化内容 JSON
     */
    private String resolveStructuredContentJson(JsonNode resultNode, JsonNode elementsNode) {
        if (elementsNode != null && !elementsNode.isMissingNode() && !elementsNode.isNull()) {
            return elementsNode.toString();
        }
        if (resultNode != null && resultNode.isObject()) {
            return resultNode.toString();
        }
        return "";
    }

    /**
     * 解析错误提示。
     *
     * @param responseNode 响应 JSON
     * @return 错误提示
     */
    private String resolveErrorMessage(JsonNode responseNode) {
        String message = readFirstText(responseNode, "msg", "message", "data.msg", "data.message");
        if (StringUtils.hasText(message)) {
            return message;
        }
        return responseNode.toString();
    }

    /**
     * 解析 JSON 对象字符串。
     *
     * @param jsonValue JSON 字符串
     * @param fieldName 字段名
     * @return JSON 节点
     */
    private JsonNode parseJsonObject(String jsonValue, String fieldName) {
        String normalizedJson = StringUtils.hasText(jsonValue) ? jsonValue.trim() : "{}";
        try {
            JsonNode jsonNode = objectMapper.readTree(normalizedJson);
            if (!jsonNode.isObject()) {
                throw new IllegalArgumentException(fieldName + "必须是 JSON 对象");
            }
            return jsonNode;
        }
        catch (IOException exception) {
            throw new IllegalArgumentException(fieldName + "不是合法 JSON", exception);
        }
    }

    /**
     * 规范化 JSON 对象字符串。
     *
     * @param jsonValue JSON 字符串
     * @param fieldName 字段名
     * @return 规范化后的 JSON 字符串
     */
    private String normalizeJsonObject(String jsonValue, String fieldName) {
        try {
            return objectMapper.writeValueAsString(parseJsonObject(jsonValue, fieldName));
        }
        catch (Exception exception) {
            throw new IllegalArgumentException(fieldName + "不是合法 JSON", exception);
        }
    }

    /**
     * 解析文件 Content-Type。
     *
     * @param filePath 文件路径
     * @param parseCapability 解析能力
     * @return Content-Type
     * @throws IOException IO 异常
     */
    private String resolveContentType(Path filePath, ParseCapability parseCapability) throws IOException {
        String probeContentType = Files.probeContentType(filePath);
        if (StringUtils.hasText(probeContentType)) {
            return probeContentType;
        }
        if (ParseCapability.IMAGE_OCR.equals(parseCapability)) {
            return "image/*";
        }
        return "application/pdf";
    }

    /**
     * 创建请求工厂。
     *
     * @return 请求工厂
     */
    private SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.toIntExact(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS).toMillis());
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        requestFactory.setProxy(Proxy.NO_PROXY);
        return requestFactory;
    }

    /**
     * 解析文件类别。
     *
     * @param parseCapability 解析能力
     * @return 文件类别
     */
    private String resolveFileKind(ParseCapability parseCapability) {
        return ParseCapability.IMAGE_OCR.equals(parseCapability) ? "image" : "pdf";
    }

    /**
     * 解析解析模式。
     *
     * @param parseCapability 解析能力
     * @return 解析模式
     */
    private DocumentParseMode resolveParseMode(ParseCapability parseCapability) {
        return ParseCapability.IMAGE_OCR.equals(parseCapability)
                ? DocumentParseMode.OCR_IMAGE
                : DocumentParseMode.OCR_SCANNED_PDF;
    }

    /**
     * 规范化文本。
     *
     * @param value 输入文本
     * @return 规范化后的文本
     */
    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    /**
     * 规范化基础地址。
     *
     * @param baseUrl 基础地址
     * @return 规范化结果
     */
    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 规范化接口路径。
     *
     * @param endpointPath 接口路径
     * @return 规范化结果
     */
    private String normalizeEndpointPath(String endpointPath) {
        String normalized = endpointPath == null ? "" : endpointPath.trim();
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    /**
     * 组装完整请求地址。
     *
     * @param baseUrl 基础地址
     * @param endpointPath 接口路径
     * @return 完整地址
     */
    private String buildEndpoint(String baseUrl, String endpointPath) {
        return normalizeBaseUrl(baseUrl) + normalizeEndpointPath(endpointPath);
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

    /**
     * 带文件名的字节资源。
     */
    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(String filename, byte[] byteArray) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
