package com.xbk.lattice.llm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.EOFException;
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容客户端
 *
 * 职责：面向 openai / openai_compatible Provider 发送 Chat Completions 请求
 *
 * @author xiexu
 */
@Slf4j
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final int MAX_RETRY_ATTEMPTS = 5;

    private static final long BASE_RETRY_BACKOFF_MILLIS = 300L;

    private static final ProxySelector NO_PROXY_SELECTOR = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress socketAddress, IOException exception) {
            // 明确忽略连接失败回调，避免干扰上层自己的重试策略。
        }
    };

    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    private final String modelName;

    private final Double temperature;

    private final Integer maxTokens;

    private final String extraOptionsJson;

    private final String completionPath;

    /**
     * 创建 OpenAI 兼容客户端。
     *
     * @param restClientBuilder RestClient 构建器
     * @param objectMapper Jackson 对象映射器
     * @param baseUrl 基础地址
     * @param apiKey API Key
     * @param modelName 模型名称
     * @param temperature 温度参数
     * @param maxTokens 最大输出 token
     * @param timeoutSeconds 超时秒数
     * @param extraOptionsJson 扩展参数 JSON
     */
    public OpenAiCompatibleLlmClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            String baseUrl,
            String apiKey,
            String modelName,
            Double temperature,
            Integer maxTokens,
            Integer timeoutSeconds,
            String extraOptionsJson
    ) {
        int resolvedTimeoutSeconds = resolveTimeout(timeoutSeconds);
        JdkClientHttpRequestFactory requestFactory = createRequestFactory(resolvedTimeoutSeconds);
        RestClient.Builder clientBuilder = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (StringUtils.hasText(apiKey)) {
            clientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        this.restClient = clientBuilder.build();
        this.objectMapper = objectMapper;
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.extraOptionsJson = extraOptionsJson;
        this.completionPath = resolveCompletionPath(baseUrl);
    }

    /**
     * 创建更稳态的 JDK HTTP 请求工厂。
     *
     * @param timeoutSeconds 超时秒数
     * @return 请求工厂
     */
    private JdkClientHttpRequestFactory createRequestFactory(int timeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder()
                .proxy(NO_PROXY_SELECTOR)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return requestFactory;
    }

    /**
     * 调用 OpenAI 兼容 Chat Completions。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 调用结果
     */
    @Override
    public LlmCallResult call(String systemPrompt, String userPrompt) {
        String requestJson = serialize(buildRequestBody(systemPrompt, userPrompt));
        byte[] responseBytes = executeRequestWithRetry(requestJson);
        String responseJson = decodeResponse(responseBytes);
        return parseResponse(systemPrompt, userPrompt, responseJson);
    }

    private byte[] executeRequestWithRetry(String requestJson) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return restClient.post()
                        .uri(completionPath)
                        .body(requestJson)
                        .retrieve()
                        .body(byte[].class);
            }
            catch (RuntimeException exception) {
                lastException = exception;
                if (!isRetryable(exception) || attempt >= MAX_RETRY_ATTEMPTS) {
                    throw exception;
                }
                log.warn(
                        "OpenAI compatible request failed on attempt {}/{} and will retry: {}",
                        attempt,
                        MAX_RETRY_ATTEMPTS,
                        summarizeException(exception)
                );
                sleepBeforeRetry(attempt);
            }
        }
        throw lastException == null
                ? new IllegalStateException("OpenAI compatible request failed without exception")
                : lastException;
    }

    private boolean isRetryable(RuntimeException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            int statusCode = responseException.getStatusCode().value();
            return statusCode == 408
                    || statusCode == 409
                    || statusCode == 425
                    || statusCode == 429
                    || statusCode >= 500;
        }
        if (!(exception instanceof RestClientException) && !(exception instanceof IllegalStateException)) {
            return false;
        }
        Throwable rootCause = rootCause(exception);
        return rootCause instanceof EOFException
                || rootCause instanceof SocketException
                || rootCause instanceof SocketTimeoutException
                || rootCause instanceof IOException;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String summarizeException(Throwable throwable) {
        Throwable rootCause = rootCause(throwable);
        String message = rootCause.getMessage();
        if (!StringUtils.hasText(message)) {
            return rootCause.getClass().getSimpleName();
        }
        return rootCause.getClass().getSimpleName() + ": " + message;
    }

    private void sleepBeforeRetry(int attempt) {
        long backoffMillis = BASE_RETRY_BACKOFF_MILLIS * attempt;
        try {
            Thread.sleep(backoffMillis);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI compatible request retry interrupted", exception);
        }
    }

    private Map<String, Object> buildRequestBody(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = new LinkedHashMap<String, Object>();
        requestBody.put("model", modelName);
        requestBody.put("messages", List.of(
                buildMessage("system", systemPrompt),
                buildMessage("user", userPrompt)
        ));
        requestBody.put("stream", Boolean.FALSE);
        if (temperature != null) {
            requestBody.put("temperature", temperature);
        }
        if (maxTokens != null) {
            requestBody.put("max_tokens", maxTokens);
        }
        mergeExtraOptions(requestBody);
        return requestBody;
    }

    private Map<String, Object> buildMessage(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private void mergeExtraOptions(Map<String, Object> requestBody) {
        if (!StringUtils.hasText(extraOptionsJson)) {
            return;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(extraOptionsJson);
            if (!rootNode.isObject()) {
                return;
            }
            rootNode.fields().forEachRemaining(entry -> requestBody.put(entry.getKey(), convertNode(entry.getValue())));
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse OpenAI extra options", exception);
        }
    }

    private Object convertNode(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        if (jsonNode.isBoolean()) {
            return jsonNode.booleanValue();
        }
        if (jsonNode.isInt() || jsonNode.isLong()) {
            return jsonNode.longValue();
        }
        if (jsonNode.isFloat() || jsonNode.isDouble() || jsonNode.isBigDecimal()) {
            return jsonNode.decimalValue();
        }
        if (jsonNode.isTextual()) {
            return jsonNode.textValue();
        }
        if (jsonNode.isArray()) {
            List<Object> values = new ArrayList<Object>();
            for (JsonNode itemNode : jsonNode) {
                values.add(convertNode(itemNode));
            }
            return values;
        }
        if (jsonNode.isObject()) {
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            jsonNode.fields().forEachRemaining(entry -> values.put(entry.getKey(), convertNode(entry.getValue())));
            return values;
        }
        return jsonNode.toString();
    }

    private String serialize(Map<String, Object> requestBody) {
        try {
            return objectMapper.writeValueAsString(requestBody);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize OpenAI request", exception);
        }
    }

    /**
     * 将原始响应字节解码为 UTF-8 文本。
     *
     * @param responseBytes 原始响应
     * @return 响应文本
     */
    private String decodeResponse(byte[] responseBytes) {
        if (responseBytes == null || responseBytes.length == 0) {
            return "";
        }
        return new String(responseBytes, StandardCharsets.UTF_8);
    }

    private LlmCallResult parseResponse(String systemPrompt, String userPrompt, String responseJson) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseJson);
            JsonNode firstChoice = rootNode.path("choices").path(0);
            String content = extractContent(firstChoice.path("message").path("content"));
            int inputTokens = readTokenCount(rootNode.path("usage").path("prompt_tokens"), systemPrompt + userPrompt);
            int outputTokens = readTokenCount(rootNode.path("usage").path("completion_tokens"), content);
            return new LlmCallResult(content, inputTokens, outputTokens);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse OpenAI response", exception);
        }
    }

    private String extractContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode itemNode : contentNode) {
                if (itemNode.hasNonNull("text")) {
                    builder.append(itemNode.path("text").asText());
                    continue;
                }
                if (itemNode.hasNonNull("content")) {
                    builder.append(itemNode.path("content").asText());
                }
            }
            return builder.toString();
        }
        return contentNode.toString();
    }

    private int readTokenCount(JsonNode tokenNode, String fallbackText) {
        if (tokenNode != null && tokenNode.canConvertToInt()) {
            return tokenNode.asInt();
        }
        return estimateTokens(fallbackText);
    }

    private int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int cjkChars = 0;
        for (int index = 0; index < text.length(); index++) {
            if (String.valueOf(text.charAt(index)).matches("[\\u4e00-\\u9fff]")) {
                cjkChars++;
            }
        }
        int nonCjkChars = text.length() - cjkChars;
        return (int) Math.ceil(cjkChars * 1.5D + nonCjkChars * 0.4D);
    }

    private String resolveCompletionPath(String baseUrl) {
        if (baseUrl != null && baseUrl.endsWith("/v1")) {
            return "/chat/completions";
        }
        return "/v1/chat/completions";
    }

    private int resolveTimeout(Integer timeoutSeconds) {
        if (timeoutSeconds == null || timeoutSeconds.intValue() <= 0) {
            return 300;
        }
        return timeoutSeconds.intValue();
    }
}
