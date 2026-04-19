package com.xbk.lattice.documentparse.service;

import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.documentparse.domain.DocumentParseMode;
import com.xbk.lattice.documentparse.domain.DocumentParseResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocumentParseResultNormalizer 测试
 *
 * 职责：验证文档解析结果会被标准化为带解析上下文的 RawSource
 *
 * @author xiexu
 */
class DocumentParseResultNormalizerTests {

    /**
     * 验证 OCR 结果会写入统一 RawSource 契约。
     */
    @Test
    void shouldNormalizeDocumentParseResultIntoRawSource() {
        DocumentParseResultNormalizer normalizer = new DocumentParseResultNormalizer();
        DocumentParseResult result = new DocumentParseResult(
                Long.valueOf(12L),
                "docs/scan.png",
                "image ocr text",
                "png",
                256L,
                DocumentParseMode.OCR_IMAGE,
                "tencent_ocr",
                "{\"page\":1}",
                true,
                "docs/scan.png"
        );

        RawSource rawSource = normalizer.normalize(result);

        assertThat(rawSource.getSourceId()).isEqualTo(Long.valueOf(12L));
        assertThat(rawSource.getRelativePath()).isEqualTo("docs/scan.png");
        assertThat(rawSource.getContent()).isEqualTo("image ocr text");
        assertThat(rawSource.getParseMode()).isEqualTo("ocr_image");
        assertThat(rawSource.getParseProvider()).isEqualTo("tencent_ocr");
        assertThat(rawSource.getContentHash()).isNotBlank();
        assertThat(rawSource.getMetadataJson()).contains("\"page\":1");
        assertThat(rawSource.getMetadataJson()).contains("\"ocrApplied\":true");
        assertThat(rawSource.getMetadataJson()).contains("\"relativePath\":\"docs/scan.png\"");
    }
}
