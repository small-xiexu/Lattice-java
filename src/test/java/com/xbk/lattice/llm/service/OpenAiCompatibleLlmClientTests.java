package com.xbk.lattice.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenAiCompatibleLlmClient 测试
 *
 * 职责：验证 OpenAI 兼容客户端能处理标准 JSON 与错误标注的 octet-stream 响应
 *
 * @author xiexu
 */
class OpenAiCompatibleLlmClientTests {

    private HttpServer httpServer;

    private ServerSocket socketServer;

    private ExecutorService executorService;

    private Future<?> socketServerFuture;

    /**
     * 关闭测试 HTTP 服务。
     */
    @AfterEach
    void tearDown() throws Exception {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        if (socketServer != null && !socketServer.isClosed()) {
            socketServer.close();
        }
        if (socketServerFuture != null) {
            socketServerFuture.get(5, TimeUnit.SECONDS);
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    /**
     * 验证客户端能兼容错误标记为 octet-stream 的 Chat Completions 响应。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldParseOpenAiResponseWhenGatewayReturnsOctetStream() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<String>();
        AtomicReference<String> authorizationHeader = new AtomicReference<String>();
        AtomicReference<String> contentTypeHeader = new AtomicReference<String>();
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/v1/chat/completions", new SuccessHandler(
                requestBody,
                authorizationHeader,
                contentTypeHeader,
                "application/octet-stream"
        ));
        httpServer.start();
        int port = httpServer.getAddress().getPort();
        OpenAiCompatibleLlmClient llmClient = new OpenAiCompatibleLlmClient(
                RestClient.builder(),
                new ObjectMapper(),
                "http://127.0.0.1:" + port,
                "test-openai-key",
                "gpt-5.4",
                0.3D,
                96,
                120,
                null
        );

        LlmCallResult result = llmClient.call("query-system", "query-user");

        assertThat(result.getContent()).isEqualTo("answer ok");
        assertThat(result.getInputTokens()).isEqualTo(9);
        assertThat(result.getOutputTokens()).isEqualTo(4);
        assertThat(result.getProviderRequestId()).isEqualTo("chatcmpl_test");
        assertThat(authorizationHeader.get()).isEqualTo("Bearer test-openai-key");
        assertThat(contentTypeHeader.get()).contains("application/json");
        assertThat(requestBody.get()).contains("\"model\":\"gpt-5.4\"");
        assertThat(requestBody.get()).contains("\"role\":\"system\"");
        assertThat(requestBody.get()).contains("\"content\":\"query-user\"");
    }

    /**
     * 验证客户端在网关直接断开连接时会立即失败，不再自行重试。
     *
     * @throws Exception 异常
     */
    @Test
    void shouldFailFastWhenGatewayClosesConnectionBeforeResponse() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        socketServer = new ServerSocket(0);
        executorService = Executors.newSingleThreadExecutor();
        socketServerFuture = executorService.submit(() -> {
            while (requestCount.get() < 1) {
                try (Socket socket = socketServer.accept()) {
                    consumeHttpRequest(socket.getInputStream());
                    requestCount.incrementAndGet();
                }
            }
            return null;
        });
        OpenAiCompatibleLlmClient llmClient = new OpenAiCompatibleLlmClient(
                RestClient.builder(),
                new ObjectMapper(),
                "http://127.0.0.1:" + socketServer.getLocalPort(),
                "test-openai-key",
                "gpt-5.4",
                0.3D,
                96,
                120,
                null
        );

        assertThatThrownBy(() -> llmClient.call("query-system", "query-user"))
                .isInstanceOf(RuntimeException.class);
        assertThat(requestCount.get()).isEqualTo(1);
    }

    /**
     * 验证客户端不会为连接提前断开维护内部重试预算。
     *
     * @throws Exception 异常
     */
    @Test
    void shouldStopAfterSingleAttemptWhenGatewayClosesConnectionRepeatedly() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        socketServer = new ServerSocket(0);
        executorService = Executors.newSingleThreadExecutor();
        socketServerFuture = executorService.submit(() -> {
            while (requestCount.get() < 1) {
                try (Socket socket = socketServer.accept()) {
                    consumeHttpRequest(socket.getInputStream());
                    requestCount.incrementAndGet();
                }
            }
            return null;
        });
        OpenAiCompatibleLlmClient llmClient = new OpenAiCompatibleLlmClient(
                RestClient.builder(),
                new ObjectMapper(),
                "http://127.0.0.1:" + socketServer.getLocalPort(),
                "test-openai-key",
                "gpt-5.4",
                0.3D,
                96,
                120,
                null
        );

        assertThatThrownBy(() -> llmClient.call("query-system", "query-user"))
                .isInstanceOf(RuntimeException.class);
        assertThat(requestCount.get()).isEqualTo(1);
    }

