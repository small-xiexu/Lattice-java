package com.xbk.lattice.compiler.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CompilerPromptProvider 测试
 *
 * 职责：验证外置 prompt 文件加载、占位符替换与内容语义等价
 *
 * @author xiexu
 */
class CompilerPromptProviderTests {

    private final CompilerPromptProvider provider = new CompilerPromptProvider();

    /**
     * 验证 writerPrompt 非空且不含未解析占位符。
     */
    @Test
    void writerPromptShouldBeNonBlankAndResolved() {
        String prompt = provider.writerPrompt();

        assertThat(prompt).isNotBlank();
        assertThat(prompt).doesNotContain("{{");
    }

    /**
     * 验证 writerImagePrompt 非空且不含未解析占位符。
     */
    @Test
    void writerImagePromptShouldBeNonBlankAndResolved() {
        String prompt = provider.writerImagePrompt();

        assertThat(prompt).isNotBlank();
        assertThat(prompt).doesNotContain("{{");
    }

    /**
     * 验证 reviewerPrompt 非空且不含未解析占位符。
     */
    @Test
    void reviewerPromptShouldBeNonBlankAndResolved() {
        String prompt = provider.reviewerPrompt();

        assertThat(prompt).isNotBlank();
        assertThat(prompt).doesNotContain("{{");
    }

    /**
     * 验证 reviewerImagePrompt 非空且不含未解析占位符。
     */
    @Test
    void reviewerImagePromptShouldBeNonBlankAndResolved() {
        String prompt = provider.reviewerImagePrompt();

        assertThat(prompt).isNotBlank();
        assertThat(prompt).doesNotContain("{{");
    }

    /**
     * 验证 fixerPrompt 非空且不含未解析占位符。
     */
    @Test
    void fixerPromptShouldBeNonBlankAndResolved() {
        String prompt = provider.fixerPrompt();

        assertThat(prompt).isNotBlank();
        assertThat(prompt).doesNotContain("{{");
    }

    /**
     * 验证 writerPrompt 与 LatticePrompts.SYSTEM_COMPILE_ARTICLE 语义等价。
     */
    @Test
    void writerPromptShouldMatchLatticePromptsConstant() {
        String prompt = provider.writerPrompt();

        assertThat(prompt).contains("You are a knowledge compiler");
        assertThat(prompt).contains("TRUTH LEVEL ANNOTATIONS");
        assertThat(prompt).contains("KNOWLEDGE CLASSIFICATION");
        assertThat(prompt).contains("do NOT write a full expected result section from analogy");
        assertThat(prompt).contains("referential_keywords");
        assertThat(normalizeWhitespace(prompt))
                .isEqualTo(normalizeWhitespace(LatticePrompts.SYSTEM_COMPILE_ARTICLE));
    }

    /**
     * 验证 writerImagePrompt 与 LatticePrompts.SYSTEM_COMPILE_IMAGE_ARTICLE 语义等价。
     */
    @Test
    void writerImagePromptShouldMatchLatticePromptsConstant() {
        String prompt = provider.writerImagePrompt();

        assertThat(prompt).contains("UI screenshots, diagrams, OCR assets");
        assertThat(prompt).contains("TRUTH LEVEL ANNOTATIONS");
        assertThat(prompt).contains("KNOWLEDGE CLASSIFICATION");
        assertThat(prompt).contains("Special rules for image/OCR based concepts");
        assertThat(normalizeWhitespace(prompt))
                .isEqualTo(normalizeWhitespace(LatticePrompts.SYSTEM_COMPILE_IMAGE_ARTICLE));
    }

    /**
     * 验证 reviewerPrompt 与 LatticePrompts.SYSTEM_REVIEW 语义等价。
     */
    @Test
    void reviewerPromptShouldMatchLatticePromptsConstant() {
        String prompt = provider.reviewerPrompt();

        assertThat(prompt).contains("You are a knowledge base REVIEWER");
        assertThat(prompt).contains("TRUTH LEVEL ANNOTATIONS");
        assertThat(prompt).contains("CHECK 1 — Referential Knowledge Completeness");
        assertThat(prompt).contains("CHECK 5 — Speculative Abnormal Scenarios");
        assertThat(normalizeWhitespace(prompt))
                .isEqualTo(normalizeWhitespace(LatticePrompts.SYSTEM_REVIEW));
    }

