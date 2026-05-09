package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerFallbackMarkdownBuilder 测试
 *
 * 职责：验证确定性 fallback Markdown 与修订 Markdown 的组装结构
 *
 * @author xiexu
 */
class AnswerFallbackMarkdownBuilderTests {

    private final AnswerGenerationService support = new AnswerGenerationService();

    private final AnswerFallbackMarkdownBuilder fallbackMarkdownBuilder = new AnswerFallbackMarkdownBuilder(support);

    /**
     * 验证无证据时 fallback Markdown 保持稳定章节与缺省结论。
     */
    @Test
    void shouldBuildFallbackMarkdownWithoutEvidence() {
        String answerMarkdown = fallbackMarkdownBuilder.buildFallbackMarkdown("timeout 配置是多少", List.of());

        assertThat(answerMarkdown).contains("# 查询回答");
        assertThat(answerMarkdown).contains("## 问题");
        assertThat(answerMarkdown).contains("timeout 配置是多少");
        assertThat(answerMarkdown).contains("当前未找到与该问题直接相关的知识。");
    }

    /**
     * 验证修订 fallback Markdown 会携带纠正输入与分组证据。
     */
    @Test
    void shouldBuildFallbackRevisionMarkdownWithEvidenceSection() {
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "timeout-rule",
                "Timeout Rule",
                "content: timeout_seconds = 30",
                "",
                List.of("docs/rule.md"),
                0.8
        );

        String revisionMarkdown = fallbackMarkdownBuilder.buildFallbackRevisionMarkdown(
                "timeout 配置是多少",
                "旧答案 timeout=20",
                "timeout 应为 30",
                List.of(sourceHit)
        );

        assertThat(revisionMarkdown).contains("# 修订答案");
        assertThat(revisionMarkdown).contains("timeout 应为 30");
        assertThat(revisionMarkdown).contains("## 源文件证据");
        assertThat(revisionMarkdown).contains("Timeout Rule");
        assertThat(revisionMarkdown).contains("[→ docs/rule.md]");
    }
}
