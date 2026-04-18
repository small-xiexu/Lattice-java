package com.xbk.lattice.llm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.service.LlmCallResult;
import com.xbk.lattice.compiler.service.LlmClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.Proxy;
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
public class OpenAiCompatibleLlmClient implements LlmClient {

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
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setProxy(Proxy.NO_PROXY);
        requestFactory.setConnectTimeout(Duration.ofSeconds(resolveTimeout(timeoutSeconds)));
        requestFactory.setReadTimeout(Duration.ofSeconds(resolveTimeout(timeoutSeconds)));
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
     * 调用 OpenAI 兼容 Chat Completions。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 调用结果
     */
    @Override
    public LlmCallResult call(String systemPrompt, String userPrompt) {
        String requestJson = serialize(buildRequestBody(systemPrompt, userPrompt));
        byte[] responseBytes = restClient.post()
                .uri(completionPath)
                .body(requestJson)
                .retrieve()
                .body(byte[].class);
        String responseJson = decodeResponse(responseBytes);
        return parseResponse(systemPrompt, userPrompt, responseJson);
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
