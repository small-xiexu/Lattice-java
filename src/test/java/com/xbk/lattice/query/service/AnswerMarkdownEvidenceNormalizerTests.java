package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerMarkdownEvidenceNormalizer 测试
 *
 * 职责：验证 fallback 证据行、Markdown 表格行与结构化 JSON 行的通用归一化
 *
 * @author xiexu
 */
class AnswerMarkdownEvidenceNormalizerTests {

    private final AnswerMarkdownEvidenceNormalizer normalizer = new AnswerMarkdownEvidenceNormalizer();

    /**
     * 验证配置键值表格行会归一成可读事实句。
     */
    @Test
    void shouldNormalizeMarkdownTableRowToFactSentence() {
        String normalizedLine = normalizer.normalizeMarkdownTableRow("| `timeout_seconds` | **30** | 当前值 |");

        assertThat(normalizedLine).isEqualTo("timeout_seconds = 30，当前值");
    }

    /**
     * 验证 Markdown 表头与分隔线不会作为事实行展示。
     */
    @Test
    void shouldSkipMarkdownTableHeaderAndDividerRows() {
        boolean headerWithDivider = normalizer.isMarkdownTableHeaderWithDivider(
                "| 配置键 | 精确值 |",
                "| --- | --- |"
        );
        String dividerLine = normalizer.normalizeFallbackLineCandidate("| --- | --- |");

        assertThat(headerWithDivider).isTrue();
        assertThat(dividerLine).isEmpty();
    }

    /**
     * 验证结构化抽取残留前缀与 summary 字段名会被剥离。
     */
    @Test
    void shouldStripStructuredPrefixAndKeepFieldValue() {
        String normalizedLine = normalizer.normalizeFallbackLineCandidate(
                "table_row: sheet=Rules; row=7; summary: \"配置已启用\""
        );

        assertThat(normalizedLine).isEqualTo("配置已启用");
    }

    /**
     * 验证结构化 JSON 会抽取可读叶子值。
     */
    @Test
    void shouldSelectReadableStructuredJsonValues() {
        List<String> valueLines = normalizer.selectStructuredJsonValueLines("""
                {"summary":"默认超时时间为 30 秒","value":30,"nested":{"content":"启用自动重试"}}
                """);

        assertThat(valueLines).contains("默认超时时间为 30 秒", "30", "启用自动重试");
    }

    /**
     * 验证 metadata 与纯媒体行会被过滤。
     */
    @Test
    void shouldFilterMetadataAndMediaLines() {
        List<String> filteredLines = normalizer.filterFallbackMatchedLines(List.of(
                "title: 内部标题",
                "![截图](x.png)",
                "content: \"真实事实\""
        ));

        assertThat(filteredLines).containsExactly("真实事实");
    }

    /**
     * 验证结构化事实赋值判定仍保持通用配置键口径。
     */
    @Test
    void shouldDetectDirectStructuredFactAssignment() {
        boolean directAssignment = normalizer.startsWithDirectStructuredFactAssignment("lattice.query.timeout = 30");
        boolean plainText = normalizer.startsWithDirectStructuredFactAssignment("普通文本说明");

        assertThat(directAssignment).isTrue();
        assertThat(plainText).isFalse();
    }
}
