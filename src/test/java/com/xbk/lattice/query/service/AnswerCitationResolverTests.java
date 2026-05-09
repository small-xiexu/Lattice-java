package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerCitationResolver 测试
 *
 * 职责：验证答案 citation 的解析、清理与同源 source 回落
 *
 * @author xiexu
 */
class AnswerCitationResolverTests {

    private final AnswerCitationResolver citationResolver = new AnswerCitationResolver();

    /**
     * 验证 fact card 会优先回落到同源 source chunk 的引用。
     */
    @Test
    void shouldResolveFactCardCitationToCompanionSourceCitation() {
        QueryArticleHit factCardHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "fact-card:100:0:fact_enum:abc12345",
                "Fact Card",
                "settle_window=45m 表示结算窗口为 45 分钟。",
                "{\"cardType\":\"FACT_ENUM\"}",
                List.of("payment/context.md"),
                2.5D
        );
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "payment/context.md#0",
                "payment/context.md",
                "settle_window=45m 表示结算窗口为 45 分钟。",
                "{\"filePath\":\"payment/context.md\"}",
                List.of("payment/context.md"),
                2.0D
        );

        String citationLiteral = citationResolver.resolveCitationLiteral(factCardHit, List.of(factCardHit, sourceHit));

        assertThat(citationLiteral).isEqualTo("[→ payment/context.md]");
    }

    /**
     * 验证 citation 清理不会留下重复空白。
     */
    @Test
    void shouldStripEmbeddedCitationLiterals() {
        String stripped = citationResolver.stripEmbeddedCitationLiterals(
                "settle_window=45m [[payment-routing]] [→ payment/context.md]。"
        );

        assertThat(citationResolver.containsSourceCitationLiteral("[→ payment/context.md]")).isTrue();
        assertThat(stripped).isEqualTo("settle_window=45m。");
        assertThat(citationResolver.containsCitationLiteral(stripped)).isFalse();
    }
}
