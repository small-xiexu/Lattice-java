package com.xbk.lattice.infra.chunking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SemanticChunker 测试
 *
 * 职责：验证 Markdown 语义断点与代码围栏保护策略
 *
 * @author xiexu
 */
class SemanticChunkerTests {

    /**
     * 验证标题、空行与列表项会作为优先断点。
     */
    @Test
    void shouldPreferSemanticBreakpointsForMarkdownContent() {
        SemanticChunker semanticChunker = new SemanticChunker();
        String content = "# Payment Timeout\n"
                + "summary line\n\n"
                + "## Timeout Rules\n"
                + "- retry=3\n"
                + "- interval=30s\n\n"
                + "## Fallback\n"
                + "- manual-review\n";

        List<TextChunk> chunks = semanticChunker.chunk(content, 45, 0.15f);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).getText()).contains("# Payment Timeout");
        assertThat(chunks.stream().anyMatch(chunk -> chunk.getText().contains("## Timeout Rules"))).isTrue();
        assertThat(chunks.stream().anyMatch(chunk -> chunk.getText().contains("## Fallback"))).isTrue();
    }

    /**
     * 验证 ATX 标题（##）强制开始新 chunk，使其成为后续内容所在 chunk 的首行。
     */
    @Test
    void shouldStartNewChunkAtAtxHeadingWhenBuilderHasContent() {
        SemanticChunker semanticChunker = new SemanticChunker();
        String content = "intro text\n\n"
                + "## Section A\n"
                + "content for section A\n\n"
                + "## Section B\n"
                + "content for section B\n";

        List<TextChunk> chunks = semanticChunker.chunk(content, 500, 0.15f);

        long headingAChunks = chunks.stream()
                .filter(chunk -> chunk.getText().startsWith("## Section A"))
                .count();
        long headingBChunks = chunks.stream()
                .filter(chunk -> chunk.getText().startsWith("## Section B"))
                .count();
        assertThat(headingAChunks).isEqualTo(1);
        assertThat(headingBChunks).isEqualTo(1);
    }

    /**
     * 验证标题边界规则依赖通用文本格式而非具体标题文本。
     */
    @Test
    void shouldTreatAnyAtxHeadingAsBoundaryRegardlessOfText() {
        SemanticChunker semanticChunker = new SemanticChunker();
        String content = "preamble\n\n"
                + "# arbitrary title\n"
                + "body\n\n"
                + "### another arbitrary\n"
                + "more body\n";

        List<TextChunk> chunks = semanticChunker.chunk(content, 500, 0.15f);

        assertThat(chunks.get(0).getText()).doesNotStartWith("# arbitrary title");
        assertThat(chunks.get(0).getText()).doesNotStartWith("### another arbitrary");
        assertThat(chunks.stream().anyMatch(chunk -> chunk.getText().startsWith("# arbitrary title"))).isTrue();
        assertThat(chunks.stream().anyMatch(chunk -> chunk.getText().startsWith("### another arbitrary"))).isTrue();
    }

    /**
     * 验证列表项、水平线等非 ATX 标题语义单元不强制产生 chunk 边界。
     */
    @Test
    void shouldNotForceChunkBoundaryOnNonAtxMarkers() {
        SemanticChunker semanticChunker = new SemanticChunker();
        String content = "intro line\n\n"
                + "- list item one\n"
                + "- list item two\n\n"
                + "more content\n\n"
                + "---\n\n"
                + "after horizontal rule\n";

        List<TextChunk> chunks = semanticChunker.chunk(content, 500, 0.15f);

        assertThat(chunks).hasSize(1);
    }

    /**
     * 验证 maxChars 限制在 ATX 标题边界规则下仍然生效。
     */
    @Test
    void shouldStillRespectMaxCharsWithAtxHeadingBoundary() {
        SemanticChunker semanticChunker = new SemanticChunker();
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("start\n\n");
        for (int i = 0; i < 10; i++) {
            contentBuilder.append("line ").append(i).append("\n");
        }
        contentBuilder.append("\n## Final Section\nfinal content\n");
        String content = contentBuilder.toString();

        List<TextChunk> chunks = semanticChunker.chunk(content, 25, 0.15f);

        assertThat(chunks).hasSizeGreaterThan(2);
    }

    /**
     * 验证纯文本无 Markdown 标题时的 chunking 行为不受影响。
     */
    @Test
    void shouldNotAffectPlainTextChunking() {
        SemanticChunker semanticChunker = new SemanticChunker();
        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            contentBuilder.append("plain text line number ").append(i).append("\n");
        }
        String content = contentBuilder.toString();

        List<TextChunk> chunks = semanticChunker.chunk(content, 100, 0.15f);

        for (TextChunk chunk : chunks) {
            assertThat(chunk.getText()).doesNotStartWith("#");
        }
    }

    /**
     * 验证 ATX heading boundary break 时不应用 overlap rewind。
     *
     * 当当前 chunk 中至少有两个 TextUnit 且 overlapRatio > 0 时，heading
     * boundary 触发 break 后不应回退到 heading 前的段落。构造两个前置段落，
     * 第二段足够长（超过 overlapChars），使旧逻辑执行 rewind 后会回退到
     * 第二段。下一 chunk 必须从 heading 单元开始。
     */
    @Test
    void shouldNotApplyOverlapWhenBreakingAtAtxHeadingBoundary() {
        SemanticChunker semanticChunker = new SemanticChunker();
        String content = "paragraph one\n\n"
                + "this is a longer paragraph with more text content "
                + "to make sure the overlap rewind would step back into it\n\n"
                + "## Target Section\n"
                + "content for target section\n";

        List<TextChunk> chunks = semanticChunker.chunk(content, 200, 0.5f);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks.get(0).getText()).doesNotContain("## Target Section");
        assertThat(chunks.get(1).getText()).startsWith("## Target Section");
        assertThat(chunks.get(1).getText()).doesNotStartWith("this is a longer");
    }

    /**
     * 验证代码围栏内部不会被切断。
     */
    @Test
    void shouldNotSplitInsideCodeFence() {
        SemanticChunker semanticChunker = new SemanticChunker();
        String codeBlock = "```java\n"
                + "public void demo() {\n"
                + "    System.out.println(\"hello\");\n"
                + "    System.out.println(\"world\");\n"
                + "}\n"
                + "```\n";
        String content = "# Demo\n"
                + "intro\n\n"
                + codeBlock
                + "\n## After\n"
                + "tail";

        List<TextChunk> chunks = semanticChunker.chunk(content, 40, 0.15f);

        long codeFenceChunkCount = chunks.stream()
                .filter(chunk -> chunk.getText().contains("```java") || chunk.getText().contains("System.out.println"))
                .count();
        assertThat(codeFenceChunkCount).isEqualTo(1);
        assertThat(chunks.stream().filter(chunk -> chunk.getText().contains("```java")).findFirst().orElseThrow().getText())
                .contains("```java")
                .contains("System.out.println(\"hello\")")
                .contains("```");
    }
}
