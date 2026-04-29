package com.xbk.lattice.article.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ArticleMarkdownSupport 测试
 *
 * 职责：验证模型生成 Markdown 的 frontmatter 边界归一能力
 *
 * @author xiexu
 */
class ArticleMarkdownSupportTests {

    /**
     * 验证模型说明性前言不会进入最终文章。
     */
    @Test
    void shouldStripPreambleBeforeValidFrontmatter() {
        String normalizedContent = ArticleMarkdownSupport.normalizeGeneratedMarkdown("""
                Here is the revised article. It avoids unsupported assumptions.

                ---
                ---
                title: "Business Migration"
                summary: "Migration facts"
                sources:
                  - "migration.pdf"
                review_status: pending
                ---

                # Business Migration

                Keep endpoint compatibility.
                """);

        assertThat(normalizedContent).startsWith("---\ntitle: \"Business Migration\"");
        assertThat(normalizedContent).doesNotContain("Here is the revised article");
        assertThat(normalizedContent).contains("Keep endpoint compatibility.");
    }

    /**
     * 验证带代码围栏的文章也能归一并补齐审查状态。
     */
    @Test
    void shouldNormalizeFencedArticleAndReviewStatus() {
        String normalizedContent = ArticleMarkdownSupport.normalizeReviewStatus("""
                The fixed markdown is below:

                ```markdown
                ---
                title: "Compatibility"
                summary: "API compatibility"
                sources: []
                ---

                # Compatibility

                /api/v1/example remains compatible.
                ```
                """, "passed");

        assertThat(normalizedContent).startsWith("---\ntitle: \"Compatibility\"");
        assertThat(normalizedContent).contains("review_status: passed");
        assertThat(normalizedContent).contains("/api/v1/example remains compatible.");
        assertThat(normalizedContent).doesNotContain("The fixed markdown is below");
        assertThat(normalizedContent).doesNotContain("```");
    }

    /**
     * 验证缺少 frontmatter 时会从首个顶级标题开始保留正文。
     */
    @Test
    void shouldStripPreambleBeforeTopLevelHeadingWhenFrontmatterMissing() {
        String normalizedContent = ArticleMarkdownSupport.normalizeGeneratedMarkdown("""
                Fixed article summary:
                1. Rechecked missing facts.
                2. Preserved all source references.

                ---

                # Migration Plan

                The API path /api/v1/example remains compatible.
                """);

        assertThat(normalizedContent).startsWith("# Migration Plan");
        assertThat(normalizedContent).contains("/api/v1/example remains compatible.");
        assertThat(normalizedContent).doesNotContain("Fixed article summary");
    }
}
