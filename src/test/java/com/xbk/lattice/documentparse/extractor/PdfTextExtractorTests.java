package com.xbk.lattice.documentparse.extractor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDF 文本抽取器测试
 *
 * 职责：验证 PDF 正文抽取会补充坐标表格文本
 *
 * @author xiexu
 */
class PdfTextExtractorTests {

    @TempDir
    private Path tempDir;

    /**
     * 验证 PDF 多列文本会被补充为 table_row，便于问答召回完整表格证据。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldAppendPositionAwareTableRows() throws IOException {
        Path pdfPath = tempDir.resolve("table.pdf");
        writeTablePdf(pdfPath);

        SourceExtractionResult result = new PdfTextExtractor().extract(pdfPath);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).contains("=== Page: 1 ===");
        assertThat(result.getContent()).contains("=== Table: page 1 block 1 ===");
        assertThat(result.getContent()).contains("table_row: Risk | Impact | Mitigation");
        assertThat(result.getContent()).contains("table_row: Capacity | Latency | Throttle writes");
    }

    /**
     * 验证 PDF 抽取内容可按 gold Markdown 口径复核关键正文、页码和表格证据。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldMatchGoldMarkdownForTextAndTableExtraction() throws IOException {
        Path pdfPath = tempDir.resolve("gold.pdf");
        writeGoldPdf(pdfPath);
        String goldMarkdown = """
                === Page: 1 ===
                Payment Retry Guide
                Retry window: 15 minutes
                Owner: platform-team

                === Page: 2 ===
                Escalation Matrix
                table_row: Level | Response | Owner
                table_row: P1 | 5 minutes | oncall
                table_row: P2 | 30 minutes | support
                """;

        SourceExtractionResult result = new PdfTextExtractor().extract(pdfPath);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).contains("=== Page: 1 ===");
        assertThat(result.getContent()).contains("=== Page: 2 ===");
        assertThat(result.getContent()).contains("Payment Retry Guide");
        assertThat(result.getContent()).contains("Retry window: 15 minutes");
        assertThat(result.getContent()).contains("Owner: platform-team");
        assertThat(result.getContent()).contains("table_row: Level | Response | Owner");
        assertThat(result.getContent()).contains("table_row: P1 | 5 minutes | oncall");
        assertThat(result.getContent()).contains("table_row: P2 | 30 minutes | support");
        assertThat(result.getMetadataJson()).contains("\"pageCount\":2");
        assertThat(goldMarkdown).contains("Payment Retry Guide");
    }

    /**
     * 写入测试 PDF。
     *
     * @param pdfPath PDF 路径
     * @throws IOException IO 异常
     */
    private void writeTablePdf(Path pdfPath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                writeRow(contentStream, font, 720.0F, "Risk", "Impact", "Mitigation");
                writeRow(contentStream, font, 700.0F, "Capacity", "Latency", "Throttle writes");
                writeRow(contentStream, font, 680.0F, "Rollback", "Mismatch", "Reconcile");
            }
            document.save(pdfPath.toFile());
        }
    }

    /**
     * 写入带正文和表格的 gold PDF。
     *
     * @param pdfPath PDF 路径
     * @throws IOException IO 异常
     */
    private void writeGoldPdf(Path pdfPath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage firstPage = new PDPage();
            document.addPage(firstPage);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, firstPage)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                writeText(contentStream, font, 50.0F, 720.0F, "Payment Retry Guide");
                writeText(contentStream, font, 50.0F, 700.0F, "Retry window: 15 minutes");
                writeText(contentStream, font, 50.0F, 680.0F, "Owner: platform-team");
            }
            PDPage secondPage = new PDPage();
            document.addPage(secondPage);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, secondPage)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                writeText(contentStream, font, 50.0F, 740.0F, "Escalation Matrix");
                writeRow(contentStream, font, 710.0F, "Level", "Response", "Owner");
                writeRow(contentStream, font, 690.0F, "P1", "5 minutes", "oncall");
                writeRow(contentStream, font, 670.0F, "P2", "30 minutes", "support");
            }
            document.save(pdfPath.toFile());
        }
    }

    /**
     * 写入一行三列文本。
     *
     * @param contentStream PDF 内容流
     * @param font 字体
     * @param y y 坐标
     * @param first 第一列
     * @param second 第二列
     * @param third 第三列
     * @throws IOException IO 异常
     */
    private void writeRow(
            PDPageContentStream contentStream,
            PDType1Font font,
            float y,
            String first,
            String second,
            String third
    ) throws IOException {
        writeText(contentStream, font, 50.0F, y, first);
        writeText(contentStream, font, 180.0F, y, second);
        writeText(contentStream, font, 320.0F, y, third);
    }

    /**
     * 写入单段文本。
     *
     * @param contentStream PDF 内容流
     * @param font 字体
     * @param x x 坐标
     * @param y y 坐标
     * @param text 文本
     * @throws IOException IO 异常
     */
    private void writeText(
            PDPageContentStream contentStream,
            PDType1Font font,
            float x,
            float y,
            String text
    ) throws IOException {
        contentStream.beginText();
        contentStream.setFont(font, 12);
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(text);
        contentStream.endText();
    }
}
