package com.xbk.lattice.documentparse.extractor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 坐标表格抽取器
 *
 * 职责：通过 PDFBox 文本坐标提取多列表格补充文本
 *
 * @author xiexu
 */
final class PdfPositionedTextTableExtractor {

    /**
     * 抽取每页的表格补充文本。
     *
     * @param document PDF 文档
     * @return 按页排列的表格补充文本
     * @throws IOException IO 异常
     */
    List<String> extractPageTables(PDDocument document) throws IOException {
        if (document == null || document.getNumberOfPages() <= 0) {
            return List.of();
        }
        PdfTableStripper tableStripper = new PdfTableStripper(new PdfPositionedTextTableFormatter());
        tableStripper.setSortByPosition(true);
        tableStripper.getText(document);
        int pageCount = document.getNumberOfPages();
        return normalizePageTableTexts(tableStripper.getPageTableTexts(), pageCount);
    }

    /**
     * 把抽取结果补齐到 PDF 页数。
     *
     * @param pageTableTexts 已抽取的表格文本
     * @param pageCount 页数
     * @return 补齐后的表格文本
     */
    private List<String> normalizePageTableTexts(List<String> pageTableTexts, int pageCount) {
        List<String> normalizedTexts = new ArrayList<String>();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            if (pageIndex < pageTableTexts.size()) {
                normalizedTexts.add(pageTableTexts.get(pageIndex));
            }
            else {
                normalizedTexts.add("");
            }
        }
        return normalizedTexts;
    }

    /**
     * PDFBox 表格文本收集器。
     *
     * 职责：把 PDFBox 的 TextPosition 归一化成坐标文本片段
     *
     * @author xiexu
     */
    private static final class PdfTableStripper extends PDFTextStripper {

        private static final float CHARACTER_GAP_THRESHOLD = 8.0F;

        private static final float LINE_SHIFT_THRESHOLD = 3.0F;

        private final PdfPositionedTextTableFormatter tableFormatter;

        private final List<String> pageTableTexts = new ArrayList<String>();

        private final List<PdfPositionedTextTableFormatter.PositionedText> currentPageTexts =
                new ArrayList<PdfPositionedTextTableFormatter.PositionedText>();

        private int currentPageNumber;

        /**
         * 创建 PDFBox 表格文本收集器。
         *
         * @param tableFormatter 表格格式化器
         * @throws IOException IO 异常
         */
        private PdfTableStripper(PdfPositionedTextTableFormatter tableFormatter) throws IOException {
            this.tableFormatter = tableFormatter;
        }

        /**
         * 开始处理单页。
         *
         * @param page PDF 页
         * @throws IOException IO 异常
         */
        @Override
        protected void startPage(PDPage page) throws IOException {
            currentPageNumber++;
            currentPageTexts.clear();
            super.startPage(page);
        }

        /**
         * 收集当前文本行的坐标片段。
         *
         * @param text 文本
         * @param textPositions 文本坐标
         * @throws IOException IO 异常
         */
        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            appendTextPositions(textPositions);
        }

        /**
         * 结束处理单页并生成表格补充文本。
         *
         * @param page PDF 页
         * @throws IOException IO 异常
         */
        @Override
        protected void endPage(PDPage page) throws IOException {
            String tableText = tableFormatter.formatTables(currentPageNumber, currentPageTexts);
            pageTableTexts.add(tableText);
            currentPageTexts.clear();
            super.endPage(page);
        }

        /**
         * 返回按页收集的表格补充文本。
         *
         * @return 表格补充文本
         */
        private List<String> getPageTableTexts() {
            return pageTableTexts;
        }

        /**
         * 把 PDFBox 文本坐标归并为可用于表格识别的文本片段。
         *
         * @param textPositions 文本坐标
         */
        private void appendTextPositions(List<TextPosition> textPositions) {
            if (textPositions == null || textPositions.isEmpty()) {
                return;
            }
            PositionedTextBuilder positionedTextBuilder = new PositionedTextBuilder(currentPageTexts);
            TextPosition previousTextPosition = null;
            for (TextPosition textPosition : textPositions) {
                String unicode = textPosition.getUnicode();
                if (unicode == null || unicode.isBlank()) {
                    positionedTextBuilder.flush();
                    previousTextPosition = null;
                    continue;
                }
                if (previousTextPosition != null && shouldStartNewToken(previousTextPosition, textPosition)) {
                    positionedTextBuilder.flush();
                }
                positionedTextBuilder.append(textPosition);
                previousTextPosition = textPosition;
            }
            positionedTextBuilder.flush();
        }

        /**
         * 判断当前字符是否应开启新的文本片段。
         *
         * @param previousTextPosition 前一个文本坐标
         * @param currentTextPosition 当前文本坐标
         * @return 应开启新片段返回 true
         */
        private boolean shouldStartNewToken(TextPosition previousTextPosition, TextPosition currentTextPosition) {
            float lineShift = Math.abs(currentTextPosition.getYDirAdj() - previousTextPosition.getYDirAdj());
            if (lineShift > LINE_SHIFT_THRESHOLD) {
                return true;
            }
            float previousRight = previousTextPosition.getXDirAdj() + previousTextPosition.getWidthDirAdj();
            float currentX = currentTextPosition.getXDirAdj();
            float gap = currentX - previousRight;
            float dynamicThreshold = Math.max(
                    CHARACTER_GAP_THRESHOLD,
                    previousTextPosition.getWidthDirAdj() * 1.8F
            );
            return gap > dynamicThreshold;
        }
    }

    /**
     * 坐标文本构建器。
     *
     * 职责：把连续 TextPosition 合并为一个坐标文本片段
     *
     * @author xiexu
     */
    private static final class PositionedTextBuilder {

        private final List<PdfPositionedTextTableFormatter.PositionedText> positionedTexts;

        private final StringBuilder textBuilder = new StringBuilder();

        private float x;

        private float y;

        private float right;

        /**
         * 创建坐标文本构建器。
         *
         * @param positionedTexts 目标坐标文本列表
         */
        private PositionedTextBuilder(List<PdfPositionedTextTableFormatter.PositionedText> positionedTexts) {
            this.positionedTexts = positionedTexts;
        }

        /**
         * 追加 PDFBox 文本坐标。
         *
         * @param textPosition PDFBox 文本坐标
         */
        private void append(TextPosition textPosition) {
            if (!hasText()) {
                x = textPosition.getXDirAdj();
                y = textPosition.getYDirAdj();
                right = textPosition.getXDirAdj() + textPosition.getWidthDirAdj();
            }
            else {
                right = Math.max(right, textPosition.getXDirAdj() + textPosition.getWidthDirAdj());
            }
            textBuilder.append(textPosition.getUnicode());
        }

        /**
         * 刷新当前坐标文本。
         */
        private void flush() {
            if (hasText()) {
                String text = textBuilder.toString().replaceAll("\\s+", " ").trim();
                if (!text.isBlank()) {
                    float width = Math.max(0.0F, right - x);
                    positionedTexts.add(new PdfPositionedTextTableFormatter.PositionedText(text, x, y, width));
                }
            }
            textBuilder.setLength(0);
            x = 0.0F;
            y = 0.0F;
            right = 0.0F;
        }

        /**
         * 返回是否已有文本。
         *
         * @return 有文本返回 true
         */
        private boolean hasText() {
            return textBuilder.length() > 0;
        }
    }
}
