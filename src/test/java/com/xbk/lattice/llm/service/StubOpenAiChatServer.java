package com.xbk.lattice.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OpenAI Chat Completions stub 服务。
 *
 * 职责：为动态 ChatClient / executor 测试提供最小本地响应桩
 *
 * @author xiexu
 */
class StubOpenAiChatServer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpServer httpServer;

    private final String answerText;

    private final int transientFailureCount;

    private final AtomicInteger requestCount = new AtomicInteger();

    private final List<String> capturedModels = new CopyOnWriteArrayList<String>();

    private final List<String> capturedTransferEncodings = new CopyOnWriteArrayList<String>();

    private final List<String> capturedContentLengths = new CopyOnWriteArrayList<String>();

    private final List<String> capturedConnectionHeaders = new CopyOnWriteArrayList<String>();

    StubOpenAiChatServer(String answerText) throws IOException {
        this(answerText, 0);
    }

    StubOpenAiChatServer(String answerText, int transientFailureCount) throws IOException {
        this.answerText = answerText;
        this.transientFailureCount = transientFailureCount;
        this.httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        HttpHandler handler = new ChatCompletionsHandler();
        httpServer.createContext("/v1/chat/completions", handler);
        httpServer.createContext("/chat/completions", handler);
    }

    void start() {
        httpServer.start();
    }

    void stop() {
        httpServer.stop(0);
    }

    String getBaseUrl() {
        return "http://127.0.0.1:" + httpServer.getAddress().getPort();
    }

    int getRequestCount() {
        return requestCount.get();
    }

    List<String> getCapturedModels() {
        return capturedModels;
    }

    List<String> getCapturedTransferEncodings() {
        return capturedTransferEncodings;
    }

    List<String> getCapturedContentLengths() {
        return capturedContentLengths;
    }

    List<String> getCapturedConnectionHeaders() {
        return capturedConnectionHeaders;
    }

    private final class ChatCompletionsHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            int currentRequestCount = requestCount.incrementAndGet();
            if (currentRequestCount <= transientFailureCount) {
                byte[] responseBytes = "{\"error\":\"temporary upstream failure\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.close();
                return;
            }
            capturedTransferEncodings.add(exchange.getRequestHeaders().getFirst("Transfer-encoding"));
            capturedContentLengths.add(exchange.getRequestHeaders().getFirst("Content-length"));
            capturedConnectionHeaders.add(exchange.getRequestHeaders().getFirst("Connection"));
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode rootNode = OBJECT_MAPPER.readTree(requestBody);
            capturedModels.add(rootNode.path("model").asText());
            String responseBody = """
                    {
                      "id": "chatcmpl_test",
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "%s"
                          }
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 9,
                        "completion_tokens": 4,
                        "total_tokens": 13
                      }
                    }
                    """.formatted(answerText);
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        }
    }
}
