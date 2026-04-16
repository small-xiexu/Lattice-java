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
        assertThat(chunks.get(0).text()).contains("# Payment Timeout");
        assertThat(chunks.stream().anyMatch(chunk -> chunk.text().contains("## Timeout Rules"))).isTrue();
        assertThat(chunks.stream().anyMatch(chunk -> chunk.text().contains("## Fallback"))).isTrue();
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
                .filter(chunk -> chunk.text().contains("```java") || chunk.text().contains("System.out.println"))
                .count();
        assertThat(codeFenceChunkCount).isEqualTo(1);
        assertThat(chunks.stream().filter(chunk -> chunk.text().contains("```java")).findFirst().orElseThrow().text())
                .contains("```java")
                .contains("System.out.println(\"hello\")")
                .contains("```");
    }
}
