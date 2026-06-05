package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryTokenExtractor 测试
 *
 * 职责：验证查询 token 提取逻辑能够覆盖中文语义问句
 *
 * @author xiexu
 */
class QueryTokenExtractorTests {

    /**
     * 验证纯中文问句也会提取出稳定 token，供 source / contribution 检索复用。
     */
    @Test
    void shouldExtractChineseTokensFromNaturalLanguageQuestion() {
        List<String> tokens = QueryTokenExtractor.extract("用户确认的运维口径说重试间隔是什么");

        assertThat(tokens).contains("重试", "间隔");
    }

    /**
     * 验证纯中文 token 仍按既有 Han n-gram 规则提取。
     */
    @Test
    void shouldKeepExistingPureChineseTokenBehavior() {
        List<String> tokens = QueryTokenExtractor.extract("甲乙丙丁");

        assertThat(tokens).contains("甲乙", "丙丁", "甲乙丙丁");
        assertThat(tokens).doesNotContain("甲", "丁");
    }

    /**
     * 验证纯 ASCII token 仍按既有长度规则提取。
     */
    @Test
    void shouldKeepExistingPureAsciiTokenBehavior() {
        List<String> tokens = QueryTokenExtractor.extract("healthz B");

        assertThat(tokens).contains("healthz");
        assertThat(tokens).doesNotContain("b");
    }

    /**
     * 验证 Latin + Han 的混合脚本短片段会作为整体 token 保留。
     */
    @Test
    void shouldExtractLatinHanMixedScriptToken() {
        List<String> tokens = QueryTokenExtractor.extract("请查 X项 与 q段 的要求");

        assertThat(tokens).contains("x项", "q段");
    }

    /**
     * 验证连续 Latin + Han 的混合脚本短片段仍会作为整体 token 保留。
     */
    @Test
    void shouldKeepContinuousLatinHanMixedScriptToken() {
        List<String> tokens = QueryTokenExtractor.extract("请查 B级 的要求");

        assertThat(tokens).contains("b级");
    }

    /**
     * 验证数字 + Han 的混合脚本短片段会作为整体 token 保留。
     */
    @Test
    void shouldExtractDigitHanMixedScriptToken() {
        List<String> tokens = QueryTokenExtractor.extract("查看 2项 和 10段 的说明");

        assertThat(tokens).contains("2项", "10段");
    }

    /**
     * 验证空白分隔的短 Latin + Han 相邻片段会合并，但单片段不被放宽。
     */
    @Test
    void shouldNotRelaxSingleLatinOrSingleHanTokens() {
        List<String> singleLatinTokens = QueryTokenExtractor.extract("B");
        List<String> singleHanTokens = QueryTokenExtractor.extract("级");
        List<String> spacedMixedTokens = QueryTokenExtractor.extract("B 级");

        assertThat(singleLatinTokens).doesNotContain("b");
        assertThat(singleHanTokens).doesNotContain("级");
        assertThat(spacedMixedTokens).contains("b级");
        assertThat(spacedMixedTokens).doesNotContain("b", "级");
    }

    /**
     * 验证空白分隔的短 Latin + Han 相邻片段按通用规则合并。
     */
    @Test
    void shouldExtractSpacedLatinHanMixedScriptToken() {
        List<String> tokens = QueryTokenExtractor.extract("请查 A 类 的要求");

        assertThat(tokens).contains("a类");
        assertThat(tokens).doesNotContain("a", "类");
    }

    /**
     * 验证空白分隔的数字 + Han 相邻片段按通用规则合并。
     */
    @Test
    void shouldExtractSpacedDigitHanMixedScriptToken() {
        List<String> tokens = QueryTokenExtractor.extract("查看 2 项 的说明");

        assertThat(tokens).contains("2项");
        assertThat(tokens).doesNotContain("项");
    }

    /**
     * 验证路径、类名与配置键会被作为高信号 token 保留下来。
     */
    @Test
    void shouldExtractPathClassAndConfigKeyTokens() {
        List<String> tokens = QueryTokenExtractor.extract(
                "RoutePlanner 在 src/main/java/payment/RoutePlanner.java 里怎么读取 payment.retry.maxAttempts"
        );

        assertThat(tokens).contains(
                "routeplanner",
                "route",
                "planner",
                "src/main/java/payment/routeplanner.java",
                "payment.retry.maxattempts"
        );
    }

    /**
     * 验证单数字序号可被保留，用于表格中的 step_index、row 等结构化字段匹配。
     */
    @Test
    void shouldExtractSingleNumberTokensForStructuredTableQuestions() {
        List<String> tokens = QueryTokenExtractor.extract("case 100997 第9步校验什么");

        assertThat(tokens).contains("100997", "9");
    }

    /**
     * 验证接口路径会作为完整 token 保留，支撑业务接口表精准召回。
     */
    @Test
    void shouldExtractApiPathTokensForEndpointQuestions() {
        List<String> tokens = QueryTokenExtractor.extract("DPFM 的 /api/v2/fulfillment/request/add 接口做什么");

        assertThat(tokens).contains("/api/v2/fulfillment/request/add");
        assertThat(tokens).contains("api", "v2", "fulfillment", "request", "add");
    }
}
