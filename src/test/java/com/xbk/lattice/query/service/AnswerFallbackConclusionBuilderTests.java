package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerFallbackConclusionBuilder 测试
 *
 * 职责：验证确定性 fallback 结论行的基础编排
 *
 * @author xiexu
 */
class AnswerFallbackConclusionBuilderTests {

    private final AnswerGenerationService support = new AnswerGenerationService();

    private final AnswerFallbackConclusionBuilder conclusionBuilder = new AnswerFallbackConclusionBuilder(support);

    /**
     * 验证无证据时结论列表为空，由 Markdown 构建器负责缺省文案。
     */
    @Test
    void shouldReturnEmptyConclusionWhenEvidenceMissing() {
        List<String> conclusionLines = conclusionBuilder.buildEvidenceConclusionLines(
                "timeout 配置是多少",
                List.<QueryArticleHit>of(),
                support.extractQueryTokens("timeout 配置是多少")
        );

        assertThat(conclusionLines).isEmpty();
    }

    /**
     * 验证普通证据会生成带 citation 的结论行。
     */
    @Test
    void shouldBuildConclusionLineWithCitation() {
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "timeout-rule",
                "Timeout Rule",
                "content: timeout_seconds = 30",
                "",
                List.of("docs/rule.md"),
                0.8
        );

        List<String> conclusionLines = conclusionBuilder.buildEvidenceConclusionLines(
                "timeout 配置是多少",
                List.of(sourceHit),
                support.extractQueryTokens("timeout 配置是多少")
        );

        assertThat(conclusionLines).hasSize(1);
        assertThat(conclusionLines.get(0)).contains("timeout_seconds = 30");
        assertThat(conclusionLines.get(0)).contains("[→ docs/rule.md]");
    }
}
