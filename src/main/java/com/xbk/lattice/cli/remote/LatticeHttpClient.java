package com.xbk.lattice.cli.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * CLI 远程 HTTP 客户端
 *
 * 职责：封装 CLI 远程模式对 Web API 的调用
 *
 * @author xiexu
 */
public class LatticeHttpClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final String serverUrl;

    private final HttpClient httpClient;

    private final Duration requestTimeout;

    /**
     * 创建 CLI 远程 HTTP 客户端。
     *
     * @param serverUrl 服务地址
     */
    public LatticeHttpClient(String serverUrl) {
        this(serverUrl, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * 创建带自定义超时的 CLI 远程 HTTP 客户端。
     *
     * @param serverUrl 服务地址
     * @param requestTimeout 请求超时时间
     */
    public LatticeHttpClient(String serverUrl, Duration requestTimeout) {
        this.serverUrl = trimTrailingSlash(serverUrl);
        this.requestTimeout = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 执行 GET 请求。
     *
     * @param path 路径
     * @param queryParams 查询参数
     * @param responseType 响应类型
     * @param <T> 响应泛型
     * @return 响应对象
     * @throws IOException IO 异常
     * @throws InterruptedException 中断异常
     */
    public <T> T get(String path, Map<String, String> queryParams, Class<T> responseType)
            throws IOException, InterruptedException {
        String uri = buildUri(path, queryParams);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(requestTimeout)
                .GET()
                .build();
        return send(request, responseType);
    }

    /**
     * 执行 POST 请求。
     *
     * @param path 路径
     * @param requestBody 请求对象
     * @param responseType 响应类型
     * @param <T> 响应泛型
     * @return 响应对象
     * @throws IOException IO 异常
     * @throws InterruptedException 中断异常
     */
    public <T> T post(String path, Object requestBody, Class<T> responseType)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildUri(path, Map.of())))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(requestBody), StandardCharsets.UTF_8))
                .build();
        return send(request, responseType);
    }

    private <T> T send(HttpRequest request, Class<T> responseType)
            throws IOException, InterruptedException {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        catch (IOException exception) {
            throw new IllegalStateException("远程服务不可达: " + serverUrl, exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("远程调用失败: HTTP " + response.statusCode() + " " + response.body());
        }
        if (responseType == String.class) {
            return responseType.cast(response.body());
        }
        return OBJECT_MAPPER.readValue(response.body(), responseType);
    }

    private String buildUri(String path, Map<String, String> queryParams) {
        StringBuilder builder = new StringBuilder();
        builder.append(serverUrl);
        builder.append(path.startsWith("/") ? path : "/" + path);
        if (queryParams != null && !queryParams.isEmpty()) {
            builder.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                if (!first) {
                    builder.append("&");
                }
                builder.append(urlEncode(entry.getKey()));
                builder.append("=");
                builder.append(urlEncode(entry.getValue()));
                first = false;
            }
        }
        return builder.toString();
    }

    private String toJson(Object requestBody) {
        try {
            return OBJECT_MAPPER.writeValueAsString(requestBody);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化远程请求失败", exception);
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("serverUrl 不能为空");
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