    /**
     * 验证客户端收到可恢复的 5xx 响应时会立即抛错，不再自行重试。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldFailFastWhenGatewayReturnsRecoverableServerError() throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/v1/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            byte[] responseBytes = "{\"error\":\"temporary upstream failure\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(502, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        httpServer.start();
        int port = httpServer.getAddress().getPort();
        OpenAiCompatibleLlmClient llmClient = new OpenAiCompatibleLlmClient(
                RestClient.builder(),
                new ObjectMapper(),
                "http://127.0.0.1:" + port,
                "test-openai-key",
                "gpt-5.4",
                0.3D,
                96,
                120,
                null
        );

        assertThatThrownBy(() -> llmClient.call("query-system", "query-user"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("502");
        assertThat(requestCount.get()).isEqualTo(1);
    }

    private void consumeHttpRequest(InputStream inputStream) throws IOException {
        BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
        StringBuilder headerBuilder = new StringBuilder();
        int previous3 = -1;
        int previous2 = -1;
        int previous1 = -1;
        int current;
        while ((current = bufferedInputStream.read()) != -1) {
            headerBuilder.append((char) current);
            if (previous3 == '\r' && previous2 == '\n' && previous1 == '\r' && current == '\n') {
                break;
            }
            previous3 = previous2;
            previous2 = previous1;
            previous1 = current;
        }
        int contentLength = resolveContentLength(headerBuilder.toString());
        bufferedInputStream.readNBytes(contentLength);
    }

    private int resolveContentLength(String headers) {
        for (String line : headers.split("\\r\\n")) {
            if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                return Integer.parseInt(line.substring("content-length:".length()).trim());
            }
        }
        return 0;
    }

    private void writeSuccessResponse(OutputStream outputStream, String contentType) throws IOException {
        String responseBody = """
                {
                  "id": "chatcmpl_test",
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "answer ok"
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 9,
                    "completion_tokens": 4
                  }
                }
                """;
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        String responseHeaders = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + responseBytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        outputStream.write(responseHeaders.getBytes(StandardCharsets.UTF_8));
        outputStream.write(responseBytes);
        outputStream.flush();
    }

    /**
     * 成功响应处理器。
     *
     * 职责：记录请求头与请求体，并回写 OpenAI Chat Completions JSON 响应
     *
     * @author xiexu
     */
    private static class SuccessHandler implements HttpHandler {

        private final AtomicReference<String> requestBody;

        private final AtomicReference<String> authorizationHeader;

        private final AtomicReference<String> contentTypeHeader;

        private final String responseContentType;

        private SuccessHandler(
                AtomicReference<String> requestBody,
                AtomicReference<String> authorizationHeader,
                AtomicReference<String> contentTypeHeader,
                String responseContentType
        ) {
            this.requestBody = requestBody;
            this.authorizationHeader = authorizationHeader;
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
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentTypeHeader.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            String responseBody = """
                    {
                      "id": "chatcmpl_test",
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "answer ok"
                          }
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 9,
                        "completion_tokens": 4
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
