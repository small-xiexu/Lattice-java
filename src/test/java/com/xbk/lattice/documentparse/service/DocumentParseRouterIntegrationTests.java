package com.xbk.lattice.documentparse.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.service.SourceIngestSupport;
import com.xbk.lattice.documentparse.domain.model.ParseRoutePolicy;
import com.xbk.lattice.documentparse.domain.model.ProviderConnection;
import com.xbk.lattice.infra.persistence.SourceFileChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileChunkRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.infra.persistence.StructuredTableCellRecord;
import com.xbk.lattice.infra.persistence.StructuredTableJdbcRepository;
import com.xbk.lattice.infra.persistence.StructuredTableRecord;
import com.xbk.lattice.infra.persistence.StructuredTableRowRecord;
import com.xbk.lattice.llm.service.LlmSecretCryptoService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocumentParseRouter 集成测试
 *
 * 职责：验证 `.doc`、`.csv`、图片 OCR 与扫描 PDF OCR 的组件级解析行为
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
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
    private DocumentParseConnectionAdminService documentParseConnectionAdminService;

    @Autowired
    private DocumentParseRoutePolicyAdminService documentParseRoutePolicyAdminService;

    @Autowired
    private SourceIngestSupport sourceIngestSupport;

    @Autowired
    private StructuredTableJdbcRepository structuredTableJdbcRepository;

    @Autowired
    private SourceFileJdbcRepository sourceFileJdbcRepository;

    @Autowired
    private SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository;

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
     * 验证 `.doc`、`.docx`、`.xlsx`、`.pptx`、`.csv`、文本型 PDF 会走本地解析链，
     * 图片与扫描 PDF 会走 OCR 分支。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldParseDocCsvAndRouteImageAndScannedPdfThroughOcr(@TempDir Path tempDir) throws Exception {
        resetTables();
        AtomicReference<String> capturedSecretId = new AtomicReference<String>("");
        AtomicReference<String> capturedSecretKey = new AtomicReference<String>("");
        int port = startOcrServer(capturedSecretId, capturedSecretKey);
        ProviderConnection connection = documentParseConnectionAdminService.saveConnection(
                new ProviderConnection(
                        null,
                        "ocr-main",
                        ProviderConnection.PROVIDER_TENCENT_OCR,
                        "http://127.0.0.1:" + port,
                        llmSecretCryptoService.encrypt("{\"secretId\":\"phase-c-ocr-id\",\"secretKey\":\"phase-c-ocr-key\"}"),
                        "已配置 JSON 凭证",
                        "{\"endpointPath\":\"/ocr/v1/general-basic\"}",
                        true,
                        "tester",
                        "tester",
                        null,
                        null
                )
        );
        documentParseRoutePolicyAdminService.saveDefaultPolicy(
                new ParseRoutePolicy(
                        null,
                        ParseRoutePolicy.DEFAULT_SCOPE,
                        connection.getId(),
                        connection.getId(),
                        false,
                        null,
                        "{}",
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
        Path csvPath = docsDir.resolve("rules.csv");
        Files.writeString(csvPath, "businessSubTypeCode,meaning\n1210,refund", StandardCharsets.UTF_8);
        Path wordPath = docsDir.resolve("brief.docx");
        writeSimpleWord(wordPath);
        Path excelPath = docsDir.resolve("codes.xlsx");
        writeSimpleWorkbook(excelPath);
        Path pptPath = docsDir.resolve("briefing.pptx");
        writeSimplePresentation(pptPath);
        Path imagePath = imagesDir.resolve("receipt.png");
        Files.write(imagePath, new byte[]{1, 2, 3, 4, 5});
        Path textPdfPath = docsDir.resolve("text.pdf");
        writeTextPdf(textPdfPath, "Native pdf text");
        Path scannedPdfPath = docsDir.resolve("scanned.pdf");
        writeBlankPdf(scannedPdfPath);

        RawSource docSource = documentParseRouter.parseRawSource(tempDir, legacyDocPath);
        RawSource csvSource = documentParseRouter.parseRawSource(tempDir, csvPath);
        RawSource wordSource = documentParseRouter.parseRawSource(tempDir, wordPath);
        RawSource excelSource = documentParseRouter.parseRawSource(tempDir, excelPath);
        RawSource pptSource = documentParseRouter.parseRawSource(tempDir, pptPath);
        RawSource imageSource = documentParseRouter.parseRawSource(tempDir, imagePath);
        RawSource textPdfSource = documentParseRouter.parseRawSource(tempDir, textPdfPath);
        RawSource pdfSource = documentParseRouter.parseRawSource(tempDir, scannedPdfPath);

        assertThat(docSource).isNotNull();
        assertThat(docSource.getFormat()).isEqualTo("doc");
        assertThat(docSource.getParseMode()).isEqualTo("office_extract");
        assertThat(docSource.getParseProvider()).isEqualTo("poi_hwpf");
        assertThat(docSource.getMetadataJson()).contains("\"extractionStrategy\"");
        assertThat(docSource.getMetadataJson()).contains("\"listFormattingPreserved\":false");
        assertThat(docSource.getContent()).contains("Legacy DOC payment timeout");
        assertThat(docSource.getContent()).contains("retry=3");

        assertThat(csvSource).isNotNull();
        assertThat(csvSource.getFormat()).isEqualTo("csv");
        assertThat(csvSource.getParseMode()).isEqualTo("text_read");
        assertThat(csvSource.getParseProvider()).isEqualTo("filesystem");
        assertThat(csvSource.getContent()).contains("businessSubTypeCode,meaning");
        assertThat(csvSource.getContent()).contains("1210,refund");
        assertThat(csvSource.getMetadataJson()).contains("\"structuredContentJson\"");

        assertThat(wordSource).isNotNull();
        assertThat(wordSource.getParseMode()).isEqualTo("office_extract");
        assertThat(wordSource.getParseProvider()).isEqualTo("poi_xwpf");
        assertThat(wordSource.getContent()).contains("Payment timeout recovery");
        assertThat(wordSource.getMetadataJson()).contains("\"paragraphCount\"");

        assertThat(excelSource).isNotNull();
        assertThat(excelSource.getParseMode()).isEqualTo("office_extract");
        assertThat(excelSource.getParseProvider()).isEqualTo("poi_excel");
        assertThat(excelSource.getContent()).contains("=== Sheet: Codes ===");
        assertThat(excelSource.getMetadataJson()).contains("\"sheetCount\"");
        assertThat(excelSource.getMetadataJson()).contains("\"structuredContentJson\"");

        assertThat(pptSource).isNotNull();
        assertThat(pptSource.getParseMode()).isEqualTo("office_extract");
        assertThat(pptSource.getParseProvider()).isEqualTo("poi_ppt");
        assertThat(pptSource.getContent()).contains("=== Slide: 1 ===");
        assertThat(pptSource.getMetadataJson()).contains("\"slideCount\"");

        assertThat(imageSource).isNotNull();
        assertThat(imageSource.getParseMode()).isEqualTo("ocr_image");
        assertThat(imageSource.getParseProvider()).isEqualTo("tencent_ocr");
        assertThat(imageSource.getContent()).contains("OCR image extracted text");
        assertThat(imageSource.getMetadataJson()).contains("\"ocrApplied\":true");

        assertThat(textPdfSource).isNotNull();
        assertThat(textPdfSource.getParseMode()).isEqualTo("pdf_text");
        assertThat(textPdfSource.getParseProvider()).isEqualTo("pdfbox");
        assertThat(textPdfSource.getContent()).contains("Native pdf text");
        assertThat(textPdfSource.getMetadataJson()).contains("\"pageCount\"");

        assertThat(pdfSource).isNotNull();
        assertThat(pdfSource.getParseMode()).isEqualTo("ocr_scanned_pdf");
        assertThat(pdfSource.getParseProvider()).isEqualTo("tencent_ocr");
        assertThat(pdfSource.getContent()).contains("OCR scanned pdf text");
        assertThat(capturedSecretId.get()).isEqualTo("phase-c-ocr-id");
        assertThat(capturedSecretKey.get()).isEqualTo("phase-c-ocr-key");
    }

    /**
     * 验证 TextIn xParse Adapter 能处理图片 OCR 与扫描 PDF OCR。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldParseImageAndScannedPdfThroughTextInAdapter(@TempDir Path tempDir) throws Exception {
        resetTables();
        AtomicReference<String> capturedAppId = new AtomicReference<String>("");
        AtomicReference<String> capturedSecretCode = new AtomicReference<String>("");
        List<String> capturedBodies = new CopyOnWriteArrayList<>();
        int port = startTextInServer(capturedAppId, capturedSecretCode, capturedBodies);
        ProviderConnection connection = documentParseConnectionAdminService.saveConnection(
                new ProviderConnection(
                        null,
                        "textin-main",
                        ProviderConnection.PROVIDER_TEXTIN_XPARSE,
                        "http://127.0.0.1:" + port,
                        llmSecretCryptoService.encrypt("{\"appId\":\"textin-app-id\",\"secretCode\":\"textin-secret-code\"}"),
                        "已配置 JSON 凭证",
                        "{\"endpointPath\":\"/api/v1/xparse/parse/sync\",\"parseConfigJson\":\"{\\\"parse_mode\\\":\\\"scan\\\"}\"}",
                        true,
                        "tester",
                        "tester",
                        null,
                        null
                )
        );
        documentParseRoutePolicyAdminService.saveDefaultPolicy(
                new ParseRoutePolicy(
                        null,
                        ParseRoutePolicy.DEFAULT_SCOPE,
                        connection.getId(),
                        connection.getId(),
                        false,
                        null,
                        "{}",
                        "tester",
                        "tester",
                        null,
                        null
                )
        );

        Path imagesDir = Files.createDirectories(tempDir.resolve("images"));
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Path imagePath = imagesDir.resolve("receipt.png");
        Files.write(imagePath, new byte[]{1, 2, 3, 4, 5});
        Path scannedPdfPath = docsDir.resolve("scanned.pdf");
        writeBlankPdf(scannedPdfPath);

        RawSource imageSource = documentParseRouter.parseRawSource(tempDir, imagePath);
        RawSource pdfSource = documentParseRouter.parseRawSource(tempDir, scannedPdfPath);

        assertThat(imageSource).isNotNull();
        assertThat(imageSource.getParseMode()).isEqualTo("ocr_image");
        assertThat(imageSource.getParseProvider()).isEqualTo("textin_xparse");
        assertThat(imageSource.getContent()).contains("TextIn image extracted text");
        assertThat(imageSource.getMetadataJson()).contains("\"contentFormat\":\"markdown\"");

        assertThat(pdfSource).isNotNull();
        assertThat(pdfSource.getParseMode()).isEqualTo("ocr_scanned_pdf");
        assertThat(pdfSource.getParseProvider()).isEqualTo("textin_xparse");
        assertThat(pdfSource.getContent()).contains("TextIn scanned pdf text");
        assertThat(pdfSource.getMetadataJson()).contains("\"structuredContentJson\"");

        assertThat(capturedAppId.get()).isEqualTo("textin-app-id");
        assertThat(capturedSecretCode.get()).isEqualTo("textin-secret-code");
        assertThat(capturedBodies).anyMatch(body -> body.contains("filename=\"receipt.png\""));
        assertThat(capturedBodies).anyMatch(body -> body.contains("filename=\"scanned.pdf\""));
        assertThat(capturedBodies).anyMatch(body -> body.contains("name=\"config\""));
        assertThat(capturedBodies).anyMatch(body -> body.contains("{\"parse_mode\":\"scan\"}"));
    }

    /**
     * 验证编译入口摄入链会复用新文档解析内核处理图片 OCR 与扫描 PDF OCR。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldIngestImageAndScannedPdfThroughDocumentParseApplicationService(@TempDir Path tempDir) throws Exception {
        resetTables();
        AtomicReference<String> capturedAppId = new AtomicReference<String>("");
        AtomicReference<String> capturedSecretCode = new AtomicReference<String>("");
        List<String> capturedBodies = new CopyOnWriteArrayList<>();
        int port = startTextInServer(capturedAppId, capturedSecretCode, capturedBodies);
        ProviderConnection connection = documentParseConnectionAdminService.saveConnection(
                new ProviderConnection(
                        null,
                        "textin-main",
                        ProviderConnection.PROVIDER_TEXTIN_XPARSE,
                        "http://127.0.0.1:" + port,
                        llmSecretCryptoService.encrypt("{\"appId\":\"textin-app-id\",\"secretCode\":\"textin-secret-code\"}"),
                        "已配置 JSON 凭证",
                        "{\"endpointPath\":\"/api/v1/xparse/parse/sync\",\"parseConfigJson\":\"{\\\"parse_mode\\\":\\\"scan\\\"}\"}",
                        true,
                        "tester",
                        "tester",
                        null,
                        null
                )
        );
        documentParseRoutePolicyAdminService.saveDefaultPolicy(
                new ParseRoutePolicy(
                        null,
                        ParseRoutePolicy.DEFAULT_SCOPE,
                        connection.getId(),
                        connection.getId(),
                        false,
                        null,
                        "{}",
                        "tester",
                        "tester",
                        null,
                        null
                )
        );

        Path imagesDir = Files.createDirectories(tempDir.resolve("images"));
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Path imagePath = imagesDir.resolve("receipt.png");
        Files.write(imagePath, new byte[]{1, 2, 3, 4, 5});
        Path scannedPdfPath = docsDir.resolve("scanned.pdf");
        writeBlankPdf(scannedPdfPath);

        List<RawSource> rawSources = sourceIngestSupport.ingest(tempDir);

        assertThat(rawSources).hasSize(2);
        RawSource imageSource = rawSources.stream()
                .filter(rawSource -> "png".equals(rawSource.getFormat()))
                .findFirst()
                .orElseThrow();
        RawSource pdfSource = rawSources.stream()
                .filter(rawSource -> "pdf".equals(rawSource.getFormat()))
                .findFirst()
                .orElseThrow();

        assertThat(imageSource.getParseMode()).isEqualTo("ocr_image");
        assertThat(imageSource.getParseProvider()).isEqualTo("textin_xparse");
        assertThat(imageSource.getContent()).contains("TextIn image extracted text");
        assertThat(imageSource.getMetadataJson()).contains("\"ocrApplied\":true");

        assertThat(pdfSource.getParseMode()).isEqualTo("ocr_scanned_pdf");
        assertThat(pdfSource.getParseProvider()).isEqualTo("textin_xparse");
        assertThat(pdfSource.getContent()).contains("TextIn scanned pdf text");
        assertThat(pdfSource.getMetadataJson()).contains("\"ocrApplied\":true");

        assertThat(capturedAppId.get()).isEqualTo("textin-app-id");
        assertThat(capturedSecretCode.get()).isEqualTo("textin-secret-code");
        assertThat(capturedBodies).hasSize(2);
        assertThat(capturedBodies).anyMatch(body -> body.contains("filename=\"receipt.png\""));
        assertThat(capturedBodies).anyMatch(body -> body.contains("filename=\"scanned.pdf\""));
    }

    /**
     * 验证编译入口会把 XLSX / CSV 解析出的通用表格结构落库。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldPersistStructuredTablesWhenPersistingParsedXlsxAndCsv(@TempDir Path tempDir) throws Exception {
        resetTables();
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_files CASCADE");
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Path excelPath = docsDir.resolve("codes.xlsx");
        writeSimpleWorkbook(excelPath);
        Path csvPath = docsDir.resolve("rules.csv");
        Files.writeString(csvPath, "businessSubTypeCode,meaning\n1210,refund", StandardCharsets.UTF_8);

        List<RawSource> rawSources = sourceIngestSupport.ingest(tempDir);
        Map<String, Long> sourceFileIdsByPath = sourceIngestSupport.persistSourceFiles(rawSources, null, null);

        SourceFileRecord excelSourceFile = new SourceFileRecord(
                sourceFileIdsByPath.get("docs/codes.xlsx"),
                null,
                "docs/codes.xlsx",
                "docs/codes.xlsx",
                null,
                "",
                "xlsx",
                0L,
                "",
                "{}",
                true,
                "docs/codes.xlsx"
        );
        List<StructuredTableRecord> excelTables = structuredTableJdbcRepository.findTablesBySourceFileId(
                excelSourceFile.getId()
        );
        assertThat(excelTables).hasSize(1);
        assertThat(excelTables.get(0).getSheetName()).isEqualTo("Codes");
        List<StructuredTableRowRecord> excelRows = structuredTableJdbcRepository.findRowsByTableId(
                excelTables.get(0).getId()
        );
        assertThat(excelRows).hasSize(1);
        assertThat(excelRows.get(0).getRowText()).contains("businessSubTypeCode=1210");
        List<StructuredTableCellRecord> excelCells = structuredTableJdbcRepository.findCellsByColumnValue(
                excelSourceFile.getId(),
                "meaning",
                "refund"
        );
        assertThat(excelCells).hasSize(1);
        assertThat(excelCells.get(0).getRowNumber()).isEqualTo(2);

        Long csvSourceFileId = sourceFileIdsByPath.get("docs/rules.csv");
        List<StructuredTableRecord> csvTables = structuredTableJdbcRepository.findTablesBySourceFileId(csvSourceFileId);
        assertThat(csvTables).hasSize(1);
        assertThat(csvTables.get(0).getFormat()).isEqualTo("csv");
    }

    /**
     * 验证固定入库准确性样本集会保留正文、source chunk 与结构化表格事实。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldPersistFixedIngestionAccuracySampleSet(@TempDir Path tempDir) throws Exception {
        resetTables();
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_files RESTART IDENTITY CASCADE");
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Path markdownPath = docsDir.resolve("guide.md");
        Path htmlPath = docsDir.resolve("page.html");
        Path csvPath = docsDir.resolve("rules.csv");
        Path excelPath = docsDir.resolve("codes.xlsx");
        Path pdfPath = docsDir.resolve("native.pdf");
        Path wordPath = docsDir.resolve("brief.docx");
        Files.writeString(markdownPath, "# Refund Guide\n\n- retry=3\n- owner=ops\n", StandardCharsets.UTF_8);
        Files.writeString(
                htmlPath,
                "<html><body><h1>Ops Console</h1><p>feature flag enabled</p></body></html>",
                StandardCharsets.UTF_8
        );
        Files.writeString(csvPath, "businessSubTypeCode,meaning\n1210,refund", StandardCharsets.UTF_8);
        writeSimpleWorkbook(excelPath);
        writeTextPdf(pdfPath, "Native pdf retry policy");
        writeSimpleWord(wordPath);

        List<RawSource> rawSources = sourceIngestSupport.ingest(tempDir);
        Map<String, Long> sourceFileIdsByPath = sourceIngestSupport.persistSourceFiles(rawSources, null, null);
        sourceIngestSupport.persistSourceFileChunks(rawSources, sourceFileIdsByPath);

        assertThat(rawSources)
                .extracting(RawSource::getRelativePath)
                .contains(
                        "docs/guide.md",
                        "docs/page.html",
                        "docs/rules.csv",
                        "docs/codes.xlsx",
                        "docs/native.pdf",
                        "docs/brief.docx"
                );
        assertThat(sourceFileIdsByPath)
                .containsKeys(
                        "docs/guide.md",
                        "docs/page.html",
                        "docs/rules.csv",
                        "docs/codes.xlsx",
                        "docs/native.pdf",
                        "docs/brief.docx"
                );

        SourceFileRecord markdownRecord = sourceFileJdbcRepository.findByPath("docs/guide.md").orElseThrow();
        SourceFileRecord htmlRecord = sourceFileJdbcRepository.findByPath("docs/page.html").orElseThrow();
        SourceFileRecord excelRecord = sourceFileJdbcRepository.findByPath("docs/codes.xlsx").orElseThrow();
        SourceFileRecord csvRecord = sourceFileJdbcRepository.findByPath("docs/rules.csv").orElseThrow();
        SourceFileRecord pdfRecord = sourceFileJdbcRepository.findByPath("docs/native.pdf").orElseThrow();
        SourceFileRecord wordRecord = sourceFileJdbcRepository.findByPath("docs/brief.docx").orElseThrow();

        assertThat(markdownRecord.getFormat()).isEqualTo("md");
        assertThat(markdownRecord.getContentText()).contains("# Refund Guide");
        assertThat(markdownRecord.getContentText()).contains("owner=ops");
        assertThat(htmlRecord.getFormat()).isEqualTo("html");
        assertThat(htmlRecord.getContentText()).contains("Ops Console");
        assertThat(htmlRecord.getContentText()).contains("feature flag enabled");
        assertThat(pdfRecord.getMetadataJson()).contains("\"parseMode\"");
        assertThat(pdfRecord.getMetadataJson()).contains("\"pdf_text\"");
        assertThat(pdfRecord.getMetadataJson()).contains("\"pageCount\"");
        assertThat(pdfRecord.getContentText()).contains("Native pdf retry policy");
        assertThat(wordRecord.getMetadataJson()).contains("\"parseMode\"");
        assertThat(wordRecord.getMetadataJson()).contains("\"office_extract\"");
        assertThat(wordRecord.getMetadataJson()).contains("\"paragraphCount\"");
        assertThat(wordRecord.getContentText()).contains("Payment timeout recovery");
        assertThat(excelRecord.getMetadataJson()).contains("\"structuredContentJson\"");
        assertThat(excelRecord.getContentText()).contains("sheet=Codes; row=2");
        assertThat(excelRecord.getContentText()).contains("businessSubTypeCode=1210");
        assertThat(csvRecord.getMetadataJson()).contains("\"structuredContentJson\"");
        assertThat(csvRecord.getContentText()).contains("businessSubTypeCode,meaning");

        List<SourceFileChunkRecord> chunkRecords = sourceFileChunkJdbcRepository.findByFilePaths(
                List.of("docs/guide.md", "docs/page.html", "docs/codes.xlsx", "docs/native.pdf", "docs/brief.docx")
        );
        String chunkText = chunkRecords.stream()
                .map(SourceFileChunkRecord::getChunkText)
                .reduce("", (left, right) -> left + "\n" + right);
        assertThat(chunkRecords).hasSizeGreaterThanOrEqualTo(5);
        assertThat(chunkText).contains("# Refund Guide");
        assertThat(chunkText).contains("Ops Console");
        assertThat(chunkText).contains("businessSubTypeCode=1210");
        assertThat(chunkText).contains("Native pdf retry policy");
        assertThat(chunkText).contains("Payment timeout recovery");

        List<StructuredTableRecord> excelTables = structuredTableJdbcRepository.findTablesBySourceFileId(
                sourceFileIdsByPath.get("docs/codes.xlsx")
        );
        List<StructuredTableRecord> csvTables = structuredTableJdbcRepository.findTablesBySourceFileId(
                sourceFileIdsByPath.get("docs/rules.csv")
        );
        assertThat(excelTables).hasSize(1);
        assertThat(excelTables.get(0).getSheetName()).isEqualTo("Codes");
        assertThat(csvTables).hasSize(1);
        assertThat(csvTables.get(0).getFormat()).isEqualTo("csv");

        List<StructuredTableCellRecord> excelCells = structuredTableJdbcRepository.findCellsByColumnValue(
                sourceFileIdsByPath.get("docs/codes.xlsx"),
                "meaning",
                "refund"
        );
        List<StructuredTableCellRecord> csvCells = structuredTableJdbcRepository.findCellsByColumnValue(
                sourceFileIdsByPath.get("docs/rules.csv"),
                "businessSubTypeCode",
                "1210"
        );
        assertThat(excelCells).hasSize(1);
        assertThat(excelCells.get(0).getRowNumber()).isEqualTo(2);
        assertThat(csvCells).hasSize(1);
        assertThat(csvCells.get(0).getCellValue()).isEqualTo("1210");
    }

    /**
     * 验证纯文本类文件与旧版 Excel `.xls` 仍能走本地抽取链。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldParsePlainTextMarkdownJsonYamlTxtAndLegacyExcelLocally(@TempDir Path tempDir) throws Exception {
        resetTables();
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Path markdownPath = docsDir.resolve("guide.md");
        Path textPath = docsDir.resolve("notes.txt");
        Path jsonPath = docsDir.resolve("rules.json");
        Path yamlPath = docsDir.resolve("rules.yaml");
        Path legacyExcelPath = docsDir.resolve("codes.xls");

        Files.writeString(markdownPath, "# Payment Guide\n\n- retry=3\n", StandardCharsets.UTF_8);
        Files.writeString(textPath, "plain text fallback", StandardCharsets.UTF_8);
        Files.writeString(jsonPath, "{\"retry\":3,\"scene\":\"refund\"}", StandardCharsets.UTF_8);
        Files.writeString(yamlPath, "retry: 3\nscene: refund\n", StandardCharsets.UTF_8);
        writeLegacyWorkbook(legacyExcelPath);

        RawSource markdownSource = documentParseRouter.parseRawSource(tempDir, markdownPath);
        RawSource textSource = documentParseRouter.parseRawSource(tempDir, textPath);
        RawSource jsonSource = documentParseRouter.parseRawSource(tempDir, jsonPath);
        RawSource yamlSource = documentParseRouter.parseRawSource(tempDir, yamlPath);
        RawSource legacyExcelSource = documentParseRouter.parseRawSource(tempDir, legacyExcelPath);

        assertThat(markdownSource.getParseMode()).isEqualTo("text_read");
        assertThat(markdownSource.getParseProvider()).isEqualTo("filesystem");
        assertThat(markdownSource.getContent()).contains("# Payment Guide");

        assertThat(textSource.getParseMode()).isEqualTo("text_read");
        assertThat(textSource.getParseProvider()).isEqualTo("filesystem");
        assertThat(textSource.getContent()).contains("plain text fallback");

        assertThat(jsonSource.getParseMode()).isEqualTo("text_read");
        assertThat(jsonSource.getParseProvider()).isEqualTo("filesystem");
        assertThat(jsonSource.getContent()).contains("\"retry\":3");

        assertThat(yamlSource.getParseMode()).isEqualTo("text_read");
        assertThat(yamlSource.getParseProvider()).isEqualTo("filesystem");
        assertThat(yamlSource.getContent()).contains("scene: refund");

        assertThat(legacyExcelSource.getParseMode()).isEqualTo("office_extract");
        assertThat(legacyExcelSource.getParseProvider()).isEqualTo("poi_excel");
        assertThat(legacyExcelSource.getContent()).contains("=== Sheet: LegacyCodes ===");
        assertThat(legacyExcelSource.getContent()).contains("1210,refund");
        assertThat(legacyExcelSource.getMetadataJson()).contains("\"sheetCount\"");
    }

    private int startOcrServer(
            AtomicReference<String> capturedSecretId,
            AtomicReference<String> capturedSecretKey
    ) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/ocr/v1/general-basic", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                capturedSecretId.set(exchange.getRequestHeaders().getFirst("x-secret-id"));
                capturedSecretKey.set(exchange.getRequestHeaders().getFirst("x-secret-key"));
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

    private int startTextInServer(
            AtomicReference<String> capturedAppId,
            AtomicReference<String> capturedSecretCode,
            List<String> capturedBodies
    ) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/v1/xparse/parse/sync", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                capturedAppId.set(exchange.getRequestHeaders().getFirst("x-ti-app-id"));
                capturedSecretCode.set(exchange.getRequestHeaders().getFirst("x-ti-secret-code"));
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
                capturedBodies.add(body);
                String responseBody = body.contains("filename=\"scanned.pdf\"")
                        ? "{\"code\":200,\"data\":{\"result\":{\"elements\":[{\"text\":\"TextIn scanned pdf text\"}]}}}"
                        : "{\"code\":200,\"data\":{\"markdown\":\"# OCR image\\nTextIn image extracted text\"}}";
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

    /**
     * 写入带正文的 PDF。
     *
     * @param pdfPath PDF 路径
     * @param text PDF 文本
     * @throws IOException IO 异常
     */
    private void writeTextPdf(Path pdfPath, String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(pdfPath.toFile());
        }
    }

    /**
     * 写入简单 Word 测试文件。
     *
     * @param wordPath Word 路径
     * @throws IOException IO 异常
     */
    private void writeSimpleWord(Path wordPath) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Payment timeout recovery");
            document.createParagraph().createRun().setText("retry=3");
            try (OutputStream outputStream = Files.newOutputStream(wordPath)) {
                document.write(outputStream);
            }
        }
    }

    /**
     * 写入简单 Excel 测试文件。
     *
     * @param excelPath Excel 路径
     * @throws IOException IO 异常
     */
    private void writeSimpleWorkbook(Path excelPath) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet codesSheet = workbook.createSheet("Codes");
            Row codeHeader = codesSheet.createRow(0);
            codeHeader.createCell(0).setCellValue("businessSubTypeCode");
            codeHeader.createCell(1).setCellValue("meaning");
            Row codeRow = codesSheet.createRow(1);
            codeRow.createCell(0).setCellValue("1210");
            codeRow.createCell(1).setCellValue("refund");
            try (OutputStream outputStream = Files.newOutputStream(excelPath)) {
                workbook.write(outputStream);
            }
        }
    }

    /**
     * 写入旧版 Excel 97-2003 测试文件。
     *
     * @param excelPath Excel 路径
     * @throws IOException IO 异常
     */
    private void writeLegacyWorkbook(Path excelPath) throws IOException {
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            Sheet codesSheet = workbook.createSheet("LegacyCodes");
            Row codeHeader = codesSheet.createRow(0);
            codeHeader.createCell(0).setCellValue("businessSubTypeCode");
            codeHeader.createCell(1).setCellValue("meaning");
            Row codeRow = codesSheet.createRow(1);
            codeRow.createCell(0).setCellValue("1210");
            codeRow.createCell(1).setCellValue("refund");
            try (OutputStream outputStream = Files.newOutputStream(excelPath)) {
                workbook.write(outputStream);
            }
        }
    }

    /**
     * 写入简单 PPT 测试文件。
     *
     * @param pptPath PPT 路径
     * @throws IOException IO 异常
     */
    private void writeSimplePresentation(Path pptPath) throws IOException {
        try (XMLSlideShow slideShow = new XMLSlideShow()) {
            XSLFSlide slide = slideShow.createSlide();
            XSLFTextBox textBox = slide.createTextBox();
            textBox.setText("Timeout review");
            textBox.addNewTextParagraph().addNewTextRun().setText("Escalation path");
            try (OutputStream outputStream = Files.newOutputStream(pptPath)) {
                slideShow.write(outputStream);
            }
        }
    }

    private void resetTables() {
        jdbcTemplate.execute("delete from document_parse_route_policies");
        jdbcTemplate.execute("delete from document_parse_connections");
    }
}