    /**
     * 验证 reviewerImagePrompt 与 LatticePrompts.SYSTEM_REVIEW_IMAGE_ARTICLE 语义等价。
     */
    @Test
    void reviewerImagePromptShouldMatchLatticePromptsConstant() {
        String prompt = provider.reviewerImagePrompt();

        assertThat(prompt).contains("screenshots, diagrams, OCR assets");
        assertThat(prompt).contains("TRUTH LEVEL ANNOTATIONS");
        assertThat(prompt).contains("CHECK 1 — Important UI / Architecture Completeness");
        assertThat(normalizeWhitespace(prompt))
                .isEqualTo(normalizeWhitespace(LatticePrompts.SYSTEM_REVIEW_IMAGE_ARTICLE));
    }

    /**
     * 验证 fixerPrompt 与 LatticePrompts.SYSTEM_REVIEW_FIX 语义等价。
     */
    @Test
    void fixerPromptShouldMatchLatticePromptsConstant() {
        String prompt = provider.fixerPrompt();

        assertThat(prompt).contains("你是知识编译器");
        assertThat(prompt).contains("不要根据相邻上下文脑补新的精确值");
        assertThat(prompt).contains("保留这条修正关系");
        assertThat(prompt).contains("10. 输出完整的修正后文章");
        assertThat(normalizeWhitespace(prompt))
                .isEqualTo(normalizeWhitespace(LatticePrompts.SYSTEM_REVIEW_FIX));
    }

    /**
     * 验证四个 role prompt 都会注入 shared-grounding-rules 内容。
     */
    @Test
    void rolePromptsShouldContainSharedGroundingRulesContent() {
        List<String> prompts = List.of(
                provider.writerPrompt(),
                provider.writerImagePrompt(),
                provider.reviewerPrompt(),
                provider.reviewerImagePrompt()
        );

        for (String prompt : prompts) {
            assertThat(prompt).doesNotContain("{{");
            assertThat(prompt).contains("TRUTH LEVEL ANNOTATIONS");
            assertThat(prompt).contains("KNOWLEDGE CLASSIFICATION");
            assertThat(prompt).contains("Referential Knowledge");
        }
    }

    /**
     * 验证缺失 prompt 文件会 fail-fast。
     */
    @Test
    void missingPromptShouldFailFast() {
        CompilerPromptProvider.PromptResourceLoader loader = path -> {
            if ("prompts/compiler/writer.md".equals(path)) {
                return null;
            }
            return promptFixture(path);
        };

        assertThatThrownBy(() -> new CompilerPromptProvider(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Compiler prompt file missing: prompts/compiler/writer.md");
    }

    /**
     * 验证空 prompt 文件会 fail-fast。
     */
    @Test
    void emptyPromptShouldFailFast() {
        CompilerPromptProvider.PromptResourceLoader loader = path -> {
            if ("prompts/compiler/reviewer.md".equals(path)) {
                return "   ";
            }
            return promptFixture(path);
        };

        assertThatThrownBy(() -> new CompilerPromptProvider(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Compiler prompt file is empty: prompts/compiler/reviewer.md");
    }

    /**
     * 返回测试用 prompt 内容。
     *
     * @param path classpath 路径
     * @return prompt 内容
     */
    private static String promptFixture(String path) {
        if ("prompts/compiler/shared-grounding-rules.md".equals(path)) {
            return "TRUTH LEVEL ANNOTATIONS\nKNOWLEDGE CLASSIFICATION\nReferential Knowledge";
        }
        if ("prompts/compiler/fixer.md".equals(path)) {
            return "fixer prompt";
        }
        return "{{shared-grounding-rules}}";
    }

    /**
     * 将字符串中的连续空白字符（包括换行）统一为单个空格后 trim。
     *
     * @param text 原始文本
     * @return 归一化后的文本
     */
    private static String normalizeWhitespace(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }
}
