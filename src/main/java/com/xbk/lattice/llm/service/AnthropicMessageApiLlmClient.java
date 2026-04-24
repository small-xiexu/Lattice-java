package com.xbk.lattice.llm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
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
 * Anthropic Messages API LLM 客户端
 *
 * 职责：绕过 RestClient 对对象请求体的兼容问题，使用 JSON 字符串直连 Claude Messages API
 *
 * @author xiexu
 */
public class AnthropicMessageApiLlmClient implements LlmClient {

    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    private final String model;

    private final Integer maxTokens;

    private final Double temperature;

    private final Double topP;

    private final Integer topK;

    /**
     * 创建 Anthropic Messages API 客户端。
     *
     * @param restClientBuilder RestClient 构建器
     * @param objectMapper Jackson 对象映射器
     * @param connectionProperties Anthropic 连接配置
     * @param chatProperties Anthropic Chat 配置
     */
    public AnthropicMessageApiLlmClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            AnthropicConnectionProperties connectionProperties,
            AnthropicChatProperties chatProperties
    ) {
        this(
                restClientBuilder,
                objectMapper,
                connectionProperties.getBaseUrl(),
                connectionProperties.getApiKey(),
                connectionProperties.getVersion(),
                connectionProperties.getBetaVersion(),
                chatProperties.getOptions().getModel(),
                chatProperties.getOptions().getMaxTokens(),
                chatProperties.getOptions().getTemperature(),
                chatProperties.getOptions().getTopP(),
                chatProperties.getOptions().getTopK(),
                Integer.valueOf(300)
        );
    }

    /**
     * 创建 Anthropic Messages API 客户端。
     *
     * @param restClientBuilder RestClient 构建器
     * @param objectMapper Jackson 对象映射器
     * @param baseUrl Anthropic 基础地址
     * @param apiKey Anthropic API Key
     * @param version Anthropic 版本头
     * @param betaVersion Anthropic Beta 头
     * @param model 模型名称
     * @param maxTokens 最大输出 token
     * @param temperature 温度
     * @param topP topP 参数
     * @param topK topK 参数
     */
    public AnthropicMessageApiLlmClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            String baseUrl,
            String apiKey,
            String version,
            String betaVersion,
            String model,
            Integer maxTokens,
            Double temperature,
            Double topP,
            Integer topK
    ) {
        this(
                restClientBuilder,
                objectMapper,
                baseUrl,
                apiKey,
                version,
                betaVersion,
                model,
                maxTokens,
                temperature,
                topP,
                topK,
                Integer.valueOf(300)
        );
    }

    /**
     * 创建 Anthropic Messages API 客户端。
     *
     * @param restClientBuilder RestClient 构建器
     * @param objectMapper Jackson 对象映射器
     * @param baseUrl Anthropic 基础地址
     * @param apiKey Anthropic API Key
     * @param version Anthropic 版本头
     * @param betaVersion Anthropic Beta 头
     * @param model 模型名称
     * @param maxTokens 最大输出 token
     * @param temperature 温度
     * @param topP topP 参数
     * @param topK topK 参数
     * @param timeoutSeconds 超时秒数
     */
    public AnthropicMessageApiLlmClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            String baseUrl,
            String apiKey,
            String version,
            String betaVersion,
            String model,
            Integer maxTokens,
            Double temperature,
            Double topP,
            Integer topK,
            Integer timeoutSeconds
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
            clientBuilder.defaultHeader("x-api-key", apiKey);
        }
        if (StringUtils.hasText(version)) {
            clientBuilder.defaultHeader("anthropic-version", version);
        }
        if (StringUtils.hasText(betaVersion)) {
            clientBuilder.defaultHeader("anthropic-beta", betaVersion);
        }
        this.restClient = clientBuilder.build();
        this.objectMapper = objectMapper;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.topP = topP;
        this.topK = topK;
    }

    /**
     * 调用 Claude Messages API。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 调用结果
     */
    @Override
    public LlmCallResult call(String systemPrompt, String userPrompt) {
        String requestJson = serializeRequest(buildRequestBody(systemPrompt, userPrompt));
        byte[] responseBytes = restClient.post()
                .uri("/v1/messages")
                .body(requestJson)
                .retrieve()
                .body(byte[].class);
        String responseJson = decodeResponse(responseBytes);
        return parseResponse(systemPrompt, userPrompt, responseJson);
    }

    /**
     * 构建 Claude Messages 请求体。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 请求体
     */
    private Map<String, Object> buildRequestBody(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = new LinkedHashMap<String, Object>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(createUserMessage(userPrompt)));
        requestBody.put("stream", Boolean.FALSE);
        if (StringUtils.hasText(systemPrompt)) {
            requestBody.put("system", systemPrompt);
        }
        if (maxTokens != null) {
            requestBody.put("max_tokens", maxTokens);
        }
        if (temperature != null) {
            requestBody.put("temperature", temperature);
        }
        if (topP != null) {
            requestBody.put("top_p", topP);
        }
        if (topK != null) {
            requestBody.put("top_k", topK);
        }
        return requestBody;
    }

    /**
     * 创建用户消息块。
     *
     * @param userPrompt 用户提示词
     * @return 用户消息块
     */
    private Map<String, Object> createUserMessage(String userPrompt) {
        Map<String, Object> textBlock = new LinkedHashMap<String, Object>();
        textBlock.put("type", "text");
        textBlock.put("text", userPrompt);

        Map<String, Object> userMessage = new LinkedHashMap<String, Object>();
        userMessage.put("role", "user");
        userMessage.put("content", List.of(textBlock));
        return userMessage;
    }

    /**
     * 将请求体序列化为 JSON 字符串。
     *
     * @param requestBody 请求体
     * @return JSON 字符串
     */
    private String serializeRequest(Map<String, Object> requestBody) {
        try {
            return objectMapper.writeValueAsString(requestBody);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize Anthropic request", exception);
        }
    }

    private int resolveTimeout(Integer timeoutSeconds) {
        if (timeoutSeconds == null || timeoutSeconds.intValue() <= 0) {
            return 300;
        }
        return timeoutSeconds.intValue();
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

    /**
     * 解析 Claude Messages 响应。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param responseJson 响应 JSON
     * @return 调用结果
     */
    private LlmCallResult parseResponse(String systemPrompt, String userPrompt, String responseJson) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseJson);
            String content = extractContent(rootNode.path("content"));
            int inputTokens = readTokenCount(rootNode.path("usage").path("input_tokens"), systemPrompt + userPrompt);
            int outputTokens = readTokenCount(rootNode.path("usage").path("output_tokens"), content);
            return new LlmCallResult(content, inputTokens, outputTokens, readText(rootNode, "id"));
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse Anthropic response", exception);
        }
    }

    /**
     * 提取响应中的文本内容。
     *
     * @param contentNode content 节点
     * @return 拼接后的文本内容
     */
    private String extractContent(JsonNode contentNode) {
        if (!contentNode.isArray()) {
            return "";
        }
        List<String> textBlocks = new ArrayList<String>();
        for (JsonNode blockNode : contentNode) {
            if ("text".equals(blockNode.path("type").asText()) && blockNode.hasNonNull("text")) {
                textBlocks.add(blockNode.path("text").asText());
            }
        }
        return String.join("", textBlocks);
    }

    /**
     * 读取 token 数，缺失时使用估算值。
     *
     * @param tokenNode token 节点
     * @param fallbackText 估算文本
     * @return token 数
     */
    private int readTokenCount(JsonNode tokenNode, String fallbackText) {
        if (tokenNode != null && tokenNode.canConvertToInt()) {
            return tokenNode.asInt();
        }
        return estimateTokens(fallbackText);
    }

    private String readText(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        String value = node.path(fieldName).asText();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value;
    }

    /**
     * 估算 token 数。
     *
     * @param text 文本内容
     * @return 估算 token 数
     */
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
}
