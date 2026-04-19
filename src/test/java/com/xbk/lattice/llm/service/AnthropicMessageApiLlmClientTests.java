package com.xbk.lattice.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnthropicMessageApiLlmClient 测试
 *
 * 职责：验证 Anthropic 直连客户端会发送 JSON 字符串请求体，并正确解析响应
 *
 * @author xiexu
 */
class AnthropicMessageApiLlmClientTests {

    private HttpServer httpServer;

    /**
     * 关闭测试 HTTP 服务。
     */
    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    /**
     * 验证客户端会发送 JSON 字符串请求体，并解析 Claude Messages 响应。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldSendJsonStringBodyAndParseAnthropicResponse() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<String>();
        AtomicReference<String> apiKeyHeader = new AtomicReference<String>();
        AtomicReference<String> contentTypeHeader = new AtomicReference<String>();
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/v1/messages", new SuccessHandler(
                requestBody,
                apiKeyHeader,
                contentTypeHeader,
                "application/json"
        ));
        httpServer.start();
        int port = httpServer.getAddress().getPort();
        AnthropicMessageApiLlmClient llmClient = new AnthropicMessageApiLlmClient(
                RestClient.builder(),
                new ObjectMapper(),
                "http://127.0.0.1:" + port,
                "test-anthropic-key",
                "2023-06-01",
                "tools-2024-04-04,pdfs-2024-09-25,structured-outputs-2025-11-13",
                "claude-sonnet-4-6",
                64,
                0.2D,
                null,
                null
        );

        LlmCallResult result = llmClient.call("review-system", "review-user");

        assertThat(result.getContent()).isEqualTo("review ok");
        assertThat(result.getInputTokens()).isEqualTo(11);
        assertThat(result.getOutputTokens()).isEqualTo(7);
        assertThat(apiKeyHeader.get()).isEqualTo("test-anthropic-key");
        assertThat(contentTypeHeader.get()).contains("application/json");
        assertThat(requestBody.get()).contains("\"system\":\"review-system\"");
        assertThat(requestBody.get()).contains("\"model\":\"claude-sonnet-4-6\"");
        assertThat(requestBody.get()).contains("\"text\":\"review-user\"");
        assertThat(requestBody.get()).contains("\"max_tokens\":64");
    }

    /**
     * 验证客户端能兼容错误标记为 octet-stream 的 Claude JSON 响应。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldParseAnthropicResponseWhenGatewayReturnsOctetStream() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/v1/messages", new SuccessHandler(
                new AtomicReference<String>(),
                new AtomicReference<String>(),
                new AtomicReference<String>(),
                "application/octet-stream"
        ));
        httpServer.start();
        int port = httpServer.getAddress().getPort();
        AnthropicMessageApiLlmClient llmClient = new AnthropicMessageApiLlmClient(
                RestClient.builder(),
                new ObjectMapper(),
                "http://127.0.0.1:" + port,
                "test-anthropic-key",
                "2023-06-01",
                "tools-2024-04-04,pdfs-2024-09-25,structured-outputs-2025-11-13",
                "claude-sonnet-4-6",
                64,
                0.2D,
                null,
                null
        );

        LlmCallResult result = llmClient.call("review-system", "review-user");

        assertThat(result.getContent()).isEqualTo("review ok");
        assertThat(result.getInputTokens()).isEqualTo(11);
        assertThat(result.getOutputTokens()).isEqualTo(7);
    }

    /**
     * 成功响应处理器。
     *
     * 职责：记录请求头与请求体，并回写 Claude Messages JSON 响应
     *
     * @author xiexu
     */
    private static class SuccessHandler implements HttpHandler {

        private final AtomicReference<String> requestBody;

        private final AtomicReference<String> apiKeyHeader;

        private final AtomicReference<String> contentTypeHeader;

        private final String responseContentType;

        private SuccessHandler(
                AtomicReference<String> requestBody,
                AtomicReference<String> apiKeyHeader,
                AtomicReference<String> contentTypeHeader,
                String responseContentType
        ) {
            this.requestBody = requestBody;
            this.apiKeyHeader = apiKeyHeader;
            this.contentTypeHeader = contentTypeHeader;
            this.responseContentType = responseContentType;
        }

        /**
         * 处理测试请求。
         *
         * @param exchange HTTP 交换对象
         * @throws IOException IO 异常
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            contentTypeHeader.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            String responseBody = """
                    {
                      "id": "msg_test",
                      "type": "message",
                      "model": "claude-sonnet-4-6",
                      "role": "assistant",
                      "content": [
                        {
                          "type": "text",
                          "text": "review ok"
                        }
                      ],
                      "usage": {
                        "input_tokens": 11,
                        "output_tokens": 7
                      }
                    }
                    """;
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", responseContentType);
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        }
    }
}
