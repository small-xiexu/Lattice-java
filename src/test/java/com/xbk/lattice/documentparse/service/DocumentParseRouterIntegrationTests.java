package com.xbk.lattice.documentparse.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.documentparse.domain.DocumentParseProviderConnection;
import com.xbk.lattice.documentparse.domain.DocumentParseSettings;
import com.xbk.lattice.llm.service.LlmSecretCryptoService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocumentParseRouter 集成测试
 *
 * 职责：验证 `.doc`、图片 OCR 与扫描 PDF OCR 的组件级解析行为
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_phase_c_document_parse_router_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_phase_c_document_parse_router_test",
        "spring.flyway.default-schema=lattice_phase_c_document_parse_router_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory",
        "lattice.llm.secret-encryption-key=test-phase-c-key-0123456789abcdef"
})
class DocumentParseRouterIntegrationTests {

    private HttpServer httpServer;

    @Autowired
    private DocumentParseRouter documentParseRouter;

    @Autowired
    private DocumentParseAdminService documentParseAdminService;

    @Autowired
    private LlmSecretCryptoService llmSecretCryptoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 关闭测试使用的本地 HTTP 服务。
     */
    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    /**
     * 验证 `.doc` 会走直读，图片与扫描 PDF 会走 OCR 分支。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldParseDocAndRouteImageAndScannedPdfThroughOcr(@TempDir Path tempDir) throws Exception {
        resetTables();
        AtomicReference<String> capturedAuthorization = new AtomicReference<String>("");
        int port = startOcrServer(capturedAuthorization);
        DocumentParseProviderConnection connection = documentParseAdminService.saveConnection(
                new DocumentParseProviderConnection(
                        null,
                        "ocr-main",
                        DocumentParseProviderConnection.PROVIDER_TENCENT_OCR,
                        "http://127.0.0.1:" + port,
                        "/ocr/v1/general-basic",
                        llmSecretCryptoService.encrypt("{\"apiKey\":\"phase-c-ocr-token\"}"),
                        "已配置 JSON 凭证",
                        "{}",
                        true,
                        "tester",
                        "tester",
                        null,
                        null
                )
        );
        documentParseAdminService.saveSettings(
                new DocumentParseSettings(
                        null,
                        DocumentParseSettings.DEFAULT_SCOPE,
                        connection.getId(),
                        true,
                        true,
                        false,
                        null,
                        "tester",
                        "tester",
                        null,
                        null
                )
        );

        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Path imagesDir = Files.createDirectories(tempDir.resolve("images"));
        Path legacyDocPath = docsDir.resolve("legacy-word.doc");
        try (InputStream inputStream = getClass().getResourceAsStream("/documentparse/legacy-word.doc")) {
            assertThat(inputStream).isNotNull();
            Files.copy(inputStream, legacyDocPath);
        }
        Path imagePath = imagesDir.resolve("receipt.png");
        Files.write(imagePath, new byte[]{1, 2, 3, 4, 5});
        Path scannedPdfPath = docsDir.resolve("scanned.pdf");
        writeBlankPdf(scannedPdfPath);

        RawSource docSource = documentParseRouter.parseRawSource(tempDir, legacyDocPath);
        RawSource imageSource = documentParseRouter.parseRawSource(tempDir, imagePath);
        RawSource pdfSource = documentParseRouter.parseRawSource(tempDir, scannedPdfPath);

        assertThat(docSource).isNotNull();
        assertThat(docSource.getFormat()).isEqualTo("doc");
        assertThat(docSource.getParseMode()).isEqualTo("office_extract");
        assertThat(docSource.getParseProvider()).isEqualTo("poi_hwpf");
        assertThat(docSource.getContent()).contains("Legacy DOC payment timeout");
        assertThat(docSource.getContent()).contains("retry=3");

        assertThat(imageSource).isNotNull();
        assertThat(imageSource.getParseMode()).isEqualTo("ocr_image");
        assertThat(imageSource.getParseProvider()).isEqualTo("tencent_ocr");
        assertThat(imageSource.getContent()).contains("OCR image extracted text");
        assertThat(imageSource.getMetadataJson()).contains("\"ocrApplied\":true");

        assertThat(pdfSource).isNotNull();
        assertThat(pdfSource.getParseMode()).isEqualTo("ocr_scanned_pdf");
        assertThat(pdfSource.getParseProvider()).isEqualTo("tencent_ocr");
        assertThat(pdfSource.getContent()).contains("OCR scanned pdf text");
        assertThat(capturedAuthorization.get()).isEqualTo("Bearer phase-c-ocr-token");
    }

    private int startOcrServer(AtomicReference<String> capturedAuthorization) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/ocr/v1/general-basic", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                capturedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String responseBody = requestBody.contains("\"fileKind\":\"pdf\"")
                        ? "{\"text\":\"OCR scanned pdf text\"}"
                        : "{\"text\":\"OCR image extracted text\"}";
                byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.close();
            }
        });
        httpServer.start();
        return httpServer.getAddress().getPort();
    }

    private void writeBlankPdf(Path pdfPath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdfPath.toFile());
        }
    }

    private void resetTables() {
        jdbcTemplate.execute("delete from document_parse_settings");
        jdbcTemplate.execute("delete from document_parse_provider_connections");
    }
}
