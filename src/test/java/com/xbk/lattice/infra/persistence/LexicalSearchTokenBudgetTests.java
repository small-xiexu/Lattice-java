package com.xbk.lattice.infra.persistence;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
     * 上限已从 8 提高到 32，当前输入 12 个 token 全部在预算内。
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

        assertThat(likeTokens).hasSize(12);
        assertThat(likeTokens)
                .contains("/alpha/beta", "alpha.beta", "alpha_beta", "alpha-beta", "key=value", "123456");
    }

    /**
     * 验证 LIKE token 数量不超过 MAX_LIKE_TOKENS 上限（当前为 32）。
     * 使用大量 synthetic CJK token 超出预算，验证截断正确。
     */
    @Test
    void shouldEnforceMaxLikeTokenCap() {
        List<String> inputTokens = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            inputTokens.add(cjkBigramAt(i));
        }

        List<String> likeTokens = LexicalSearchTokenBudget.selectLikeTokens(inputTokens);

        assertThat(likeTokens).hasSize(32);
    }

    /**
     * 验证 CJK token 评分：较短 token（bigram）排名高于较长 token（trigram/quadgram）。
     * bigram (len=2) = 228, trigram (len=3) = 227, quadgram (len=4) = 226。
     */
    @Test
    void shouldRankCjkBigramAboveTrigramAndQuadgram() {
        List<String> likeTokens = LexicalSearchTokenBudget.selectLikeTokens(List.of(
                "甲乙丙丁",
                "状态值",
                "甲乙",
                "配置项",
                "处理结果",
                "甲乙丙"
        ));

        assertThat(likeTokens)
                .contains("甲乙丙丁", "状态值", "甲乙丙");

        int bigramIndex = likeTokens.indexOf("甲乙");
        int trigramIndex = likeTokens.indexOf("甲乙丙");
        int quadgramIndex = likeTokens.indexOf("甲乙丙丁");

        assertThat(bigramIndex).isLessThan(trigramIndex);
        assertThat(bigramIndex).isLessThan(quadgramIndex);
    }

    /**
     * 验证在结构化 token 存在时，CJK bigram 不会被预算挤出。
     * 模拟文件名前缀 + CJK N-gram 混合查询的场景：
     * 2 个结构化 token + 20 个 CJK quadgram + 1 个 CJK bigram，
     * 在 32 token 预算下 CJK bigram 应入选且排在所有同分 quadgram 之前。
     */
    @Test
    void shouldNotCrowdOutCjkBigramWhenStructuredTokensPresent() {
        List<String> inputTokens = new ArrayList<>();
        inputTokens.add("example-document.xlsx");
        inputTokens.add("xlsx");
        for (int i = 0; i < 20; i++) {
            inputTokens.add("项目" + cjkBigramAt(i));
        }
        inputTokens.add("字段");

        List<String> likeTokens = LexicalSearchTokenBudget.selectLikeTokens(inputTokens);

        assertThat(likeTokens).hasSize(23);

        int bigramIndex = likeTokens.indexOf("字段");
        assertThat(bigramIndex).isGreaterThanOrEqualTo(0);
        assertThat(bigramIndex).isLessThan(6);
    }

    /**
     * 验证纯数字 token 评分正确（len >= 2 时 420+len，单数字时 80）。
     */
    @Test
    void shouldScoreNumericTokensCorrectly() {
        List<String> likeTokens = LexicalSearchTokenBudget.selectLikeTokens(List.of(
                "1",
                "42",
                "100",
                "9999"
        ));

        assertThat(likeTokens).contains("42", "100", "9999");
        assertThat(likeTokens.indexOf("9999")).isLessThan(likeTokens.indexOf("42"));
        assertThat(likeTokens.indexOf("1")).isGreaterThan(likeTokens.indexOf("42"));
    }

    /**
     * 验证 Han + Latin/数字的短混合脚本 token 有正分，可进入 LIKE token 预算。
     */
    @Test
    void shouldSelectShortMixedScriptTokensForLikeConditions() {
        List<String> likeTokens = LexicalSearchTokenBudget.selectLikeTokens(List.of(
                "x项",
                "2项",
                "a",
                "甲"
        ));

        assertThat(likeTokens).contains("x项", "2项");
        assertThat(likeTokens).doesNotContain("甲");
    }

    private static final String[] CJK_BIGRAM_POOL = {
            "一甲", "一乙", "一丙", "一丁", "一戊",
            "二甲", "二乙", "二丙", "二丁", "二戊",
            "三甲", "三乙", "三丙", "三丁", "三戊",
            "四甲", "四乙", "四丙", "四丁", "四戊",
            "五甲", "五乙", "五丙", "五丁", "五戊",
            "六甲", "六乙", "六丙", "六丁", "六戊",
            "七甲", "七乙", "七丙", "七丁", "七戊",
            "八甲", "八乙", "八丙", "八丁", "八戊",
            "九甲", "九乙", "九丙", "九丁", "九戊",
            "十甲", "十乙", "十丙", "十丁", "十戊"
    };

    private static String cjkBigramAt(int index) {
        return CJK_BIGRAM_POOL[index % CJK_BIGRAM_POOL.length];
    }
}
