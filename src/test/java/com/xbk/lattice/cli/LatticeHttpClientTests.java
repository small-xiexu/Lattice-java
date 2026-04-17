package com.xbk.lattice.cli;

import com.sun.net.httpserver.HttpServer;
import com.xbk.lattice.api.admin.AdminOverviewPendingResponse;
import com.xbk.lattice.api.admin.AdminOverviewResponse;
import com.xbk.lattice.governance.QualityMetricsReport;
import com.xbk.lattice.governance.StatusSnapshot;
import com.xbk.lattice.cli.remote.LatticeHttpClient;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLI 远程 HTTP 客户端测试
 *
 * 职责：验证远程模式可调用已运行服务的 HTTP API
 *
 * @author xiexu
 */
class LatticeHttpClientTests {

    /**
     * 验证客户端可执行 GET 请求并反序列化响应。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldCallGetEndpoint() throws Exception {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/v1/admin/overview", exchange -> {
            String responseBody = """
                    {
                      "status": {
                        "articleCount": 1,
                        "sourceFileCount": 2,
                        "contributionCount": 3,
                        "pendingQueryCount": 4,
                        "reviewPendingArticleCount": 5
                      },
                      "quality": {
                        "totalArticles": 1,
                        "passedArticles": 1,
                        "pendingReviewArticles": 0,
                        "needsHumanReviewArticles": 0,
                        "contributionCount": 3,
                        "sourceFileCount": 2
                      },
                      "pending": {
                        "count": 0,
                        "items": []
                      }
                    }
                    """;
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBody.getBytes(StandardCharsets.UTF_8));
            }
        });
        httpServer.start();
        try {
            LatticeHttpClient latticeHttpClient = new LatticeHttpClient("http://127.0.0.1:" + httpServer.getAddress().getPort());

            AdminOverviewResponse adminOverviewResponse = latticeHttpClient.get(
                    "/api/v1/admin/overview",
                    java.util.Map.of(),
                    AdminOverviewResponse.class
            );

            assertThat(adminOverviewResponse.getStatus().getArticleCount()).isEqualTo(1);
            assertThat(adminOverviewResponse.getQuality().getContributionCount()).isEqualTo(3);
            assertThat(adminOverviewResponse.getPending().getCount()).isZero();
        }
        finally {
            httpServer.stop(0);
        }
    }
}
