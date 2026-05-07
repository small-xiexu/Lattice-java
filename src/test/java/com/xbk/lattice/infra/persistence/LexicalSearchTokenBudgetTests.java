package com.xbk.lattice.infra.persistence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LexicalSearchTokenBudget 测试
 *
 * 职责：验证 LIKE token 预算只依赖通用 token 形态
 *
 * @author xiexu
 */
class LexicalSearchTokenBudgetTests {

    /**
     * 验证 token 会小写去重并去掉空白。
     */
    @Test
    void shouldNormalizeTokensByCaseAndOrder() {
        List<String> normalizedTokens = LexicalSearchTokenBudget.normalize(List.of(
                "  Alpha ",
                "alpha",
                "BETA",
                " "
        ));

        assertThat(normalizedTokens).containsExactly("alpha", "beta");
    }

    /**
     * 验证 LIKE token 选择有固定上限并优先保留结构化标识符。
     */
    @Test
    void shouldSelectBoundedHighSignalTokensForLikeConditions() {
        List<String> likeTokens = LexicalSearchTokenBudget.selectLikeTokens(List.of(
                "普通",
                "token",
                "/alpha/beta",
                "alpha.beta",
                "alpha_beta",
                "alpha-beta",
                "key=value",
                "123456",
                "another",
                "更多",
                "plain",
                "extra"
        ));

        assertThat(likeTokens).hasSize(8);
        assertThat(likeTokens)
                .contains("/alpha/beta", "alpha.beta", "alpha_beta", "alpha-beta", "key=value", "123456");
    }
}
