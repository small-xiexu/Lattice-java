package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerParagraphPostProcessor 测试
 *
 * 职责：验证段落级保守引言移除与精确查值答案压缩
 *
 * @author xiexu
 */
class AnswerParagraphPostProcessorTests {

    private final AnswerGenerationService support = new AnswerGenerationService();

    private final AnswerParagraphPostProcessor paragraphPostProcessor = new AnswerParagraphPostProcessor(support);

    /**
     * 验证精确查值题会压缩掉多余补充段。
     */
    @Test
    void shouldCompressStructuredExactLookupAnswer() {
        String answerMarkdown = """
                字段命名必须采用统一前缀规则。[→ payment/context.md]

                补充说明：这是一个字段命名的补充口径。[→ payment/context.md]

                第三段：更多扩展说明。[→ payment/context.md]
                """;

        String compressedAnswer = paragraphPostProcessor.compressStructuredExactLookupAnswer(
                answerMarkdown,
                "这个规则是什么？"
        );

        assertThat(compressedAnswer).contains("字段命名必须采用统一前缀规则。");
        assertThat(compressedAnswer).contains("补充说明");
        assertThat(compressedAnswer).doesNotContain("第三段");
    }
}
