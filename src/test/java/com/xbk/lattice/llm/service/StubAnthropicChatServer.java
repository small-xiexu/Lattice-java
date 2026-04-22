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
 * Anthropic Messages stub 服务。
 *
 * 职责：为动态 Anthropic ChatClient / executor 测试提供最小本地响应桩
 *
 * @author xiexu
 */
class StubAnthropicChatServer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpServer httpServer;

    private final String answerText;

    private final AtomicInteger requestCount = new AtomicInteger();

    private final List<String> capturedModels = new CopyOnWriteArrayList<String>();

    private final List<Double> capturedTopPs = new CopyOnWriteArrayList<Double>();

    private final List<Integer> capturedTopKs = new CopyOnWriteArrayList<Integer>();

    StubAnthropicChatServer(String answerText) throws IOException {
        this.answerText = answerText;
        this.httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        HttpHandler handler = new MessagesHandler();
        httpServer.createContext("/v1/messages", handler);
        httpServer.createContext("/messages", handler);
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

    List<Double> getCapturedTopPs() {
        return capturedTopPs;
    }

    List<Integer> getCapturedTopKs() {
        return capturedTopKs;
    }

    private final class MessagesHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode rootNode = OBJECT_MAPPER.readTree(requestBody);
            capturedModels.add(rootNode.path("model").asText());
            if (rootNode.has("top_p")) {
                capturedTopPs.add(Double.valueOf(rootNode.path("top_p").asDouble()));
            }
            if (rootNode.has("top_k")) {
                capturedTopKs.add(Integer.valueOf(rootNode.path("top_k").asInt()));
            }
            String responseBody = """
                    {
                      "id": "msg_test",
                      "type": "message",
                      "model": "%s",
                      "role": "assistant",
                      "content": [
                        {
                          "type": "text",
                          "text": "%s"
                        }
                      ],
                      "usage": {
                        "input_tokens": 11,
                        "output_tokens": 7
                      }
                    }
                    """.formatted(rootNode.path("model").asText(), answerText);
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        }
    }
}
