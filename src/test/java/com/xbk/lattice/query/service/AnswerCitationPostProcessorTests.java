package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerCitationPostProcessor 测试
 *
 * 职责：验证结构化答案 citation 的自动补齐与表格归位
 *
 * @author xiexu
 */
class AnswerCitationPostProcessorTests {

    private final AnswerCitationPostProcessor citationPostProcessor = new AnswerCitationPostProcessor(
            new AnswerGenerationService(),
            new AnswerCitationResolver()
    );

    /**
     * 验证普通正文行缺 citation 时会补上 source 引用。
     */
    @Test
    void shouldAttachSourceCitationToUncitedLine() {
        String answerMarkdown = citationPostProcessor.attachDefaultCitationWhenMissing(
                "settle_window=45m 表示结算窗口为 45 分钟。",
                "settle_window=45m 是什么",
                List.of(sourceHit())
        );

        assertThat(answerMarkdown).isEqualTo("settle_window=45m 表示结算窗口为 45 分钟。 [→ payment/context.md]");
    }

    /**
     * 验证表格数据行会把 citation 放进最后一个单元格，而不是表格外。
     */
    @Test
    void shouldAttachCitationInsideMarkdownTableDataRow() {
        String answerMarkdown = citationPostProcessor.attachDefaultCitationWhenMissing(
                """
                        | 字段 | 含义 |
                        | --- | --- |
                        | settle_window | 45 分钟 |
                        """,
                "settle_window=45m 是什么",
                List.of(sourceHit())
        );

        assertThat(answerMarkdown).contains("| settle_window | 45 分钟 [→ payment/context.md] |");
        assertThat(answerMarkdown).contains("| 字段 | 含义 |");
        assertThat(answerMarkdown).contains("| --- | --- |");
    }

    private QueryArticleHit sourceHit() {
        return new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "payment/context.md#0",
                "payment/context.md",
                "settle_window=45m 表示结算窗口为 45 分钟。",
                "{\"filePath\":\"payment/context.md\"}",
                List.of("payment/context.md"),
                2.0D
        );
    }
}
