package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.node.IngestNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IngestNode 测试
 *
 * 职责：验证文件采集、截断与跳过规则
 *
 * @author xiexu
 */
class IngestNodeTests {

    /**
     * 验证文本文件会被采集，且内容会按配置截断。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldReadSupportedTextFilesAndTrimContent(@TempDir Path tempDir) throws IOException {
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(docsDir.resolve("intro.md"), "abcdefghij", StandardCharsets.UTF_8);

        CompilerProperties properties = new CompilerProperties();
        properties.setIngestMaxChars(5);

        IngestNode ingestNode = new IngestNode(properties);
        List<RawSource> rawSources = ingestNode.ingest(tempDir);

        assertThat(rawSources).hasSize(1);
        assertThat(rawSources.get(0).getRelativePath()).isEqualTo("docs/intro.md");
        assertThat(rawSources.get(0).getContent()).isEqualTo("abcde");
        assertThat(rawSources.get(0).getFormat()).isEqualTo("md");
    }

    /**
     * 验证命中跳过规则的目录和文件不会被采集。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldSkipIgnoredDirectoriesAndUnsupportedFiles(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve(".git"));
        Files.createDirectories(tempDir.resolve("target"));
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve(".git").resolve("ignored.md"), "ignored", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("target").resolve("ignored.java"), "ignored", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("src").resolve("App.java"), "class App {}", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("archive.jar"), "jar-binary", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve(".DS_Store"), "desktop-service-store", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("src").resolve("._App.java"), "apple-double", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("Thumbs.db"), "thumb-cache", StandardCharsets.UTF_8);

        CompilerProperties properties = new CompilerProperties();
        IngestNode ingestNode = new IngestNode(properties);

        List<RawSource> rawSources = ingestNode.ingest(tempDir);

        assertThat(rawSources).hasSize(1);
        assertThat(rawSources.get(0).getRelativePath()).isEqualTo("src/App.java");
    }

    /**
     * 验证新增文本格式和图片占位文件会被采集。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldReadExtendedTextFormatsAndCreateImagePlaceholder(@TempDir Path tempDir) throws IOException {
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Path assetsDir = Files.createDirectories(tempDir.resolve("assets"));
        Files.writeString(docsDir.resolve("notes.txt"), "plain-text", StandardCharsets.UTF_8);
        Files.writeString(docsDir.resolve("application.properties"), "timeout=30", StandardCharsets.UTF_8);
        Files.writeString(docsDir.resolve("rules.csv"), "code,meaning\n1210,refund", StandardCharsets.UTF_8);
        Files.writeString(docsDir.resolve("style.css"), ".box { color: red; }", StandardCharsets.UTF_8);
        Files.writeString(docsDir.resolve("page.html"), "<html><body>hello</body></html>", StandardCharsets.UTF_8);
        Files.writeString(docsDir.resolve("run.sh"), "echo hello", StandardCharsets.UTF_8);
        Files.writeString(docsDir.resolve("worker.py"), "print('hello')", StandardCharsets.UTF_8);
        Files.write(assetsDir.resolve("diagram.png"), new byte[]{1, 2, 3, 4});

        CompilerProperties properties = new CompilerProperties();
        properties.setIngestMaxChars(100);

        IngestNode ingestNode = new IngestNode(properties);
        List<RawSource> rawSources = ingestNode.ingest(tempDir);

        assertThat(rawSources).hasSize(8);
        assertThat(rawSources)
                .extracting(RawSource::getRelativePath)
                .containsExactly(
                        "assets/diagram.png",
                        "docs/application.properties",
                        "docs/notes.txt",
                        "docs/page.html",
                        "docs/rules.csv",
                        "docs/run.sh",
                        "docs/style.css",
                        "docs/worker.py"
                );
        RawSource imageSource = rawSources.get(0);
        assertThat(imageSource.getFormat()).isEqualTo("png");
        assertThat(imageSource.getContent()).isEqualTo("[Image file: assets/diagram.png]");
        RawSource csvSource = rawSources.stream()
                .filter(rawSource -> "csv".equals(rawSource.getFormat()))
                .findFirst()
                .orElseThrow();
        assertThat(csvSource.getContent()).contains("code,meaning");
        assertThat(csvSource.getContent()).contains("1210,refund");
    }

    /**
     * 验证 PDF 与 Excel 文件会被抽取为规范化文本。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldExtractPdfAndExcelIntoNormalizedText(@TempDir Path tempDir) throws IOException {
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Path pdfPath = docsDir.resolve("timeout.pdf");
        Path excelPath = docsDir.resolve("codes.xlsx");
        Path wordPath = docsDir.resolve("brief.docx");
        Path legacyWordPath = docsDir.resolve("legacy-brief.doc");
        Path pptPath = docsDir.resolve("briefing.pptx");
        writeSimplePdf(pdfPath, "Payment timeout retry = 3");
        writeSimpleWorkbook(excelPath);
        writeSimpleWord(wordPath);
        writeLegacyWord(legacyWordPath);
        writeSimplePresentation(pptPath);

        CompilerProperties properties = new CompilerProperties();
        properties.setIngestMaxChars(4000);

        IngestNode ingestNode = new IngestNode(properties);
        List<RawSource> rawSources = ingestNode.ingest(tempDir);

        assertThat(rawSources).hasSize(5);
        RawSource pdfSource = rawSources.stream()
                .filter(rawSource -> "pdf".equals(rawSource.getFormat()))
                .findFirst()
                .orElseThrow();
        RawSource excelSource = rawSources.stream()
                .filter(rawSource -> "xlsx".equals(rawSource.getFormat()))
                .findFirst()
                .orElseThrow();
        RawSource wordSource = rawSources.stream()
                .filter(rawSource -> "docx".equals(rawSource.getFormat()))
                .findFirst()
                .orElseThrow();
        RawSource legacyWordSource = rawSources.stream()
                .filter(rawSource -> "doc".equals(rawSource.getFormat()))
                .findFirst()
                .orElseThrow();
        RawSource pptSource = rawSources.stream()
                .filter(rawSource -> "pptx".equals(rawSource.getFormat()))
                .findFirst()
                .orElseThrow();
        assertThat(pdfSource.getRelativePath()).isEqualTo("docs/timeout.pdf");
        assertThat(pdfSource.getFormat()).isEqualTo("pdf");
        assertThat(pdfSource.getMetadataJson()).contains("pageCount");
        assertThat(pdfSource.getContent()).contains("Payment timeout retry = 3");
        assertThat(excelSource.getRelativePath()).isEqualTo("docs/codes.xlsx");
        assertThat(excelSource.getFormat()).isEqualTo("xlsx");
        assertThat(excelSource.getMetadataJson()).contains("sheetCount");
        assertThat(excelSource.getContent()).contains("=== Sheet: Codes ===");
        assertThat(excelSource.getContent()).contains("businessSubTypeCode,meaning");
        assertThat(excelSource.getContent()).contains("1210,refund");
        assertThat(excelSource.getContent()).contains("=== Sheet: Settings ===");
        assertThat(wordSource.getRelativePath()).isEqualTo("docs/brief.docx");
        assertThat(wordSource.getMetadataJson()).contains("paragraphCount");
        assertThat(wordSource.getContent()).contains("Payment timeout recovery");
        assertThat(wordSource.getContent()).contains("retry=3");
        assertThat(legacyWordSource.getRelativePath()).isEqualTo("docs/legacy-brief.doc");
        assertThat(legacyWordSource.getMetadataJson()).contains("legacyWord");
        assertThat(legacyWordSource.getMetadataJson()).contains("extractionStrategy");
        assertThat(legacyWordSource.getMetadataJson()).contains("listFormattingPreserved");
        assertThat(legacyWordSource.getContent()).contains("Legacy DOC payment timeout");
        assertThat(legacyWordSource.getContent()).contains("retry=3");
        assertThat(pptSource.getRelativePath()).isEqualTo("docs/briefing.pptx");
        assertThat(pptSource.getMetadataJson()).contains("slideCount");
        assertThat(pptSource.getContent()).contains("=== Slide: 1 ===");
        assertThat(pptSource.getContent()).contains("Timeout review");
    }

    /**
     * 写入简单 PDF 测试文件。
     *
     * @param pdfPath PDF 路径
     * @param text 文本内容
     * @throws IOException IO 异常
     */
    private void writeSimplePdf(Path pdfPath, String text) throws IOException {
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

            Sheet settingsSheet = workbook.createSheet("Settings");
            Row settingsHeader = settingsSheet.createRow(0);
            settingsHeader.createCell(0).setCellValue("key");
            settingsHeader.createCell(1).setCellValue("value");
            Row settingsRow = settingsSheet.createRow(1);
            settingsRow.createCell(0).setCellValue("retry");
            settingsRow.createCell(1).setCellValue("3");

            try (OutputStream outputStream = Files.newOutputStream(excelPath)) {
                workbook.write(outputStream);
            }
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
     * 写入旧版 Word 测试文件。
     *
     * @param legacyWordPath Word 路径
     * @throws IOException IO 异常
     */
    private void writeLegacyWord(Path legacyWordPath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream("/documentparse/legacy-word.doc")) {
            assertThat(inputStream).isNotNull();
            Files.copy(inputStream, legacyWordPath);
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
}
