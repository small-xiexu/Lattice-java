package com.xbk.lattice.documentparse.extractor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDF 坐标文本表格格式化器测试
 *
 * 职责：验证 PDF 多列表格可按坐标对齐关系重建
 *
 * @author xiexu
 */
class PdfPositionedTextTableFormatterTests {

    /**
     * 验证多行多列坐标文本会被格式化为通用 table_row。
     */
    @Test
    void shouldFormatAlignedPositionedTextsAsTableRows() {
        PdfPositionedTextTableFormatter formatter = new PdfPositionedTextTableFormatter();

        String tableText = formatter.formatTables(
                7,
                List.of(
                        text("Risk", 50.0F, 100.0F, 22.0F),
                        text("Impact", 170.0F, 100.0F, 35.0F),
                        text("Mitigation", 310.0F, 100.0F, 55.0F),
                        text("Capacity", 50.0F, 120.0F, 45.0F),
                        text("Latency", 170.0F, 120.0F, 42.0F),
                        text("Throttle writes", 310.0F, 120.0F, 82.0F),
                        text("Rollback", 50.0F, 140.0F, 45.0F),
                        text("Mismatch", 170.0F, 140.0F, 50.0F),
                        text("Reconcile", 310.0F, 140.0F, 58.0F)
                )
        );

        assertThat(tableText).contains("=== Table: page 7 block 1 ===");
        assertThat(tableText).contains("table_row: Risk | Impact | Mitigation");
        assertThat(tableText).contains("table_row: Capacity | Latency | Throttle writes");
        assertThat(tableText).contains("table_row: Rollback | Mismatch | Reconcile");
    }

    /**
     * 验证没有首列内容的视觉续行会合并到上一条表格行。
     */
    @Test
    void shouldMergeContinuationRowsWithoutLeadingCell() {
        PdfPositionedTextTableFormatter formatter = new PdfPositionedTextTableFormatter();

        String tableText = formatter.formatTables(
                2,
                List.of(
                        text("Risk", 50.0F, 100.0F, 22.0F),
                        text("Impact", 170.0F, 100.0F, 35.0F),
                        text("Mitigation", 310.0F, 100.0F, 55.0F),
                        text("Capacity", 50.0F, 120.0F, 45.0F),
                        text("May delay writes", 170.0F, 120.0F, 85.0F),
                        text("Throttle writes", 310.0F, 120.0F, 82.0F),
                        text("during peak", 170.0F, 138.0F, 66.0F),
                        text("and compare", 310.0F, 138.0F, 70.0F)
                )
        );

        assertThat(tableText).contains("table_row: Capacity | May delay writes during peak | Throttle writes and compare");
        assertThat(tableText).doesNotContain("table_row: during peak");
    }

    /**
     * 验证普通单列段落不会被误判为表格。
     */
    @Test
    void shouldIgnoreSingleColumnParagraphs() {
        PdfPositionedTextTableFormatter formatter = new PdfPositionedTextTableFormatter();

        String tableText = formatter.formatTables(
                1,
                List.of(
                        text("This is a normal paragraph line.", 50.0F, 100.0F, 180.0F),
                        text("Another paragraph line follows.", 50.0F, 120.0F, 170.0F)
                )
        );

        assertThat(tableText).isBlank();
    }

    /**
     * 创建坐标文本片段。
     *
     * @param text 文本
     * @param x x 坐标
     * @param y y 坐标
     * @param width 宽度
     * @return 坐标文本片段
     */
    private PdfPositionedTextTableFormatter.PositionedText text(String text, float x, float y, float width) {
        return new PdfPositionedTextTableFormatter.PositionedText(text, x, y, width);
    }
}
