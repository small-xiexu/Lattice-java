package com.xbk.lattice.documentparse.extractor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocTextExtractor 测试
 *
 * 职责：验证二进制 DOC 的段落优先抽取与 text pieces 降级逻辑
 *
 * @author xiexu
 */
class DocTextExtractorTests {

    /**
     * 验证二进制 DOC 会优先使用段落级正文抽取。
     */
    @Test
    void shouldPreferParagraphExtractionWhenParagraphTextIsAvailable() {
        DocTextExtractor extractor = new DocTextExtractor();
        BinaryDocTextSource textSource = new StubBinaryDocTextSource(
                new String[]{"Binary DOC payment timeout\r", "\r", "retry=3\r"},
                "fallback should not be used"
        );

        SourceExtractionResult extractionResult = extractor.extract(textSource, "binary.doc");

        assertThat(extractionResult).isNotNull();
        assertThat(extractionResult.getContent()).isEqualTo("Binary DOC payment timeout\n\nretry=3");
        assertThat(extractionResult.getMetadataJson()).contains("\"binaryWord\":true");
        assertThat(extractionResult.getMetadataJson()).contains("\"paragraphCount\":2");
        assertThat(extractionResult.getMetadataJson()).contains("\"extractionStrategy\":\"paragraph_text\"");
        assertThat(extractionResult.getMetadataJson()).contains("\"listFormattingPreserved\":false");
        assertThat(extractionResult.isVerbatim()).isTrue();
    }

    /**
     * 验证段落级抽取失败时会降级到 text pieces。
     */
    @Test
    void shouldFallbackToTextPiecesWhenParagraphExtractionFails() {
        DocTextExtractor extractor = new DocTextExtractor();
        BinaryDocTextSource textSource = new FallbackBinaryDocTextSource(
                new IllegalStateException("broken paragraphs"),
                "Binary DOC payment timeout\r\rretry=3"
        );

        SourceExtractionResult extractionResult = extractor.extract(textSource, "binary.doc");

        assertThat(extractionResult).isNotNull();
        assertThat(extractionResult.getContent()).isEqualTo("Binary DOC payment timeout\n\nretry=3");
        assertThat(extractionResult.getMetadataJson()).contains("\"paragraphCount\":2");
        assertThat(extractionResult.getMetadataJson()).contains("\"extractionStrategy\":\"text_pieces_fallback\"");
        assertThat(extractionResult.getMetadataJson()).contains("\"listFormattingPreserved\":false");
    }

    /**
     * 验证无可用正文时返回 null。
     */
    @Test
    void shouldReturnNullWhenParagraphAndTextPiecesAreBothBlank() {
        DocTextExtractor extractor = new DocTextExtractor();
        BinaryDocTextSource textSource = new StubBinaryDocTextSource(
                new String[]{"\r", "   "},
                "\r\r"
        );

        SourceExtractionResult extractionResult = extractor.extract(textSource, "binary.doc");

        assertThat(extractionResult).isNull();
    }

    /**
     * 固定返回段落与 text pieces 的二进制 DOC 文本源。
     *
     * 职责：为 `DocTextExtractorTests` 提供稳定测试输入
     *
     * @author xiexu
     */
    private static final class StubBinaryDocTextSource implements BinaryDocTextSource {

        private final String[] paragraphText;

        private final String textFromPieces;

        /**
         * 创建测试文本源。
         *
         * @param paragraphText 段落文本
         * @param textFromPieces text pieces 文本
         */
        private StubBinaryDocTextSource(String[] paragraphText, String textFromPieces) {
            this.paragraphText = paragraphText;
            this.textFromPieces = textFromPieces;
        }

        /**
         * 获取段落文本数组。
         *
         * @return 段落文本数组
         */
        @Override
        public String[] getParagraphText() {
            return paragraphText;
        }

        /**
         * 获取 text pieces 文本。
         *
         * @return text pieces 文本
         */
        @Override
        public String getTextFromPieces() {
            return textFromPieces;
        }
    }

    /**
     * 在段落读取阶段抛出异常的二进制 DOC 文本源。
     *
     * 职责：验证 `DocTextExtractor` 的降级路径
     *
     * @author xiexu
     */
    private static final class FallbackBinaryDocTextSource implements BinaryDocTextSource {

        private final RuntimeException paragraphException;

        private final String textFromPieces;

        /**
         * 创建降级测试文本源。
         *
         * @param paragraphException 段落读取异常
         * @param textFromPieces text pieces 文本
         */
        private FallbackBinaryDocTextSource(RuntimeException paragraphException, String textFromPieces) {
            this.paragraphException = paragraphException;
            this.textFromPieces = textFromPieces;
        }

        /**
         * 获取段落文本数组。
         *
         * @return 段落文本数组
         */
        @Override
        public String[] getParagraphText() {
            throw paragraphException;
        }

        /**
         * 获取 text pieces 文本。
         *
         * @return text pieces 文本
         */
        @Override
        public String getTextFromPieces() {
            return textFromPieces;
        }
    }
}
