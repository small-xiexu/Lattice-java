package com.xbk.lattice.documentparse.service;

import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.documentparse.domain.DocumentParseMode;
import com.xbk.lattice.documentparse.domain.model.ParseOutput;
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
        ParseOutput parseOutput = new ParseOutput(
                Long.valueOf(12L),
                "docs/scan.png",
                "image ocr text",
                "",
                "",
                "png",
                256L,
                DocumentParseMode.OCR_IMAGE,
                "tencent_ocr",
                "{\"page\":1}",
                true,
                "docs/scan.png"
        );

        RawSource rawSource = normalizer.normalize(parseOutput);

        assertThat(rawSource.getSourceId()).isEqualTo(Long.valueOf(12L));
        assertThat(rawSource.getRelativePath()).isEqualTo("docs/scan.png");
        assertThat(rawSource.getContent()).isEqualTo("image ocr text");
        assertThat(rawSource.getParseMode()).isEqualTo("ocr_image");
        assertThat(rawSource.getParseProvider()).isEqualTo("tencent_ocr");
        assertThat(rawSource.getContentHash()).isNotBlank();
        assertThat(rawSource.getMetadataJson()).contains("\"page\":1");
        assertThat(rawSource.getMetadataJson()).contains("\"ocrApplied\":true");
        assertThat(rawSource.getMetadataJson()).contains("\"relativePath\":\"docs/scan.png\"");
        assertThat(rawSource.getMetadataJson()).contains("\"contentFormat\":\"plain_text\"");
        assertThat(rawSource.getMetadataJson()).contains("\"documentTitle\":\"scan\"");
    }

    /**
     * 验证文本解析结果会优先从正文 H1 提取 documentTitle。
     */
    @Test
    void shouldResolveTextDocumentTitleFromHeading() {
        DocumentParseResultNormalizer normalizer = new DocumentParseResultNormalizer();
        ParseOutput parseOutput = new ParseOutput(
                Long.valueOf(13L),
                "docs/heading.md",
                "# Delivery Plan\n\nbody",
                "",
                "",
                "md",
                128L,
                DocumentParseMode.TEXT_READ,
                "filesystem",
                "{}",
                false,
                "docs/heading.md"
        );

        String metadataJson = normalizer.normalizeMetadata(parseOutput);

        assertThat(metadataJson).contains("\"documentTitle\":\"Delivery Plan\"");
    }

    /**
     * 验证解析 metadata 中已有标题时会优先复用该标题。
     */
    @Test
    void shouldReuseDocumentTitleFromMetadataCandidates() {
        DocumentParseResultNormalizer normalizer = new DocumentParseResultNormalizer();
        ParseOutput parseOutput = new ParseOutput(
                Long.valueOf(14L),
                "docs/review.pptx",
                "slide body",
                "",
                "",
                "pptx",
                512L,
                DocumentParseMode.OFFICE_EXTRACT,
                "poi_ppt",
                "{\"slideTitles\":[\"Weekly Review\",\"Appendix\"]}",
                true,
                "docs/review.pptx"
        );

        String metadataJson = normalizer.normalizeMetadata(parseOutput);

        assertThat(metadataJson).contains("\"documentTitle\":\"Weekly Review\"");
    }
}
